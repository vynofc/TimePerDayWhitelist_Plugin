package fun.vynofc.timeperday.manager;

import org.bukkit.Bukkit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

class WorldRegenerationManager {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PlayerTimeManager manager;

    private boolean enabled;
    private String worldNamePrefix;
    private int chunkRadius;
    private int chunkyQuietMs;

    private World newWorld;
    private String newWorldName;
    private String oldWorldName;
    private String activeWorldName;
    private boolean chunkyRunning;

    WorldRegenerationManager(PlayerTimeManager manager) {
        this.manager = manager;
    }

    void load() {
        this.enabled = manager.plugin.getConfig().getBoolean("world-regeneration.enabled", false);
        this.worldNamePrefix = manager.plugin.getConfig().getString("world-regeneration.world-name-prefix", "world_");
        this.chunkRadius = manager.plugin.getConfig().getInt("world-regeneration.chunk-radius", 16);
        this.chunkyQuietMs = manager.plugin.getConfig().getInt("world-regeneration.chunky-quiet-ms", 500);

        if (enabled) {
            schedulePreGeneration();
            manager.plugin.getLogger().info("WorldRegeneration: Aktiviert (Praefix: " + worldNamePrefix + ").");
        }
    }

    void reload() {
        cancelChunkyIfRunning();
        load();
    }

    private void schedulePreGeneration() {
        LocalDateTime now = LocalDateTime.now(manager.resetZoneId);
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        LocalDateTime preGenTime = midnight.minusMinutes(30);

        long delaySeconds = Duration.between(now, preGenTime).getSeconds();
        if (delaySeconds < 0) {
            delaySeconds += 86400L;
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
                    + newWorldName + "' nicht erstellen.");
            return;
        }

        manager.plugin.getLogger().info("WorldRegeneration: Neue Welt '" + newWorldName + "' erstellt.");

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
        updateServerPropertiesLevelName(worldName);

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

        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(serverProperties)) {
            props.load(in);
        } catch (IOException e) {
            manager.plugin.getLogger().warning(
                    "WorldRegeneration: Konnte server.properties nicht lesen: " + e.getMessage());
            return;
        }

        props.setProperty("level-name", levelName);

        try (FileOutputStream out = new FileOutputStream(serverProperties)) {
            props.store(out, "TimePerDayWhitelist world regeneration");
        } catch (IOException e) {
            manager.plugin.getLogger().warning(
                    "WorldRegeneration: Konnte server.properties nicht schreiben: " + e.getMessage());
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
            for (Player player : oldWorld.getPlayers()) {
                World targetWorld = Bukkit.getWorlds().get(0);
                player.teleport(targetWorld.getSpawnLocation());
            }

            boolean unloaded = Bukkit.unloadWorld(oldWorld, false);
            if (!unloaded) {
                manager.plugin.getLogger().warning(
                        "WorldRegeneration: Konnte alte Welt '" + oldWorldName + "' nicht entladen.");
                oldWorldName = null;
                return;
            }
        }

        deleteWorldFolder(oldWorldName);
        manager.plugin.getLogger().info("WorldRegeneration: Alte Welt '" + oldWorldName + "' geloescht.");
        oldWorldName = null;
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