package gg.vynofc.timeperday;

import gg.vynofc.timeperday.command.TimeCommand;
import gg.vynofc.timeperday.command.AdminTimeCommand;
import gg.vynofc.timeperday.listener.PlayerListener;
import gg.vynofc.timeperday.manager.PlayerTimeManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;

public class TimePerDayPlugin extends JavaPlugin {

    private PlayerTimeManager timeManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        timeManager = new PlayerTimeManager(this);
        timeManager.load();

        getServer().getPluginManager().registerEvents(new PlayerListener(this, timeManager), this);

        AdminTimeCommand adminCmd = new AdminTimeCommand(this, timeManager);
        var adminCommand = getCommand("admintime");
        if (adminCommand != null) {
            adminCommand.setExecutor(adminCmd);
            adminCommand.setTabCompleter(adminCmd);
        }

        TimeCommand timeCmd = new TimeCommand(timeManager);
        var timeCommand = getCommand("time");
        if (timeCommand != null) {
            timeCommand.setExecutor(timeCmd);
        }

        // Folia & Paper 1.21+: AsyncScheduler – sekündliches Tick
        getServer().getAsyncScheduler().runAtFixedRate(this,
                task -> timeManager.tick(), 0L, 1L, TimeUnit.SECONDS);

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
}
