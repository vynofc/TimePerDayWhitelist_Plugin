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
            String line1 = plugin.getConfig().getString(
                    "messages.kick-line1", "Deine tägliche Spielzeit ist aufgebraucht!")
                    .replaceAll("<[^>]+>", "");
            String line2 = plugin.getConfig().getString(
                    "messages.kick-line2", "Du kannst morgen wieder spielen.")
                    .replaceAll("<[^>]+>", "");

            player.getScheduler().runDelayed(plugin, task ->
                    player.kick(Component.text()
                            .append(Component.text(line1, NamedTextColor.RED))
                            .appendNewline()
                            .append(Component.text(line2, NamedTextColor.GRAY))
                            .build()), null, 1L);
        } else if (plugin.getConfig().getBoolean("show-on-join", true)) {
            // Verbleibende Zeit anzeigen
            String template = plugin.getConfig().getString(
                    "messages.join-info",
                    "<green>Verbleibende Spielzeit heute: <yellow>{remaining}");
            String msg = template
                    .replace("{remaining}", PlayerTimeManager.formatTime(remaining))
                    .replaceAll("<[^>]+>", "");

            player.getScheduler().run(plugin, task ->
                    player.sendMessage(Component.text(msg, NamedTextColor.GREEN)), null);
        }
    }
}
