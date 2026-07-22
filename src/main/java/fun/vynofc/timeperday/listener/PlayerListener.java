package fun.vynofc.timeperday.listener;

import fun.vynofc.timeperday.TimePerDayPlugin;
import fun.vynofc.timeperday.manager.PlayerTimeManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerListener implements Listener {

    private final TimePerDayPlugin plugin;
    private final PlayerTimeManager timeManager;

    public PlayerListener(TimePerDayPlugin plugin, PlayerTimeManager timeManager) {
        this.plugin = plugin;
        this.timeManager = timeManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();

        timeManager.handlePlayerJoinWorldCheck(player);

        var uuid = player.getUniqueId();

        // Whitelisted oder Bypass-Permission → keine Aktion
        if (timeManager.isWhitelisted(uuid) || player.hasPermission("timeperday.bypass")) {
            return;
        }

        // Ausstehender Day-Over-Reset: Inventar leeren und zu Spawn teleportieren,
        // danach Kit vergeben und Join-Nachricht anzeigen.
        // 1-Tick-Verzögerung: PlayerJoinEvent muss erst abgeschlossen sein,
        // bevor Inventar/Position geändert werden dürfen (gleiches Muster wie Kick).
        if (timeManager.consumePendingDayOverReset(uuid)) {
            player.getScheduler().runDelayed(plugin, task -> {
                timeManager.applyDayOverReset(player);
                boolean kitGiven = timeManager.grantDailyKit(player);
                if (plugin.getConfig().getBoolean("show-on-join", true)) {
                    player.sendMessage(timeManager.buildJoinInfoComponent(player, kitGiven));
                }
            }, null, 1L);
            return;
        }

        long limit = timeManager.getLimit(uuid);
        long played = timeManager.getPlayedToday(uuid);
        long remaining = limit - played;

        if (remaining <= 0) {
            // Limit bereits ausgeschöpft – mit 1 Tick Verzögerung rauswerfen
            // (PlayerJoinEvent muss erst abgeschlossen sein)
            player.getScheduler().runDelayed(plugin, task ->
                    player.kick(timeManager.buildJoinKickComponent(uuid)), null, 1L);
        } else {
            boolean kitGiven = timeManager.grantDailyKit(player);
            if (plugin.getConfig().getBoolean("show-on-join", true)) {
                player.sendMessage(timeManager.buildJoinInfoComponent(player, kitGiven));
            }
        }
    }
}


