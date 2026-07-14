package gg.vynofc.timeperday;

import gg.vynofc.timeperday.command.TimeCommand;
import gg.vynofc.timeperday.command.AdminTimeCommand;
import gg.vynofc.timeperday.command.DebugTimeCommand;
import gg.vynofc.timeperday.gui.AdminMenuListener;
import gg.vynofc.timeperday.gui.AdminMenuService;
import gg.vynofc.timeperday.listener.PlayerItemListener;
import gg.vynofc.timeperday.listener.PlayerListener;
import gg.vynofc.timeperday.manager.PlayerTimeManager;
import org.bukkit.plugin.java.JavaPlugin;

public class TimePerDayPlugin extends JavaPlugin {

    private PlayerTimeManager timeManager;
    private AdminMenuService adminMenuService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        timeManager = new PlayerTimeManager(this);
        timeManager.load();
        adminMenuService = new AdminMenuService(this, timeManager);

        getServer().getPluginManager().registerEvents(new PlayerListener(this, timeManager), this);
        getServer().getPluginManager().registerEvents(new PlayerItemListener(timeManager), this);
        getServer().getPluginManager().registerEvents(new AdminMenuListener(adminMenuService), this);

        AdminTimeCommand adminCmd = new AdminTimeCommand(this, timeManager, adminMenuService);
        var adminCommand = getCommand("admintime");
        if (adminCommand != null) {
            adminCommand.setExecutor(adminCmd);
            adminCommand.setTabCompleter(adminCmd);
        }

        TimeCommand timeCmd = new TimeCommand(timeManager);
        var timeCommand = getCommand("timeleft");
        if (timeCommand != null) {
            timeCommand.setExecutor(timeCmd);
        }

        DebugTimeCommand debugCmd = new DebugTimeCommand(timeManager);
        var debugCommand = getCommand("debugtime");
        if (debugCommand != null) {
            debugCommand.setExecutor(debugCmd);
            debugCommand.setTabCompleter(debugCmd);
        }

        // Globaler Takt läuft im Server-Kontext; spielerbezogene Aktionen werden
        // anschließend jeweils auf den Scheduler des Spielers delegiert.
        getServer().getGlobalRegionScheduler().runAtFixedRate(this,
                task -> timeManager.tickOnlinePlayers(), 1L, 20L);

        getLogger().info("TimePerDayWhitelist aktiviert.");
    }

    @Override
    public void onDisable() {
        if (timeManager != null) {
            timeManager.save();
        }
        getLogger().info("TimePerDayWhitelist deaktiviert.");
    }

    public PlayerTimeManager getTimeManager() {
        return timeManager;
    }

    public AdminMenuService getAdminMenuService() {
        return adminMenuService;
    }
}
