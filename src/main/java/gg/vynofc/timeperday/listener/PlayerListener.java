package gg.vynofc.timeperday.listener;

import gg.vynofc.timeperday.TimePerDayPlugin;
import gg.vynofc.timeperday.manager.PlayerTimeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerListener implements Listener {

    private final TimePerDayPlugin plugin;
    private final PlayerTimeManager timeManager;

    public PlayerListener(TimePerDayPlugin plugin, PlayerTimeManager timeManager) {
        this.plugin = plugin;
        this.timeManager = timeManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        var uuid = player.getUniqueId();

        // Whitelisted oder Bypass-Permission → keine Aktion
        if (timeManager.isWhitelisted(uuid) || player.hasPermission("timeperday.bypass")) {
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
        } else if (plugin.getConfig().getBoolean("show-on-join", true)) {
            boolean kitGiven = timeManager.grantDailyKit(player);
            double level = timeManager.getTotalLevel(uuid);
            double points = timeManager.getSessionPoints(uuid);
            int kitLevel = timeManager.getBestKitLevelFor(level);

            // Verbleibende Zeit anzeigen
            String template = plugin.getConfig().getString(
                    "messages.join-info",
                    "<green>Verbleibende Zeit: <yellow>{remaining}<green> | Level: <yellow>{level}"
                            + "<green> | Session: <yellow>{session-points}<green> | Kit: <yellow>{kit-level}");
            String msg = template
                    .replace("{remaining}", PlayerTimeManager.formatTime(remaining))
                    .replace("{level}", PlayerTimeManager.formatLevel(level))
                    .replace("{session-points}", PlayerTimeManager.formatLevel(points))
                    .replace("{kit-level}", kitLevel > 0 ? String.valueOf(kitLevel) : "Keins")
                    .replace("{kit-given}", kitGiven ? "Ja" : "Nein")
                    .replaceAll("<[^>]+>", "");

            player.getScheduler().run(plugin, task ->
                    player.sendMessage(Component.text(msg, NamedTextColor.GREEN)), null);
        }
    }
}
