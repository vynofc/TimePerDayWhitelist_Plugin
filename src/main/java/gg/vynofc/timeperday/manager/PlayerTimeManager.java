package gg.vynofc.timeperday.manager;

import gg.vynofc.timeperday.TimePerDayPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
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

    private volatile long defaultLimitSeconds;
    private volatile String currentDate;
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

    public void tick() {
        // Tageswechsel prüfen
        String today = LocalDate.now().format(DATE_FORMAT);
        if (!today.equals(currentDate)) {
            currentDate = today;
            playedToday.clear();
            sessionPoints.clear();
            plugin.getLogger().info("Tägliche Spielzeiten zurückgesetzt (Mitternacht).");
        }

        List<Long> warningThresholds = plugin.getConfig().getLongList("warnings");

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();

            // Befreiung prüfen (Whitelist-Eintrag ODER Bypass-Permission)
            if (whitelist.getOrDefault(uuid, false) || player.hasPermission("timeperday.bypass")) {
                continue;
            }

            long played = playedToday.merge(uuid, 1L, Long::sum);
            long limit = getLimit(uuid);
            long remaining = limit - played;

            // Warnungen
            if (warningThresholds.contains(remaining)) {
                sendWarning(player, remaining);
            }

            // Limit überschritten → rauswerfen
            if (remaining <= 0) {
                double gained = finalizeSessionProgress(uuid);
                kickPlayer(player, gained, getTotalLevel(uuid));
            }
        }

        // Alle 5 Minuten speichern
        tickCount++;
        if (tickCount % 300 == 0) {
            save();
        }
    }

    // -------------------------------------------------------------------------
    // Interne Helfer
    // -------------------------------------------------------------------------

    private void sendWarning(Player player, long remainingSeconds) {
        String formatted = formatTime(remainingSeconds);
        String template = plugin.getConfig().getString("messages.warning",
                "<yellow>⚠ {remaining} verbleibende Spielzeit heute!");
        String msg = template.replace("{remaining}", formatted);

        // Folia: auf den Thread des Spielers planen
        player.getScheduler().run(plugin, task ->
                player.sendMessage(Component.text(stripMiniMessage(msg), NamedTextColor.YELLOW)), null);
    }

    private void kickPlayer(Player player, double gainedLevel, double totalLevelValue) {
        String line1Template = plugin.getConfig().getString(
                "messages.kick-line1",
                "<red>Deine tägliche Spielzeit ist aufgebraucht! +{gained-level} Level (Gesamt: {total-level})");
        String line1 = applyPlaceholders(line1Template, Map.of(
                "{gained-level}", formatLevel(gainedLevel),
                "{total-level}", formatLevel(totalLevelValue),
                "{session-points}", formatLevel(getSessionPoints(player.getUniqueId()))
        ));
        String line2 = stripMiniMessage(plugin.getConfig().getString(
                "messages.kick-line2", "Du kannst morgen wieder spielen."));

        player.getScheduler().run(plugin, task ->
                player.kick(Component.text()
                        .append(Component.text(stripMiniMessage(line1), NamedTextColor.RED))
                        .appendNewline()
                        .append(Component.text(line2, NamedTextColor.GRAY))
                        .build()), null);
    }

    public Component buildJoinKickComponent(UUID uuid) {
        String line1Template = plugin.getConfig().getString(
                "messages.kick-line1",
                "<red>Deine tägliche Spielzeit ist aufgebraucht! +{gained-level} Level (Gesamt: {total-level})");
        String line1 = applyPlaceholders(line1Template, Map.of(
                "{gained-level}", "0",
                "{total-level}", formatLevel(getTotalLevel(uuid)),
                "{session-points}", formatLevel(getSessionPoints(uuid))
        ));
        String line2 = stripMiniMessage(plugin.getConfig().getString(
                "messages.kick-line2", "Du kannst morgen wieder spielen."));

        return Component.text()
                .append(Component.text(stripMiniMessage(line1), NamedTextColor.RED))
                .appendNewline()
                .append(Component.text(line2, NamedTextColor.GRAY))
                .build();
    }

    private double finalizeSessionProgress(UUID uuid) {
        double gained = sessionPoints.getOrDefault(uuid, 0.0D);
        if (gained > 0.0D) {
            totalLevel.merge(uuid, gained, Double::sum);
        }
        sessionPoints.put(uuid, 0.0D);
        save();
        return gained;
    }

    /** Entfernt einfache MiniMessage-Tags für die Konsolen-/Plain-Ausgabe. */
    private String stripMiniMessage(String text) {
        return text.replaceAll("<[^>]+>", "");
    }

    private String applyPlaceholders(String text, Map<String, String> placeholders) {
        String out = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            out = out.replace(entry.getKey(), entry.getValue());
        }
        return out;
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

    public double addSessionPoints(Player player, Material material, int amount) {
        if (amount <= 0) return 0.0D;
        UUID uuid = player.getUniqueId();

        if (isWhitelisted(uuid) || player.hasPermission("timeperday.bypass")) {
            return 0.0D;
        }

        String path = "progression.points-per-item." + material.name();
        double perItem = plugin.getConfig().getDouble(path, 0.0D);
        if (perItem <= 0.0D) {
            return 0.0D;
        }

        double gain = perItem * amount;
        sessionPoints.merge(uuid, gain, Double::sum);
        return gain;
    }

    public int getBestKitLevelFor(double level) {
        var kits = readSpawnKits();
        int best = 0;
        for (Integer threshold : kits.keySet()) {
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

        var kit = readSpawnKits().get(kitLevel);
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

        for (Player player : Bukkit.getOnlinePlayers()) {
            resetOnlinePlayerState(player);
        }

        for (World world : Bukkit.getWorlds()) {
            resetWorldRuntimeState(world);
        }

        save();
    }

    private void resetOnlinePlayerState(Player player) {
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

    public long getDefaultLimitSeconds() {
        return defaultLimitSeconds;
    }

    public void setDefaultLimitSeconds(long seconds) {
        this.defaultLimitSeconds = seconds;
        plugin.getConfig().set("default-limit-minutes", seconds / 60L);
        plugin.saveConfig();
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
