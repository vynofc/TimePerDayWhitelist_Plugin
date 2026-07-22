package fun.vynofc.timeperday;

import fun.vynofc.timeperday.border.BorderCheckTask;
import fun.vynofc.timeperday.border.BorderData;
import fun.vynofc.timeperday.border.BorderListener;
import fun.vynofc.timeperday.border.BorderManager;
import fun.vynofc.timeperday.border.util.BorderColor;
import fun.vynofc.timeperday.border.util.Particles;
import fun.vynofc.timeperday.command.TimeCommand;
import fun.vynofc.timeperday.command.AdminTimeCommand;
import fun.vynofc.timeperday.command.DebugTimeCommand;
import fun.vynofc.timeperday.gui.AdminMenuListener;
import fun.vynofc.timeperday.gui.AdminMenuService;
import fun.vynofc.timeperday.gui.UserSettingsMenuListener;
import fun.vynofc.timeperday.gui.UserSettingsMenuService;
import fun.vynofc.timeperday.listener.PlayerListener;
import fun.vynofc.timeperday.manager.PlayerTimeManager;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class TimePerDayPlugin extends JavaPlugin {

    private PlayerTimeManager timeManager;
    private AdminMenuService adminMenuService;
    private UserSettingsMenuService userSettingsMenuService;
    private BorderManager borderManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        timeManager = new PlayerTimeManager(this);
        timeManager.load();
        adminMenuService = new AdminMenuService(this, timeManager);
        userSettingsMenuService = new UserSettingsMenuService(timeManager);
        borderManager = new BorderManager(this);
        borderManager.load();

        getServer().getPluginManager().registerEvents(new PlayerListener(this, timeManager, borderManager), this);
        getServer().getPluginManager().registerEvents(new AdminMenuListener(adminMenuService), this);
        getServer().getPluginManager().registerEvents(new UserSettingsMenuListener(userSettingsMenuService), this);
        getServer().getPluginManager().registerEvents(new BorderListener(borderManager), this);

        AdminTimeCommand adminCmd = new AdminTimeCommand(this, timeManager, adminMenuService, borderManager);
        var adminCommand = getCommand("tpdadmin");
        if (adminCommand != null) {
            adminCommand.setExecutor(adminCmd);
            adminCommand.setTabCompleter(adminCmd);
        }

        TimeCommand timeCmd = new TimeCommand(timeManager, userSettingsMenuService);
        var timeCommand = getCommand("tpd");
        if (timeCommand != null) {
            timeCommand.setExecutor(timeCmd);
        }

        DebugTimeCommand debugCmd = new DebugTimeCommand(timeManager, this);
        var debugCommand = getCommand("tpddebug");
        if (debugCommand != null) {
            debugCommand.setExecutor(debugCmd);
            debugCommand.setTabCompleter(debugCmd);
        }

        // Globaler Takt läuft im Server-Kontext; spielerbezogene Aktionen werden
        // anschließend jeweils auf den Scheduler des Spielers delegiert.
        getServer().getGlobalRegionScheduler().runAtFixedRate(this,
                task -> timeManager.tickOnlinePlayers(), 1L, 20L);

        BorderCheckTask borderCheckTask = new BorderCheckTask(borderManager, this);
        long checkInterval = borderManager.getCheckInterval();
        getServer().getGlobalRegionScheduler().runAtFixedRate(this,
                task -> borderCheckTask.run(), 1L, checkInterval);

        startBorderVisualizer();

        getLogger().info("TimePerDayWhitelist aktiviert.");
    }

    @Override
    public void onDisable() {
        if (timeManager != null) {
            timeManager.onDisable();
            timeManager.save();
        }
        if (borderManager != null) {
            borderManager.forceSave();
        }
        getLogger().info("TimePerDayWhitelist deaktiviert.");
    }

    public PlayerTimeManager getTimeManager() {
        return timeManager;
    }

    public AdminMenuService getAdminMenuService() {
        return adminMenuService;
    }

    public UserSettingsMenuService getUserSettingsMenuService() {
        return userSettingsMenuService;
    }

    public BorderManager getBorderManager() {
        return borderManager;
    }

    private void startBorderVisualizer() {
        if (!borderManager.isVisualizerEnabled()) {
            return;
        }
        final int maxRange = borderManager.getVisualizerRange();
        Particles.setMaxDistance(maxRange);
        final AtomicLong tick = new AtomicLong();
        final Particle.DustOptions dustOptions = new Particle.DustOptions(Color.fromRGB(BorderColor.getColor()), 1);
        getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> {
            tick.incrementAndGet();
            final double offsetPercent = (tick.longValue() % 20) / 20d;
            for (final org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
                player.getScheduler().run(this, pt -> {
                    final World world = player.getWorld();
                    final BorderData border = borderManager.getBorder(world.getName());
                    if (border == null) {
                        return;
                    }
                    final List<Location> particleLocations = Particles.at(player, border, offsetPercent);
                    for (final Location location : particleLocations) {
                        player.spawnParticle(Particle.DUST, location, 1, dustOptions);
                    }
                }, null);
            }
        }, 1L, 1L);
    }
}


