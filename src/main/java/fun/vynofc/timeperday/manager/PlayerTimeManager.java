package fun.vynofc.timeperday.manager;

import fun.vynofc.timeperday.TimePerDayPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zentrale Fassade fuer die Player-Time-Logik.
 * Konkrete Verantwortlichkeiten sind in spezialisierte Manager ausgelagert.
 */
public class PlayerTimeManager {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final long AUTO_SAVE_INTERVAL_TICKS = 600L;

    final TimePerDayPlugin plugin;
    final PlayerPersistenceManager persistenceManager;
    final PlayerProgressionManager progressionManager;
    final PlayerResetManager resetManager;
    final PlayerTickManager tickManager;
    final PlayerMessageManager messageManager;
    final WorldRegenerationManager worldRegenerationManager;

    final ConcurrentHashMap<UUID, Long> playedToday = new ConcurrentHashMap<>();
    final ConcurrentHashMap<UUID, Long> playerLimits = new ConcurrentHashMap<>();
    final ConcurrentHashMap<UUID, Boolean> whitelist = new ConcurrentHashMap<>();
    final ConcurrentHashMap<UUID, Double> sessionPoints = new ConcurrentHashMap<>();
    final ConcurrentHashMap<UUID, Double> totalLevel = new ConcurrentHashMap<>();
    final ConcurrentHashMap<UUID, String> lastKitClaimDate = new ConcurrentHashMap<>();
    final ConcurrentHashMap<UUID, Boolean> showActionBar = new ConcurrentHashMap<>();

    final java.util.Set<UUID> pendingDayOverReset = ConcurrentHashMap.newKeySet();

    volatile long defaultLimitSeconds;
    volatile String currentDate;
    volatile ZoneId resetZoneId;
    volatile NavigableMap<Integer, Map<org.bukkit.Material, Integer>> spawnKits = new TreeMap<>();
    volatile Double maxHealth = null;
    volatile boolean dirty = false;

    long tickCount = 0;

    final File dataFile;
    YamlConfiguration dataConfig;

    public PlayerTimeManager(TimePerDayPlugin plugin) {
        this.plugin = plugin;
        this.defaultLimitSeconds = plugin.getConfig().getLong("default-limit-minutes", 120L) * 60L;
        this.resetZoneId = readResetZoneIdFromConfig();
        this.currentDate = LocalDate.now(resetZoneId).format(DATE_FORMAT);
        this.dataFile = new File(plugin.getDataFolder(), "playerdata.yml");

        this.persistenceManager = new PlayerPersistenceManager(this);
        this.progressionManager = new PlayerProgressionManager(this);
        this.resetManager = new PlayerResetManager(this);
        this.tickManager = new PlayerTickManager(this);
        this.messageManager = new PlayerMessageManager(this);
        this.worldRegenerationManager = new WorldRegenerationManager(this);
    }

    public synchronized void load() {
        this.resetZoneId = readResetZoneIdFromConfig();
        persistenceManager.load();
        tickManager.reloadWarningThresholds();
        worldRegenerationManager.load();
        reloadMaxHealthFromConfig();
        dirty = false;
    }

    public synchronized void save() {
        forceSave();
    }

    public void reload() {
        this.resetZoneId = readResetZoneIdFromConfig();
        persistenceManager.reload();
        tickManager.reloadWarningThresholds();
        worldRegenerationManager.reload();
        reloadMaxHealthFromConfig();
    }

    public void tickOnlinePlayers() {
        tickManager.tickOnlinePlayers();
    }

    public long getLimit(UUID uuid) {
        return playerLimits.getOrDefault(uuid, defaultLimitSeconds);
    }

    public void setLimit(UUID uuid, long seconds) {
        playerLimits.put(uuid, seconds);
        markDirty();
    }

    public long getPlayedToday(UUID uuid) {
        return playedToday.getOrDefault(uuid, 0L);
    }

    public double getSessionPoints(Player player) {
        return progressionManager.calculateInventorySessionPoints(player);
    }

    public double getSessionPoints(UUID uuid) {
        return sessionPoints.getOrDefault(uuid, 0.0D);
    }

    public double getTotalLevel(UUID uuid) {
        return totalLevel.getOrDefault(uuid, 0.0D);
    }

    public void setTotalLevel(UUID uuid, double level) {
        totalLevel.put(uuid, Math.max(0.0D, level));
        markDirty();
    }

    public void addTotalLevel(UUID uuid, double amount) {
        if (amount <= 0.0D) {
            return;
        }
        totalLevel.merge(uuid, amount, Double::sum);
        markDirty();
    }

    public int getBestKitLevelFor(double level) {
        return progressionManager.getBestKitLevelFor(level);
    }

    public boolean grantDailyKit(Player player) {
        return progressionManager.grantDailyKit(player);
    }

    public void resetPlayerTime(UUID uuid) {
        playedToday.remove(uuid);
        sessionPoints.remove(uuid);
        markDirty();
    }

    public synchronized void debugTriggerDayOver() {
        resetManager.triggerDebugDayOver(LocalDate.now(resetZoneId).format(DATE_FORMAT),
                "Debug: Tag vorbei ausgeloest (Tageswerte zurueckgesetzt).");
    }

