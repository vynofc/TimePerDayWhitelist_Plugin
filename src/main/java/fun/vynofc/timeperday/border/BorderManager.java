package fun.vynofc.timeperday.border;

import fun.vynofc.timeperday.TimePerDayPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
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
    private final File bordersFile;

    public BorderManager(TimePerDayPlugin plugin) {
        this.plugin = plugin;
        this.bordersFile = new File(plugin.getDataFolder(), "borders.yml");
    }

    public void load() {
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
            applyToWorld(worldName, data);
        }

        plugin.getLogger().info("Border: " + borders.size() + " Border(s) geladen.");
    }

    public void save() {
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

    public void addBorder(String worldName, double centerX, double centerZ, double size) {
        addBorder(worldName, centerX, centerZ, size / 2.0, size / 2.0, "square", "none");
    }

    public void addBorder(String worldName, double centerX, double centerZ, double radiusX, double radiusZ, String shape, String wrap) {
        BorderData data = new BorderData(worldName, centerX, centerZ, radiusX, radiusZ, shape, wrap);
        borders.put(worldName, data);
        applyToWorld(worldName, data);
        save();
    }

    public void removeBorder(String worldName) {
        borders.remove(worldName);
        save();
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
        applyToWorld(worldName, data);
        save();
    }

    public void setBorderCenter(String worldName, double centerX, double centerZ) {
        BorderData data = borders.get(worldName);
        if (data == null) {
            return;
        }
        data.setCenterX(centerX);
        data.setCenterZ(centerZ);
        applyToWorld(worldName, data);
        save();
    }

    public void setBorderShape(String worldName, String shape) {
        BorderData data = borders.get(worldName);
        if (data == null) {
            return;
        }
        data.setShape(shape);
        applyToWorld(worldName, data);
        save();
    }

    public void setBorderWrap(String worldName, String wrap) {
        BorderData data = borders.get(worldName);
        if (data == null) {
            return;
        }
        data.setWrap(wrap);
        save();
    }

    public void setBypass(String worldName, UUID playerUuid, boolean bypass) {
        BorderData data = borders.get(worldName);
        if (data == null) {
            return;
        }
        data.setBypass(playerUuid, bypass);
        save();
    }

    public boolean isBypassing(String worldName, UUID playerUuid) {
        BorderData data = borders.get(worldName);
        return data != null && data.isBypassing(playerUuid);
    }

    private void applyToWorld(String worldName, BorderData data) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }

        var wb = world.getWorldBorder();
        wb.setCenter(data.getCenterX(), data.getCenterZ());
        wb.setSize(data.getSize());
    }
}