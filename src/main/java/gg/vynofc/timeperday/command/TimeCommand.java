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
            player.sendMessage(Component.text("Verwendung: /timeleft", NamedTextColor.RED));
            return true;
        }

        var snapshot = timeManager.getSnapshot(player);
        int bestKitLevel = timeManager.getBestKitLevelFor(snapshot.totalLevel());

        player.sendMessage(Component.text("--- Dein Profil heute ---", NamedTextColor.GOLD));
        player.sendMessage(info("Gespielt", PlayerTimeManager.formatTime(snapshot.played())));
        player.sendMessage(info("Session-Punkte", PlayerTimeManager.formatLevel(snapshot.sessionPoints())));
        player.sendMessage(info("Gesamtlevel", PlayerTimeManager.formatLevel(snapshot.totalLevel())));
        player.sendMessage(info("Progress-Level", PlayerTimeManager.formatLevel(timeManager.getProgressLevel(player))));
        player.sendMessage(info("Bestes Kit", bestKitLevel > 0 ? String.valueOf(bestKitLevel) : "Keins"));
        if (snapshot.unlimited()) {
            player.sendMessage(info("Limit", "Unbegrenzt"));
            player.sendMessage(info("Verbleibend", "Unbegrenzt"));
        } else {
            player.sendMessage(info("Limit", PlayerTimeManager.formatTime(snapshot.limit())));
            player.sendMessage(info("Verbleibend", PlayerTimeManager.formatTime(snapshot.remaining())));
        }

        return true;
    }

    private Component info(String label, String value) {
        return Component.text(label + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.WHITE));
    }
}
