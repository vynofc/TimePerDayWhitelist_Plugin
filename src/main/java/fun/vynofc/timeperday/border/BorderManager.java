package fun.vynofc.timeperday.border;

import fun.vynofc.timeperday.TimePerDayPlugin;
import fun.vynofc.timeperday.border.util.BorderColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class BorderManager {

    private final TimePerDayPlugin plugin;
    private final Map<String, BorderData> borders = new HashMap<>();
    private final Map<UUID, PlayerData> players = new HashMap<>();
    private final File bordersFile;

    private String message;
    private boolean useActionBar;
    private String effect;
    private String sound;
    private boolean preventMobSpawns;
    private boolean preventEnderpearl;
    private boolean preventChorusFruit;
    private boolean visualizerEnabled;
    private int visualizerRange;
    private String visualizerColor;
    private long checkInterval;

    private boolean dirty;

    public BorderManager(TimePerDayPlugin plugin) {
        this.plugin = plugin;
        this.bordersFile = new File(plugin.getDataFolder(), "borders.yml");
    }

    public void load() {
        loadConfig();
        borders.clear();
        if (!bordersFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(bordersFile);
        for (String worldName : config.getKeys(false)) {
            var section = config.getConfigurationSection(worldName);
            if (section == null) {
                continue;
            }

            BorderData data = new BorderData();
            data.setWorld(worldName);
            data.setCenterX(section.getDouble("centerX", 0));
            data.setCenterZ(section.getDouble("centerZ", 0));
            data.setRadiusX(section.getDouble("radiusX", 500));
            data.setRadiusZ(section.getDouble("radiusZ", 500));
            data.setShape(section.getString("shape", "square"));
            data.setWrap(section.getString("wrap", "none"));

            for (String uuidStr : section.getStringList("bypass")) {
                try {
                    data.getBypassPlayers().add(UUID.fromString(uuidStr));
                } catch (IllegalArgumentException ignored) {
                }
            }

            borders.put(worldName, data);
        }

        plugin.getLogger().info("Border: " + borders.size() + " Border(s) geladen.");
    }

    private void loadConfig() {
        message = plugin.getConfig().getString("border-options.message", "&cDu hast das Ende der Welt erreicht!");
        useActionBar = plugin.getConfig().getBoolean("border-options.use-action-bar", true);
        effect = plugin.getConfig().getString("border-options.effect", "ender_signal");
        sound = plugin.getConfig().getString("border-options.sound", "entity_enderman_teleport");
        preventMobSpawns = plugin.getConfig().getBoolean("border-options.prevent-mob-spawns", false);
        preventEnderpearl = plugin.getConfig().getBoolean("border-options.prevent-enderpearl", false);
        preventChorusFruit = plugin.getConfig().getBoolean("border-options.prevent-chorus-fruit", false);
        visualizerEnabled = plugin.getConfig().getBoolean("border-options.visualizer-enabled", true);
        visualizerRange = plugin.getConfig().getInt("border-options.visualizer-range", 8);
        visualizerColor = plugin.getConfig().getString("border-options.visualizer-color", "20A0FF");
        checkInterval = plugin.getConfig().getLong("border-options.check-interval", 20);
        BorderColor.parseColor(visualizerColor);
    }

    public void reloadConfig() {
        plugin.reloadConfig();
        loadConfig();
    }

    public void flushIfDirty() {
        if (!dirty) {
            return;
        }
        dirty = false;
        save();
    }

    public void forceSave() {
        dirty = false;
        save();
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (BorderData data : borders.values()) {
            config.set(data.getWorld() + ".centerX", data.getCenterX());
            config.set(data.getWorld() + ".centerZ", data.getCenterZ());
            config.set(data.getWorld() + ".radiusX", data.getRadiusX());
            config.set(data.getWorld() + ".radiusZ", data.getRadiusZ());
            config.set(data.getWorld() + ".shape", data.getShape());
            config.set(data.getWorld() + ".wrap", data.getWrap());
            config.set(data.getWorld() + ".bypass",
                    data.getBypassPlayers().stream().map(UUID::toString).toList());
        }

        try {
            config.save(bordersFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Konnte borders.yml nicht speichern", e);
        }
    }

    public void markDirty() {
        dirty = true;
    }

    public void addBorder(String worldName, double centerX, double centerZ, double size) {
        addBorder(worldName, centerX, centerZ, size / 2.0, size / 2.0, "square", "none");
    }

    public void addBorder(String worldName, double centerX, double centerZ, double radiusX, double radiusZ, String shape, String wrap) {
        BorderData data = new BorderData(worldName, centerX, centerZ, radiusX, radiusZ, shape, wrap);
        borders.put(worldName, data);
        markDirty();
    }

    public void removeBorder(String worldName) {
        borders.remove(worldName);
        markDirty();
    }

    public BorderData getBorder(String worldName) {
        return borders.get(worldName);
    }

    public Collection<BorderData> getAllBorders() {
        return borders.values();
    }

    public void setBorderRadius(String worldName, double radiusX, double radiusZ) {
        BorderData data = borders.get(worldName);
        if (data == null) {
            return;
        }
        data.setRadiusX(radiusX);
        data.setRadiusZ(radiusZ);
        markDirty();
    }

    public void setBorderCenter(String worldName, double centerX, double centerZ) {
        BorderData data = borders.get(worldName);
        if (data == null) {
            return;
        }
        data.setCenterX(centerX);
        data.setCenterZ(centerZ);
        markDirty();
    }

    public void setBorderShape(String worldName, String shape) {
        BorderData data = borders.get(worldName);
        if (data == null) {
            return;
        }
        data.setShape(shape);
        markDirty();
    }

    public void setBorderWrap(String worldName, String wrap) {
        BorderData data = borders.get(worldName);
        if (data == null) {
            return;
        }
        data.setWrap(wrap);
        markDirty();
    }

    public void setBypass(String worldName, UUID playerUuid, boolean bypass) {
        BorderData data = borders.get(worldName);
        if (data == null) {
            return;
        }
        data.setBypass(playerUuid, bypass);
        markDirty();
    }

    public boolean isBypassing(String worldName, UUID playerUuid) {
        BorderData data = borders.get(worldName);
        return data != null && data.isBypassing(playerUuid);
    }

    public PlayerData getPlayerData(UUID playerUuid) {
        return players.computeIfAbsent(playerUuid, PlayerData::new);
    }

    public void removePlayerData(UUID playerUuid) {
        players.remove(playerUuid);
    }

    public String getMessage() {
        return message;
    }

    public boolean useActionBar() {
        return useActionBar;
    }

    public String getEffect() {
        return effect;
    }

    public String getSound() {
        return sound;
    }

    public boolean preventMobSpawns() {
        return preventMobSpawns;
    }

    public boolean preventEnderpearl() {
        return preventEnderpearl;
    }

    public boolean preventChorusFruit() {
        return preventChorusFruit;
    }

    public boolean isVisualizerEnabled() {
        return visualizerEnabled;
    }

    public int getVisualizerRange() {
        return visualizerRange;
    }

    public long getCheckInterval() {
        return checkInterval;
    }

    public TimePerDayPlugin getPlugin() {
        return plugin;
    }
}