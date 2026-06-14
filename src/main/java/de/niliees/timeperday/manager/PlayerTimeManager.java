package de.niliees.timeperday.manager;

import de.niliees.timeperday.TimePerDayPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
            // Kein Laden der Spielzeiten, Maps bleiben leer
        } else {
            loadSection("playtime", playedToday);
        }

        loadSection("limits", playerLimits);
        loadBooleanSection("whitelist", whitelist);
    }

    public synchronized void save() {
        dataConfig = new YamlConfiguration();
        dataConfig.set("date", currentDate);
        playedToday.forEach((uuid, val) -> dataConfig.set("playtime." + uuid, val));
        playerLimits.forEach((uuid, val) -> dataConfig.set("limits." + uuid, val));
        whitelist.forEach((uuid, val) -> dataConfig.set("whitelist." + uuid, val));
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Konnte playerdata.yml nicht speichern", e);
        }
    }

    public void reload() {
        defaultLimitSeconds = plugin.getConfig().getLong("default-limit-minutes", 120L) * 60L;
        playedToday.clear();
        playerLimits.clear();
        whitelist.clear();
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
                kickPlayer(player);
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

    private void kickPlayer(Player player) {
        String line1 = stripMiniMessage(plugin.getConfig().getString(
                "messages.kick-line1", "Deine tägliche Spielzeit ist aufgebraucht!"));
        String line2 = stripMiniMessage(plugin.getConfig().getString(
                "messages.kick-line2", "Du kannst morgen wieder spielen."));

        player.getScheduler().run(plugin, task ->
                player.kick(Component.text()
                        .append(Component.text(line1, NamedTextColor.RED))
                        .appendNewline()
                        .append(Component.text(line2, NamedTextColor.GRAY))
                        .build()), null);
    }

    /** Entfernt einfache MiniMessage-Tags für die Konsolen-/Plain-Ausgabe. */
    private String stripMiniMessage(String text) {
        return text.replaceAll("<[^>]+>", "");
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

    public void resetPlayerTime(UUID uuid) {
        playedToday.remove(uuid);
        save();
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