    public void debugTriggerWarning(Player player, long remainingSeconds) {
        tickManager.debugTriggerWarning(player, remainingSeconds);
    }

    public boolean consumePendingDayOverReset(UUID uuid) {
        boolean consumed = pendingDayOverReset.remove(uuid);
        if (consumed) {
            markDirty();
        }
        return consumed;
    }

    public void applyDayOverReset(Player player) {
        resetManager.resetOnlinePlayerState(player);
    }

    public boolean debugTriggerTimeout(Player player) {
        return tickManager.debugTriggerTimeout(player);
    }

    public synchronized void resetEverything() {
        resetManager.resetEverything();
    }

    public boolean isWhitelisted(UUID uuid) {
        return whitelist.getOrDefault(uuid, false);
    }

    public void setWhitelisted(UUID uuid, boolean exempt) {
        whitelist.put(uuid, exempt);
        markDirty();
    }

    public boolean isShowActionBarEnabled(UUID uuid) {
        return showActionBar.getOrDefault(uuid, false);
    }

    public void setShowActionBar(UUID uuid, boolean enabled) {
        showActionBar.put(uuid, enabled);
        markDirty();
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
        return createSnapshot(
                player.getUniqueId(),
                getSessionPoints(player),
                player.hasPermission("timeperday.bypass")
        );
    }

    public PlayerTimeSnapshot getSnapshot(UUID uuid, boolean bypassPermission) {
        return createSnapshot(uuid, getSessionPoints(uuid), bypassPermission);
    }

    public Component buildJoinInfoComponent(Player player, boolean kitGiven) {
        return messageManager.buildJoinInfoComponent(player, kitGiven);
    }

    public Component buildJoinKickComponent(UUID uuid) {
        return messageManager.buildJoinKickComponent(uuid);
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

    void triggerDayOver(String newDate, String logMessage) {
        resetManager.triggerDayOver(newDate, logMessage);
    }

    void reloadDefaultLimitFromConfig() {
        this.defaultLimitSeconds = plugin.getConfig().getLong("default-limit-minutes", 120L) * 60L;
        reloadMaxHealthFromConfig();
    }

    void reloadMaxHealthFromConfig() {
        String raw = plugin.getConfig().getString("max-health", "aus");
        if (raw == null || raw.equalsIgnoreCase("aus")) {
            this.maxHealth = null;
        } else {
            try {
                double val = Double.parseDouble(raw);
                this.maxHealth = val > 0.0D ? val : null;
            } catch (NumberFormatException e) {
                this.maxHealth = null;
            }
        }
    }

    public Double getMaxHealth() {
        return maxHealth;
    }

    public boolean isMaxHealthEnabled() {
        return maxHealth != null;
    }

    static DateTimeFormatter getDateFormatter() {
        return DATE_FORMAT;
    }

    ZoneId getResetZoneId() {
        return resetZoneId;
    }

    long getAutoSaveIntervalTicks() {
        return AUTO_SAVE_INTERVAL_TICKS;
    }

    public void markDirty() {
        dirty = true;
    }

    public synchronized void flushIfDirty() {
        if (!dirty) {
            return;
        }
        persistenceManager.save();
        dirty = false;
    }

    public synchronized void forceSave() {
        persistenceManager.save();
        dirty = false;
    }

    public void onDisable() {
        worldRegenerationManager.onDisable();
    }

    public void handlePlayerJoinWorldCheck(Player player) {
        worldRegenerationManager.handlePlayerJoin(player);
    }

    private PlayerTimeSnapshot createSnapshot(UUID uuid, double sessionPointsValue, boolean bypassPermission) {
        long played = getPlayedToday(uuid);
        long limit = getLimit(uuid);
        boolean whitelistedState = isWhitelisted(uuid);
        boolean unlimited = whitelistedState || bypassPermission;
        long remaining = unlimited ? limit : Math.max(0L, limit - played);

        return new PlayerTimeSnapshot(
                played,
                limit,
                remaining,
                sessionPointsValue,
                getTotalLevel(uuid),
                whitelistedState,
                bypassPermission,
                unlimited
        );
    }

    public static String formatLevel(double level) {
        if (level < 0) {
            level = 0;
        }
        return new DecimalFormat("0.###").format(level);
    }

    public static String formatTime(long totalSeconds) {
        if (totalSeconds < 0) {
            totalSeconds = 0;
        }

        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;

        if (h > 0) {
            return h + "h " + m + "m " + s + "s";
        }
        if (m > 0) {
            return m + "m " + s + "s";
        }
        return s + "s";
    }

    private ZoneId readResetZoneIdFromConfig() {
        String configuredZone = plugin.getConfig().getString("reset-timezone", "");
        if (configuredZone == null || configuredZone.isBlank()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(configuredZone.trim());
        } catch (Exception ex) {
            plugin.getLogger().warning("Ungueltige reset-timezone in config.yml: " + configuredZone
                    + " (Fallback: " + ZoneId.systemDefault() + ")");
            return ZoneId.systemDefault();
        }
    }
}

