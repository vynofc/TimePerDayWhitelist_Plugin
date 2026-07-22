package fun.vynofc.timeperday;

import fun.vynofc.timeperday.border.BorderCheckTask;
import fun.vynofc.timeperday.border.BorderListener;
import fun.vynofc.timeperday.border.BorderManager;
import fun.vynofc.timeperday.command.TimeCommand;
import fun.vynofc.timeperday.command.AdminTimeCommand;
import fun.vynofc.timeperday.command.DebugTimeCommand;
import fun.vynofc.timeperday.gui.AdminMenuListener;
import fun.vynofc.timeperday.gui.AdminMenuService;
import fun.vynofc.timeperday.gui.UserSettingsMenuListener;
import fun.vynofc.timeperday.gui.UserSettingsMenuService;
import fun.vynofc.timeperday.listener.PlayerListener;
import fun.vynofc.timeperday.manager.PlayerTimeManager;
import org.bukkit.plugin.java.JavaPlugin;

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

        getServer().getPluginManager().registerEvents(new PlayerListener(this, timeManager), this);
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

        BorderCheckTask borderCheckTask = new BorderCheckTask(borderManager);
        getServer().getGlobalRegionScheduler().runAtFixedRate(this,
                task -> borderCheckTask.run(), 1L, 20L);

        getLogger().info("TimePerDayWhitelist aktiviert.");
    }

    @Override
    public void onDisable() {
        if (timeManager != null) {
            timeManager.onDisable();
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

    public UserSettingsMenuService getUserSettingsMenuService() {
        return userSettingsMenuService;
    }

    public BorderManager getBorderManager() {
        return borderManager;
    }
}


