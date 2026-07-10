package gg.vynofc.timeperday.command;

import gg.vynofc.timeperday.manager.PlayerTimeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class TimeCommand implements CommandExecutor {

    private final PlayerTimeManager timeManager;

    public TimeCommand(PlayerTimeManager timeManager) {
        this.timeManager = timeManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Dieser Befehl kann nur von Spielern genutzt werden.",
                    NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("timeperday.use")) {
            player.sendMessage(Component.text("Du hast keine Berechtigung für diesen Befehl.",
                    NamedTextColor.RED));
            return true;
        }

        if (args.length > 0) {
            player.sendMessage(Component.text("Verwendung: /time", NamedTextColor.RED));
            return true;
        }

        var uuid = player.getUniqueId();
        long played = timeManager.getPlayedToday(uuid);
        long limit = timeManager.getLimit(uuid);
        long remaining = Math.max(0L, limit - played);
        double points = timeManager.getSessionPoints(uuid);
        double level = timeManager.getTotalLevel(uuid);

        boolean unlimited = timeManager.isWhitelisted(uuid) || player.hasPermission("timeperday.bypass");

        player.sendMessage(Component.text("--- Deine Spielzeit heute ---", NamedTextColor.GOLD));
        player.sendMessage(info("Gespielt", PlayerTimeManager.formatTime(played)));
        player.sendMessage(info("Session-Punkte", PlayerTimeManager.formatLevel(points)));
        player.sendMessage(info("Gesamtlevel", PlayerTimeManager.formatLevel(level)));
        if (unlimited) {
            player.sendMessage(info("Limit", "Unbegrenzt"));
            player.sendMessage(info("Verbleibend", "Unbegrenzt"));
        } else {
            player.sendMessage(info("Limit", PlayerTimeManager.formatTime(limit)));
            player.sendMessage(info("Verbleibend", PlayerTimeManager.formatTime(remaining)));
        }

        return true;
    }

    private Component info(String label, String value) {
        return Component.text(label + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.WHITE));
    }
}
