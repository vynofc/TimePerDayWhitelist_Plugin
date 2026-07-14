package gg.vynofc.timeperday.manager;

import gg.vynofc.timeperday.TimePerDayPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Verwaltet die tägliche Spielzeit für jeden Spieler.
 * Thread-sicher: alle Maps sind ConcurrentHashMap, da tick() aus
 * einem Async-Thread aufgerufen wird (Folia AsyncScheduler).
 */
public class PlayerTimeManager {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final TimePerDayPlugin plugin;

    // Sekunden, die ein Spieler heute bereits gespielt hat
    private final ConcurrentHashMap<UUID, Long> playedToday = new ConcurrentHashMap<>();
    // Individuelles Tageslimit in Sekunden; fehlt ein Eintrag → defaultLimitSeconds
    private final ConcurrentHashMap<UUID, Long> playerLimits = new ConcurrentHashMap<>();
    // true = Spieler ist von Zeitlimits befreit
    private final ConcurrentHashMap<UUID, Boolean> whitelist = new ConcurrentHashMap<>();
    // Session-Fortschritt: Levelpunkte, die heute gesammelt wurden
    private final ConcurrentHashMap<UUID, Double> sessionPoints = new ConcurrentHashMap<>();
    // Persistenter Gesamtlevel
    private final ConcurrentHashMap<UUID, Double> totalLevel = new ConcurrentHashMap<>();
    // Verhindert Kit-Farming durch Rejoin: pro Tag nur ein Kit
    private final ConcurrentHashMap<UUID, String> lastKitClaimDate = new ConcurrentHashMap<>();
    // Spieler, die beim nächsten Login ihr Inventar/Position zurückgesetzt bekommen sollen
    private final Set<UUID> pendingDayOverReset = ConcurrentHashMap.newKeySet();

    private volatile long defaultLimitSeconds;
    private volatile String currentDate;
    private volatile NavigableMap<Integer, Map<Material, Integer>> spawnKits = new TreeMap<>();
    private long tickCount = 0;

    private final File dataFile;
    private YamlConfiguration dataConfig;

    public PlayerTimeManager(TimePerDayPlugin plugin) {
        this.plugin = plugin;
        this.defaultLimitSeconds = plugin.getConfig().getLong("default-limit-minutes", 120L) * 60L;
        this.currentDate = LocalDate.now().format(DATE_FORMAT);
        this.dataFile = new File(plugin.getDataFolder(), "playerdata.yml");
    }

    // -------------------------------------------------------------------------
    // Laden & Speichern
    // -------------------------------------------------------------------------

    public synchronized void load() {
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Konnte playerdata.yml nicht erstellen", e);
            }
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        String savedDate = dataConfig.getString("date", currentDate);
        boolean newDay = !savedDate.equals(currentDate);

        if (newDay) {
            plugin.getLogger().info("Neuer Tag erkannt – Spielzeiten werden zurückgesetzt.");
            // Kein Laden der Session-Daten, Maps bleiben leer
        } else {
            loadSection("playtime", playedToday);
            loadDoubleSection("session-points", sessionPoints);
        }

