package de.niliees.timeperday;

import de.niliees.timeperday.command.TimeCommand;
import de.niliees.timeperday.listener.PlayerListener;
import de.niliees.timeperday.manager.PlayerTimeManager;
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

        TimeCommand cmd = new TimeCommand(this, timeManager);
        var command = getCommand("timeperday");
        if (command != null) {
            command.setExecutor(cmd);
            command.setTabCompleter(cmd);
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
