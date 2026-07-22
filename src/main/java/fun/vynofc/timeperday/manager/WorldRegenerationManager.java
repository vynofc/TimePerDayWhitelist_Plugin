package fun.vynofc.timeperday.manager;

import fun.vynofc.timeperday.border.BorderManager;
import org.bukkit.Bukkit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class WorldRegenerationManager {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String DELETION_MARKER = "worldregeneration_deletion.txt";

    private final PlayerTimeManager manager;

    private boolean enabled;
    private String worldNamePrefix;
    private int chunkRadius;
    private int chunkyQuietMs;
    private double defaultBorderSize;

    private World newWorld;
    private String newWorldName;
    private String oldWorldName;
    private String activeWorldName;
    private volatile boolean chunkyRunning;

    WorldRegenerationManager(PlayerTimeManager manager) {
        this.manager = manager;
    }

    void load() {
        this.enabled = manager.plugin.getConfig().getBoolean("world-regeneration.enabled", false);
        this.worldNamePrefix = manager.plugin.getConfig().getString("world-regeneration.world-name-prefix", "world_");
        this.chunkRadius = manager.plugin.getConfig().getInt("world-regeneration.chunk-radius", 16);
        this.chunkyQuietMs = manager.plugin.getConfig().getInt("world-regeneration.chunky-quiet-ms", 500);
        this.defaultBorderSize = manager.plugin.getConfig().getDouble("world-regeneration.default-border-size", 1000);

        deleteMarkedWorlds();

        if (enabled) {
            schedulePreGeneration();
            manager.plugin.getLogger().info("WorldRegeneration: Aktiviert (Praefix: " + worldNamePrefix + ").");
        }
    }

    void reload() {
        cancelChunkyIfRunning();
        load();
    }

    private void deleteMarkedWorlds() {
        File markerFile = new File(manager.plugin.getDataFolder(), DELETION_MARKER);
        if (!markerFile.exists()) {
            return;
        }

        try {
            List<String> lines = Files.readAllLines(markerFile.toPath());
            for (String worldName : lines) {
                worldName = worldName.trim();
                if (worldName.isEmpty()) {
                    continue;
                }
                deleteWorldFolder(worldName);
                manager.plugin.getLogger().info("WorldRegeneration: Alte Welt '" + worldName + "' vom Disk geloescht.");
            }
        } catch (IOException e) {
            manager.plugin.getLogger().warning(
                    "WorldRegeneration: Konnte Deletion-Marker nicht lesen: " + e.getMessage());
        }

        markerFile.delete();
    }

    private void schedulePreGeneration() {
        LocalDateTime now = LocalDateTime.now(manager.resetZoneId);
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        LocalDateTime preGenTime = midnight.minusMinutes(30);

        long delaySeconds = Duration.between(now, preGenTime).getSeconds();
        if (delaySeconds < 0) {
            delaySeconds += 86400L;
            preGenTime = preGenTime.plusDays(1);
        }

        long delayTicks = delaySeconds * 20L;
        manager.plugin.getLogger().info("WorldRegeneration: Pre-Generation geplant in "
                + delaySeconds + "s (um " + preGenTime + ").");

        manager.plugin.getServer().getGlobalRegionScheduler().runDelayed(
                manager.plugin, task -> onPreGenerationTime(), delayTicks);
    }

    private void onPreGenerationTime() {
        String dateStr = LocalDate.now(manager.resetZoneId).format(DATE_FORMAT);
        newWorldName = worldNamePrefix + dateStr;

        WorldCreator creator = new WorldCreator(newWorldName);
        creator.environment(World.Environment.NORMAL);
        creator.type(WorldType.NORMAL);
        creator.generateStructures(true);
        newWorld = creator.createWorld();

        if (newWorld == null) {
            manager.plugin.getLogger().warning("WorldRegeneration: Konnte neue Welt '"
                    + newWorldName + "' nicht erstellen. Planung fuer morgen laeuft weiter.");
            schedulePreGeneration();
            return;
        }

        manager.plugin.getLogger().info("WorldRegeneration: Neue Welt '" + newWorldName + "' erstellt.");

        manager.plugin.getBorderManager().addBorder(newWorldName, 0, 0, defaultBorderSize);

        if (Bukkit.getPluginManager().getPlugin("Chunky") != null) {
            startChunkyGeneration();
        } else {
            manager.plugin.getLogger().warning(
                    "WorldRegeneration: Chunky nicht gefunden - Welt wird ohne Pre-Generation verwendet.");
        }

        schedulePreGeneration();
    }

    private void startChunkyGeneration() {
        chunkyRunning = true;
        var console = Bukkit.getConsoleSender();

        Bukkit.dispatchCommand(console, "chunky quiet " + chunkyQuietMs);
        Bukkit.dispatchCommand(console, "chunky world " + newWorldName);
        Bukkit.dispatchCommand(console, "chunky center 0 0");
        Bukkit.dispatchCommand(console, "chunky radius " + chunkRadius);
        Bukkit.dispatchCommand(console, "chunky shape square");
        Bukkit.dispatchCommand(console, "chunky start");

        manager.plugin.getLogger().info("WorldRegeneration: Chunky Pre-Generation gestartet fuer '"
                + newWorldName + "' (Radius: " + chunkRadius + " Chunks, Quiet: " + chunkyQuietMs + "ms).");
    }

    void onDayOver() {
        if (!enabled || newWorld == null) {
            return;
        }

        if (chunkyRunning) {
            cancelChunkyIfRunning();
            manager.plugin.getLogger().info("WorldRegeneration: Chunky Pre-Generation abgebrochen (Tageswechsel).");
        }

        World defaultWorld = Bukkit.getWorlds().get(0);
        oldWorldName = defaultWorld != null ? defaultWorld.getName() : null;

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(newWorld.getSpawnLocation());
        }

        manager.plugin.getLogger().info("WorldRegeneration: Alle Spieler in neue Welt '"
                + newWorldName + "' teleportiert.");

        updateServerPropertiesLevelName(newWorldName);

        newWorld = null;
        newWorldName = null;

        scheduleOldWorldDeletion();
    }

    void debugDayOver() {
        if (!enabled) {
            return;
        }

        cancelChunkyIfRunning();

        for (Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            player.kick(Component.text("Debug-Tageswechsel: Welt wird neu generiert.", NamedTextColor.YELLOW));
        }

        String dateStr = LocalDate.now(manager.resetZoneId).format(DATE_FORMAT);
        String worldName = worldNamePrefix + dateStr + "_" + UUID.randomUUID().toString().substring(0, 8);

        WorldCreator creator = new WorldCreator(worldName);
        creator.environment(World.Environment.NORMAL);
        creator.type(WorldType.NORMAL);
        creator.generateStructures(true);
        World createdWorld = creator.createWorld();

        if (createdWorld == null) {
            manager.plugin.getLogger().warning("WorldRegeneration: Konnte neue Debug-Welt '"
                    + worldName + "' nicht erstellen.");
            return;
        }

        manager.plugin.getLogger().info("WorldRegeneration: Neue Debug-Welt '" + worldName + "' erstellt.");

        manager.plugin.getBorderManager().addBorder(worldName, 0, 0, defaultBorderSize);

        World defaultWorld = Bukkit.getWorlds().get(0);
        String oldName = defaultWorld != null ? defaultWorld.getName() : null;

        updateServerPropertiesLevelName(worldName);

        if (oldName != null) {
            markWorldForDeletion(oldName);
        }

        newWorld = null;
        newWorldName = null;
    }

    private void updateServerPropertiesLevelName(String levelName) {
        File serverProperties = new File(Bukkit.getWorldContainer().getParentFile(), "server.properties");
        if (!serverProperties.exists()) {
            manager.plugin.getLogger().warning(
                    "WorldRegeneration: server.properties nicht gefunden, level-name nicht aktualisiert.");
            return;
        }

        Path path = serverProperties.toPath();
        Path tempPath = new File(serverProperties.getParentFile(), "server.properties.tmp").toPath();
        boolean found = false;

        try (BufferedReader reader = Files.newBufferedReader(path);
             BufferedWriter writer = Files.newBufferedWriter(tempPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("level-name=")) {
                    writer.write("level-name=" + levelName);
                    found = true;
                } else {
                    writer.write(line);
                }
                writer.newLine();
            }
            if (!found) {
                writer.write("level-name=" + levelName);
                writer.newLine();
            }
        } catch (IOException e) {
            manager.plugin.getLogger().warning(
                    "WorldRegeneration: Konnte server.properties nicht schreiben: " + e.getMessage());
            return;
        }

        try {
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            manager.plugin.getLogger().warning(
                    "WorldRegeneration: Konnte server.properties nicht aktualisieren: " + e.getMessage());
            return;
        }

        manager.plugin.getLogger().info("WorldRegeneration: server.properties level-name auf '"
                + levelName + "' aktualisiert.");
        activeWorldName = levelName;
    }

    private void scheduleOldWorldDeletion() {
        long delayTicks = 30L * 60L * 20L;
        manager.plugin.getServer().getGlobalRegionScheduler().runDelayed(
                manager.plugin, task -> onDeleteOldWorld(), delayTicks);

        manager.plugin.getLogger().info("WorldRegeneration: Loeschung der alten Welt in 30 Minuten geplant.");
    }

    private void onDeleteOldWorld() {
        if (oldWorldName == null) {
            return;
        }

        World oldWorld = Bukkit.getWorld(oldWorldName);
        if (oldWorld != null) {
            boolean unloaded = Bukkit.unloadWorld(oldWorld, false);
            if (!unloaded) {
                manager.plugin.getLogger().warning(
                        "WorldRegeneration: Konnte alte Welt '" + oldWorldName
                                + "' nicht entladen. Wird beim naechsten Start geloescht.");
                markWorldForDeletion(oldWorldName);
                oldWorldName = null;
                return;
            }
        }

        deleteWorldFolder(oldWorldName);
        manager.plugin.getLogger().info("WorldRegeneration: Alte Welt '" + oldWorldName + "' geloescht.");
        oldWorldName = null;
    }

    private void markWorldForDeletion(String worldName) {
        File markerFile = new File(manager.plugin.getDataFolder(), DELETION_MARKER);
        try {
            List<String> lines = new ArrayList<>();
            if (markerFile.exists()) {
                lines = Files.readAllLines(markerFile.toPath());
            }
            lines.add(worldName);
            Files.write(markerFile.toPath(), lines);
            manager.plugin.getLogger().info("WorldRegeneration: Welt '" + worldName
                    + "' zum Loeschen beim naechsten Start vorgemerkt.");
        } catch (IOException e) {
            manager.plugin.getLogger().warning(
                    "WorldRegeneration: Konnte Deletion-Marker nicht schreiben: " + e.getMessage());
        }
    }

    private void deleteWorldFolder(String worldName) {
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        if (worldFolder.exists()) {
            deleteRecursively(worldFolder);
        }
    }

    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (!file.delete()) {
            manager.plugin.getLogger().warning(
                    "WorldRegeneration: Konnte Datei/Ordner nicht loeschen: " + file.getAbsolutePath());
        }
    }

    private void cancelChunkyIfRunning() {
        if (!chunkyRunning) {
            return;
        }
        if (newWorldName != null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "chunky world " + newWorldName);
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "chunky cancel");
        chunkyRunning = false;
    }

    void onDisable() {
        cancelChunkyIfRunning();
    }

    void handlePlayerJoin(Player player) {
        if (!enabled || activeWorldName == null) {
            return;
        }

        World currentWorld = player.getWorld();
        if (currentWorld.getName().equals(activeWorldName)) {
            return;
        }

        World targetWorld = Bukkit.getWorld(activeWorldName);
        if (targetWorld != null) {
            player.getScheduler().run(manager.plugin, task ->
                    player.teleport(targetWorld.getSpawnLocation()), null);
        }
    }
}