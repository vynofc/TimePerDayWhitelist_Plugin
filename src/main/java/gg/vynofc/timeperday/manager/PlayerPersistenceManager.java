package gg.vynofc.timeperday.manager;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

class PlayerPersistenceManager {

    private final PlayerTimeManager manager;

    PlayerPersistenceManager(PlayerTimeManager manager) {
        this.manager = manager;
    }

    synchronized void load() {
        if (!manager.dataFile.exists()) {
            manager.dataFile.getParentFile().mkdirs();
            try {
                manager.dataFile.createNewFile();
            } catch (IOException e) {
                manager.plugin.getLogger().log(Level.SEVERE, "Konnte playerdata.yml nicht erstellen", e);
            }
        }

        YamlConfiguration dataConfig = YamlConfiguration.loadConfiguration(manager.dataFile);
        manager.dataConfig = dataConfig;

        String savedDate = dataConfig.getString("date", manager.currentDate);
        boolean newDay = !savedDate.equals(manager.currentDate);

        if (newDay) {
            manager.plugin.getLogger().info("Neuer Tag erkannt - Spielzeiten werden zurueckgesetzt.");
        } else {
            loadLongSection("playtime", manager.playedToday);
            loadDoubleSection("session-points", manager.sessionPoints);
            loadUuidSetSection("pending-dayover-reset", manager.pendingDayOverReset);
        }

        loadLongSection("limits", manager.playerLimits);
        loadBooleanSection("whitelist", manager.whitelist);
        loadDoubleSection("total-level", manager.totalLevel);
        loadStringSection("last-kit-claim-date", manager.lastKitClaimDate);
        manager.spawnKits = readSpawnKits();
    }

    synchronized void save() {
        YamlConfiguration dataConfig = new YamlConfiguration();
        manager.dataConfig = dataConfig;
        dataConfig.set("date", manager.currentDate);
        manager.playedToday.forEach((uuid, val) -> dataConfig.set("playtime." + uuid, val));
        manager.playerLimits.forEach((uuid, val) -> dataConfig.set("limits." + uuid, val));
        manager.whitelist.forEach((uuid, val) -> dataConfig.set("whitelist." + uuid, val));
        manager.sessionPoints.forEach((uuid, val) -> dataConfig.set("session-points." + uuid, val));
        manager.totalLevel.forEach((uuid, val) -> dataConfig.set("total-level." + uuid, val));
        manager.lastKitClaimDate.forEach((uuid, val) -> dataConfig.set("last-kit-claim-date." + uuid, val));
        int pendingResetIndex = 0;
        for (UUID uuid : manager.pendingDayOverReset) {
            dataConfig.set("pending-dayover-reset." + pendingResetIndex, uuid.toString());
            pendingResetIndex++;
        }

        try {
            dataConfig.save(manager.dataFile);
        } catch (IOException e) {
            manager.plugin.getLogger().log(Level.SEVERE, "Konnte playerdata.yml nicht speichern", e);
        }
    }

    void reload() {
        save();
        manager.reloadDefaultLimitFromConfig();
        manager.playedToday.clear();
        manager.playerLimits.clear();
        manager.whitelist.clear();
        manager.sessionPoints.clear();
        manager.totalLevel.clear();
        manager.lastKitClaimDate.clear();
        manager.pendingDayOverReset.clear();
        load();
    }

    private void loadLongSection(String path, ConcurrentHashMap<UUID, Long> map) {
        YamlConfiguration dataConfig = manager.dataConfig;
        if (dataConfig == null || !dataConfig.contains(path)) {
            return;
        }
        var section = dataConfig.getConfigurationSection(path);
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            try {
                map.put(UUID.fromString(key), section.getLong(key));
            } catch (IllegalArgumentException e) {
                manager.plugin.getLogger().warning("Ungueltige UUID in playerdata.yml: " + key);
            }
        }
    }

    private void loadBooleanSection(String path, ConcurrentHashMap<UUID, Boolean> map) {
        YamlConfiguration dataConfig = manager.dataConfig;
        if (dataConfig == null || !dataConfig.contains(path)) {
            return;
        }
        var section = dataConfig.getConfigurationSection(path);
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            try {
                map.put(UUID.fromString(key), section.getBoolean(key));
            } catch (IllegalArgumentException e) {
                manager.plugin.getLogger().warning("Ungueltige UUID in playerdata.yml: " + key);
            }
        }
    }

    private void loadDoubleSection(String path, ConcurrentHashMap<UUID, Double> map) {
        YamlConfiguration dataConfig = manager.dataConfig;
        if (dataConfig == null || !dataConfig.contains(path)) {
            return;
        }
        var section = dataConfig.getConfigurationSection(path);
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            try {
                map.put(UUID.fromString(key), section.getDouble(key));
            } catch (IllegalArgumentException e) {
                manager.plugin.getLogger().warning("Ungueltige UUID in playerdata.yml: " + key);
            }
        }
    }

    private void loadStringSection(String path, ConcurrentHashMap<UUID, String> map) {
        YamlConfiguration dataConfig = manager.dataConfig;
        if (dataConfig == null || !dataConfig.contains(path)) {
            return;
        }
        var section = dataConfig.getConfigurationSection(path);
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            try {
                map.put(UUID.fromString(key), section.getString(key, ""));
            } catch (IllegalArgumentException e) {
                manager.plugin.getLogger().warning("Ungueltige UUID in playerdata.yml: " + key);
            }
        }
    }

    private void loadUuidSetSection(String path, java.util.Set<UUID> target) {
        YamlConfiguration dataConfig = manager.dataConfig;
        if (dataConfig == null || !dataConfig.contains(path)) {
            return;
        }
        var section = dataConfig.getConfigurationSection(path);
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            String uuidText = section.getString(key, "");
            try {
                target.add(UUID.fromString(uuidText));
            } catch (IllegalArgumentException e) {
                manager.plugin.getLogger().warning(
                        "Ungueltige UUID in playerdata.yml (" + path + "." + key + "): " + uuidText);
            }
        }
    }

    private NavigableMap<Integer, Map<Material, Integer>> readSpawnKits() {
        NavigableMap<Integer, Map<Material, Integer>> kits = new TreeMap<>();
        var root = manager.plugin.getConfig().getConfigurationSection("progression.spawn-kits");
        if (root == null) {
            return kits;
        }

        for (String levelKey : root.getKeys(false)) {
            int level;
            try {
                level = Integer.parseInt(levelKey);
            } catch (NumberFormatException e) {
                manager.plugin.getLogger().warning("Ungueltige Kit-Stufe in config.yml: " + levelKey);
                continue;
            }

            var section = root.getConfigurationSection(levelKey);
            if (section == null) {
                continue;
            }

            Map<Material, Integer> kit = new HashMap<>();
            for (String materialKey : section.getKeys(false)) {
                Material material = Material.matchMaterial(materialKey);
                if (material == null) {
                    manager.plugin.getLogger().warning("Ungueltiges Material im Kit " + level + ": " + materialKey);
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
}