        loadSection("limits", playerLimits);
        loadBooleanSection("whitelist", whitelist);
        loadDoubleSection("total-level", totalLevel);
        loadStringSection("last-kit-claim-date", lastKitClaimDate);
        spawnKits = readSpawnKits();
    }

    public synchronized void save() {
        dataConfig = new YamlConfiguration();
        dataConfig.set("date", currentDate);
        playedToday.forEach((uuid, val) -> dataConfig.set("playtime." + uuid, val));
        playerLimits.forEach((uuid, val) -> dataConfig.set("limits." + uuid, val));
        whitelist.forEach((uuid, val) -> dataConfig.set("whitelist." + uuid, val));
        sessionPoints.forEach((uuid, val) -> dataConfig.set("session-points." + uuid, val));
        totalLevel.forEach((uuid, val) -> dataConfig.set("total-level." + uuid, val));
        lastKitClaimDate.forEach((uuid, val) -> dataConfig.set("last-kit-claim-date." + uuid, val));
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Konnte playerdata.yml nicht speichern", e);
        }
    }

    public void reload() {
        save();
        defaultLimitSeconds = plugin.getConfig().getLong("default-limit-minutes", 120L) * 60L;
        playedToday.clear();
        playerLimits.clear();
        whitelist.clear();
        sessionPoints.clear();
        totalLevel.clear();
        lastKitClaimDate.clear();
        load();
    }

    // -------------------------------------------------------------------------
    // Tick (jede Sekunde, aus AsyncScheduler aufgerufen)
    // -------------------------------------------------------------------------

    public void tickOnlinePlayers() {
        // Tageswechsel prüfen
        String today = LocalDate.now().format(DATE_FORMAT);
        if (!today.equals(currentDate)) {
            // Tageswechsel beendet die laufende Session sofort und setzt Spieler
            // anschließend zurück; Warn-/Kick-Checks für den alten Tag entfallen damit.
            triggerDayOver(today, "Tägliche Spielzeiten zurückgesetzt (Mitternacht).");
            return;
        }

        List<Long> warningThresholds = plugin.getConfig().getLongList("warnings");

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, task -> tickPlayer(player, warningThresholds), null);
        }

        // Alle 5 Minuten speichern
        tickCount++;
        if (tickCount % 300 == 0) {
            save();
        }
    }

    private void tickPlayer(Player player, List<Long> warningThresholds) {
        if (!player.isOnline()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (isWhitelisted(uuid) || player.hasPermission("timeperday.bypass")) {
            return;
        }

        long played = playedToday.merge(uuid, 1L, Long::sum);
        long limit = getLimit(uuid);
        long remaining = limit - played;

        if (warningThresholds.contains(remaining)) {
            sendWarning(player, remaining);
        }

        if (remaining <= 0) {
            double gained = finalizeSessionProgress(player);
            kickPlayer(player, gained, getTotalLevel(uuid));
        }
    }

    // -------------------------------------------------------------------------
    // Interne Helfer
    // -------------------------------------------------------------------------

    private void sendWarning(Player player, long remainingSeconds) {
        player.sendMessage(buildConfiguredMessage(
                plugin.getConfig().getString("messages.warning",
                        "<yellow>⚠ {remaining} verbleibende Spielzeit heute!"),
                Map.of("{remaining}", formatTime(remainingSeconds))));
    }

    private void kickPlayer(Player player, double gainedLevel, double totalLevelValue) {
        player.kick(buildKickComponent(player.getUniqueId(), gainedLevel, totalLevelValue));
    }

    private Component buildKickComponent(UUID uuid, double gainedLevel, double totalLevelValue) {
        PlayerTimeSnapshot snapshot = getSnapshot(uuid, false);
        int kitLevel = getBestKitLevelFor(totalLevelValue);
        Map<String, String> placeholders = buildProfilePlaceholders(
                uuid,
                resolvePlayerName(uuid),
                snapshot,
                gainedLevel,
                gainedLevel,
                totalLevelValue,
                kitLevel,
                false
        );
        return buildMultilineMessage(List.of(
                plugin.getConfig().getString(
                        "messages.kick-title",
                        "<gradient:#ff6b6b:#ffd166><bold>Spielzeit fuer heute verbraucht</bold></gradient>"),
                plugin.getConfig().getString(
                        "messages.kick-line1",
                        "<gray>Profil von <yellow>{player}</yellow><gray> - gespielt: <yellow>{played}</yellow>"
                                + " <dark_gray>| <gray>Limit: <yellow>{limit}</yellow>"),
                plugin.getConfig().getString(
                        "messages.kick-line2",
                        "<gray>Heute verdient: <yellow>+{gained-level}</yellow> <dark_gray>| <gray>Session: "
                                + "<yellow>{session-points}</yellow>"),
                plugin.getConfig().getString(
                        "messages.kick-line3",
                        "<gray>Gesamtlevel: <yellow>{total-level}</yellow> <dark_gray>| <gray>Bestes Kit: "
                                + "<yellow>{kit-level}</yellow>"),
                plugin.getConfig().getString(
                        "messages.kick-line4",
                        "<gray>Du kannst morgen wieder spielen. Nutze dann <yellow>/timeleft</yellow><gray> fuer dein Profil.")
        ), placeholders);
    }

    public Component buildJoinKickComponent(UUID uuid) {
        return buildKickComponent(uuid, 0.0D, getTotalLevel(uuid));
    }

    // Verwendet ausschließlich den gespeicherten Fallback-Wert. Online-Spieler
    // werden im neuen System über finalizeSessionProgress(Player) finalisiert.
    private double finalizeSessionProgress(UUID uuid) {
        return finalizeSessionProgress(uuid, sessionPoints.getOrDefault(uuid, 0.0D));
    }

    private double finalizeSessionProgress(UUID uuid, double gained) {
        if (gained > 0.0D) {
            totalLevel.merge(uuid, gained, Double::sum);
        }
        sessionPoints.put(uuid, 0.0D);
        save();
        return gained;
    }

    private double finalizeSessionProgress(Player player) {
        UUID uuid = player.getUniqueId();
        if (isWhitelisted(uuid) || player.hasPermission("timeperday.bypass")) {
            sessionPoints.remove(uuid);
            return 0.0D;
        }

        return finalizeSessionProgress(uuid, calculateInventorySessionPoints(player));
    }

    private String applyPlaceholders(String text, Map<String, String> placeholders) {
        String out = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            out = out.replace(entry.getKey(), entry.getValue());
        }
        return out;
    }

    private Component buildMultilineMessage(List<String> templates, Map<String, String> placeholders) {
        var builder = Component.text();
        boolean firstLine = true;
        for (String template : templates) {
            if (template == null || template.isBlank()) {
                continue;
            }
            if (!firstLine) {
                builder.appendNewline();
            }
            builder.append(buildConfiguredMessage(template, placeholders));
            firstLine = false;
        }
        return builder.build();
    }

    private Component buildConfiguredMessage(String template, Map<String, String> placeholders) {
        return MINI_MESSAGE.deserialize(applyPlaceholders(template, placeholders));
    }

    private String resolvePlayerName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        String offlineName = Bukkit.getOfflinePlayer(uuid).getName();
        return offlineName != null ? offlineName : uuid.toString();
    }

    private Map<String, String> buildProfilePlaceholders(UUID uuid, String playerName, PlayerTimeSnapshot snapshot,
                                                         double gainedLevel, double sessionPointsValue,
                                                         double totalLevelValue, int kitLevel, boolean kitGiven) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{player}", playerName);
        placeholders.put("{played}", formatTime(snapshot.played()));
        placeholders.put("{limit}", snapshot.unlimited() ? "Unbegrenzt" : formatTime(snapshot.limit()));
        placeholders.put("{remaining}", snapshot.unlimited() ? "Unbegrenzt" : formatTime(snapshot.remaining()));
        placeholders.put("{gained-level}", formatLevel(gainedLevel));
        placeholders.put("{session-points}", formatLevel(sessionPointsValue));
        placeholders.put("{total-level}", formatLevel(totalLevelValue));
        placeholders.put("{level}", formatLevel(totalLevelValue));
        placeholders.put("{progress-level}", formatLevel(Math.max(totalLevelValue,
                snapshot.totalLevel() + snapshot.sessionPoints())));
        placeholders.put("{kit-level}", kitLevel > 0 ? String.valueOf(kitLevel) : "Keins");
        placeholders.put("{kit-given}", kitGiven ? "Ja" : "Nein");
        placeholders.put("{whitelisted}", snapshot.whitelisted() ? "Ja" : "Nein");
        placeholders.put("{bypass}", snapshot.bypassPermission() ? "Ja" : "Nein");
        placeholders.put("{status}", snapshot.unlimited() ? "Unbegrenzt" : "Begrenzt");
        placeholders.put("{uuid}", uuid.toString());
        return placeholders;
    }

    private void loadSection(String path, ConcurrentHashMap<UUID, Long> map) {
        if (!dataConfig.contains(path)) return;
        var section = dataConfig.getConfigurationSection(path);
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                map.put(UUID.fromString(key), section.getLong(key));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Ungültige UUID in playerdata.yml: " + key);
            }
        }
    }

    private void loadBooleanSection(String path, ConcurrentHashMap<UUID, Boolean> map) {
        if (!dataConfig.contains(path)) return;
        var section = dataConfig.getConfigurationSection(path);
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                map.put(UUID.fromString(key), section.getBoolean(key));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Ungültige UUID in playerdata.yml: " + key);
            }
        }
    }

    private void loadDoubleSection(String path, ConcurrentHashMap<UUID, Double> map) {
        if (!dataConfig.contains(path)) return;
        var section = dataConfig.getConfigurationSection(path);
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                map.put(UUID.fromString(key), section.getDouble(key));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Ungültige UUID in playerdata.yml: " + key);
            }
        }
    }

    private void loadStringSection(String path, ConcurrentHashMap<UUID, String> map) {
        if (!dataConfig.contains(path)) return;
        var section = dataConfig.getConfigurationSection(path);
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                map.put(UUID.fromString(key), section.getString(key, ""));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Ungültige UUID in playerdata.yml: " + key);
            }
        }
    }

    private NavigableMap<Integer, Map<Material, Integer>> readSpawnKits() {
        NavigableMap<Integer, Map<Material, Integer>> kits = new TreeMap<>();
        var root = plugin.getConfig().getConfigurationSection("progression.spawn-kits");
        if (root == null) return kits;

        for (String levelKey : root.getKeys(false)) {
            int level;
            try {
                level = Integer.parseInt(levelKey);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Ungültige Kit-Stufe in config.yml: " + levelKey);
                continue;
            }

            var section = root.getConfigurationSection(levelKey);
            if (section == null) continue;

            Map<Material, Integer> kit = new HashMap<>();
            for (String materialKey : section.getKeys(false)) {
                Material material = Material.matchMaterial(materialKey);
                if (material == null) {
                    plugin.getLogger().warning("Ungültiges Material im Kit " + level + ": " + materialKey);
                    continue;
                }
                int amount = Math.max(1, section.getInt(materialKey, 1));
                kit.put(material, amount);
            }

            if (!kit.isEmpty()) {
                kits.put(level, kit);
            }
        }

        return kits;
    }

    // -------------------------------------------------------------------------
    // Öffentliche API
    // -------------------------------------------------------------------------

    public long getLimit(UUID uuid) {
        return playerLimits.getOrDefault(uuid, defaultLimitSeconds);
    }

    public void setLimit(UUID uuid, long seconds) {
        playerLimits.put(uuid, seconds);
        save();
    }

    public long getPlayedToday(UUID uuid) {
        return playedToday.getOrDefault(uuid, 0L);
    }

    public double getSessionPoints(Player player) {
        return calculateInventorySessionPoints(player);
    }

    public double getSessionPoints(UUID uuid) {
        return sessionPoints.getOrDefault(uuid, 0.0D);
    }

    public double getTotalLevel(UUID uuid) {
        return totalLevel.getOrDefault(uuid, 0.0D);
    }

    public void setTotalLevel(UUID uuid, double level) {
        totalLevel.put(uuid, Math.max(0.0D, level));
        save();
    }

    public void addTotalLevel(UUID uuid, double amount) {
        if (amount <= 0.0D) return;
        totalLevel.merge(uuid, amount, Double::sum);
        save();
    }

    public int getBestKitLevelFor(double level) {
        int best = 0;
        for (Integer threshold : spawnKits.keySet()) {
            if (level >= threshold) {
                best = threshold;
            }
        }
        return best;
    }

    public boolean grantDailyKit(Player player) {
        UUID uuid = player.getUniqueId();
        if (isWhitelisted(uuid) || player.hasPermission("timeperday.bypass")) {
            return false;
        }

        String claimed = lastKitClaimDate.getOrDefault(uuid, "");
        if (currentDate.equals(claimed)) {
            return false;
        }

        int kitLevel = getBestKitLevelFor(getTotalLevel(uuid));
        if (kitLevel <= 0) {
            return false;
        }

        var kit = spawnKits.get(kitLevel);
        if (kit == null || kit.isEmpty()) {
            return false;
        }

        for (Map.Entry<Material, Integer> entry : kit.entrySet()) {
            ItemStack stack = new ItemStack(entry.getKey(), entry.getValue());
            var overflow = player.getInventory().addItem(stack);
            if (!overflow.isEmpty()) {
                overflow.values().forEach(item ->
                        player.getWorld().dropItemNaturally(player.getLocation(), item));
            }
        }

        lastKitClaimDate.put(uuid, currentDate);
        save();
        return true;
    }

    public void resetPlayerTime(UUID uuid) {
        playedToday.remove(uuid);
        sessionPoints.remove(uuid);
        save();
    }

    public synchronized void debugTriggerDayOver() {
        triggerDayOver(LocalDate.now().format(DATE_FORMAT),
                "Debug: Tag vorbei ausgelöst (Tageswerte zurückgesetzt).");
    }

    public void debugTriggerWarning(Player player, long remainingSeconds) {
        sendWarning(player, Math.max(0L, remainingSeconds));
    }

    /**
     * Entfernt den Spieler aus der Pending-Reset-Liste und gibt zurück, ob ein Reset ansteht.
     * Wird vom PlayerListener beim Login aufgerufen.
     */
    public boolean consumePendingDayOverReset(UUID uuid) {
        return pendingDayOverReset.remove(uuid);
    }

    /**
     * Öffentlicher Wrapper für resetOnlinePlayerState – wird beim Login nach einem
     * debugTriggerDayOver() aufgerufen, damit Inventar und Position auch für Spieler
     * zurückgesetzt werden, die zum Zeitpunkt des Debug-Events offline waren.
     */
    public void applyDayOverReset(Player player) {
        resetOnlinePlayerState(player);
    }

    public boolean debugTriggerTimeout(Player player) {
        UUID uuid = player.getUniqueId();
        if (isWhitelisted(uuid) || player.hasPermission("timeperday.bypass")) {
            return false;
        }

        playedToday.put(uuid, getLimit(uuid));
        double gained = finalizeSessionProgress(player);
        kickPlayer(player, gained, getTotalLevel(uuid));
        return true;
    }

    /**
     * Globaler Hard-Reset für das Plugin:
     * - alle gespeicherten Spieler-/Progressionsdaten
     * - alle online Spielerzustände (Inventar, XP, Health, Position)
     * - laufender Weltzustand (Zeit/Wetter/Non-Player-Entities)
     */
    public synchronized void resetEverything() {
        playedToday.clear();
        playerLimits.clear();
        whitelist.clear();
        sessionPoints.clear();
        totalLevel.clear();
        lastKitClaimDate.clear();

        currentDate = LocalDate.now().format(DATE_FORMAT);

        save();
        plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.getScheduler().run(plugin, scheduledTask -> resetOnlinePlayerState(player), null);
            }

            for (World world : Bukkit.getWorlds()) {
                resetWorldRuntimeState(world);
            }
        });
    }

    private double calculateInventorySessionPoints(Player player) {
        UUID uuid = player.getUniqueId();
        if (isWhitelisted(uuid) || player.hasPermission("timeperday.bypass")) {
            return 0.0D;
        }

        return calculateItemPoints(player.getInventory().getStorageContents())
                + calculateItemPoints(player.getInventory().getArmorContents())
                + calculateItemPoints(new ItemStack[]{player.getInventory().getItemInOffHand()})
                + calculateItemPoints(player.getEnderChest().getContents());
    }

    private double calculateItemPoints(ItemStack[] contents) {
        double total = 0.0D;
        for (ItemStack item : contents) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            double lvl = readItemLevel(item.getType());
            if (lvl > 0.0D) {
                total += lvl * item.getAmount();
            }
        }
        return total;
    }

    private void resetOnlinePlayerState(Player player) {
        // Inventar-Items vor dem Leeren vollständig in den Gesamtlevel übernehmen
        finalizeSessionProgress(player);

        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        player.getEnderChest().clear();

        player.setLevel(0);
        player.setExp(0.0f);
        player.setTotalExperience(0);

        var healthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            player.setHealth(healthAttr.getValue());
        }
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setFireTicks(0);
        player.setFallDistance(0.0f);

        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(player.getWorld().getSpawnLocation());
    }

    private void resetWorldRuntimeState(World world) {
        world.setTime(0L);
        world.setStorm(false);
        world.setThundering(false);

        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        }
    }

    public boolean isWhitelisted(UUID uuid) {
        return whitelist.getOrDefault(uuid, false);
    }

    public void setWhitelisted(UUID uuid, boolean exempt) {
        whitelist.put(uuid, exempt);
        save();
    }

    public TimePerDayPlugin getPlugin() {
        return plugin;
    }

    public long getDefaultLimitSeconds() {
        return defaultLimitSeconds;
    }

    public void setDefaultLimitSeconds(long seconds) {
        this.defaultLimitSeconds = seconds;
        plugin.getConfig().set("default-limit-minutes", seconds / 60L);
        plugin.saveConfig();
    }

    public PlayerTimeSnapshot getSnapshot(Player player) {
        long played = getPlayedToday(player.getUniqueId());
        long limit = getLimit(player.getUniqueId());
        boolean whitelistedState = isWhitelisted(player.getUniqueId());
        boolean bypassPermission = player.hasPermission("timeperday.bypass");
        boolean unlimited = whitelistedState || bypassPermission;
        long remaining = unlimited ? limit : Math.max(0L, limit - played);
        return new PlayerTimeSnapshot(
                played,
                limit,
                remaining,
                getSessionPoints(player),
                getTotalLevel(player.getUniqueId()),
                whitelistedState,
                bypassPermission,
                unlimited
        );
    }

    public PlayerTimeSnapshot getSnapshot(UUID uuid, boolean bypassPermission) {
        long played = getPlayedToday(uuid);
        long limit = getLimit(uuid);
        boolean whitelistedState = isWhitelisted(uuid);
        boolean unlimited = whitelistedState || bypassPermission;
        long remaining = unlimited ? limit : Math.max(0L, limit - played);
        return new PlayerTimeSnapshot(
                played,
                limit,
                remaining,
                getSessionPoints(uuid),
                getTotalLevel(uuid),
                whitelistedState,
                bypassPermission,
                unlimited
        );
    }

    public Component buildJoinInfoComponent(Player player, boolean kitGiven) {
        PlayerTimeSnapshot snapshot = getSnapshot(player);
        int kitLevel = getBestKitLevelFor(snapshot.totalLevel());
        Map<String, String> placeholders = buildProfilePlaceholders(
                player.getUniqueId(),
                player.getName(),
                snapshot,
                0.0D,
                snapshot.sessionPoints(),
                snapshot.totalLevel(),
                kitLevel,
                kitGiven
        );
        placeholders.put("{level}", formatLevel(snapshot.totalLevel()));

        return buildMultilineMessage(List.of(
                plugin.getConfig().getString(
                        "messages.join-title",
                        "<gradient:#7bed9f:#70a1ff><bold>Willkommen zurueck, {player}!</bold></gradient>"),
                plugin.getConfig().getString(
                        "messages.join-info",
                        "<gray>Verbleibend: <yellow>{remaining}</yellow> <dark_gray>| <gray>Gesamtlevel: "
                                + "<yellow>{total-level}</yellow> <dark_gray>| <gray>Bestes Kit: <yellow>{kit-level}</yellow>"),
                plugin.getConfig().getString(
                        "messages.join-line2",
                        "<gray>Session heute: <yellow>{session-points}</yellow> <dark_gray>| <gray>Kit heute erhalten: "
                                + "<yellow>{kit-given}</yellow>"),
                plugin.getConfig().getString(
                        "messages.join-tip",
                        "<gray>Tipp: Nutze jederzeit <yellow>/timeleft</yellow><gray> fuer deine komplette Profiluebersicht.")
        ), placeholders);
    }

    public record PlayerTimeSnapshot(long played, long limit, long remaining, double sessionPoints,
                                     double totalLevel, boolean whitelisted, boolean bypassPermission,
                                     boolean unlimited) {
    }

    private double readItemLevel(Material material) {
        String modernPath = "progression.items." + material.name();
        if (plugin.getConfig().isConfigurationSection(modernPath)) {
            double level = plugin.getConfig().getDouble(modernPath + ".level", 0.0D);
            if (level > 0.0D) {
                return level;
            }
        }

        return 0.0D;
    }

    public double getProgressLevel(UUID uuid) {
        return getTotalLevel(uuid) + getSessionPoints(uuid);
    }

    public double getProgressLevel(Player player) {
        return getTotalLevel(player.getUniqueId()) + getSessionPoints(player);
    }

    public double getProgressLevel(OfflinePlayer player) {
        Player onlinePlayer = player.getPlayer();
        return onlinePlayer != null ? getProgressLevel(onlinePlayer) : getProgressLevel(player.getUniqueId());
    }

    private void triggerDayOver(String newDate, String logMessage) {
        Set<UUID> allTrackedUuids = new HashSet<>(playedToday.keySet());
        allTrackedUuids.addAll(sessionPoints.keySet());
        allTrackedUuids.addAll(lastKitClaimDate.keySet());
        allTrackedUuids.addAll(totalLevel.keySet());
        allTrackedUuids.addAll(playerLimits.keySet());

        currentDate = newDate;
        playedToday.clear();
        lastKitClaimDate.clear();
        plugin.getLogger().info(logMessage);
        save();

        plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> {
            Set<UUID> currentlyOnline = new HashSet<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                currentlyOnline.add(uuid);
                if (!isWhitelisted(uuid) && !player.hasPermission("timeperday.bypass")) {
                    player.getScheduler().run(plugin, scheduledTask -> {
                        resetOnlinePlayerState(player);
                        grantDailyKit(player);
                    }, null);
                } else {
                    sessionPoints.remove(uuid);
                }
            }
            for (UUID uuid : allTrackedUuids) {
                if (currentlyOnline.contains(uuid)) {
                    continue;
                }
                sessionPoints.remove(uuid);
                if (!isWhitelisted(uuid)) {
                    pendingDayOverReset.add(uuid);
                }
            }
        });
    }

    public static String formatLevel(double level) {
        if (level < 0) level = 0;
        return new DecimalFormat("0.###").format(level);
    }

    /** Formatiert Sekunden als lesbaren String (z.B. "1h 23m 45s"). */
    public static String formatTime(long totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        if (h > 0) return h + "h " + m + "m " + s + "s";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }
}
