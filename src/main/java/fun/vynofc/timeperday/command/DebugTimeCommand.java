package fun.vynofc.timeperday.command;

import fun.vynofc.timeperday.manager.PlayerTimeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class DebugTimeCommand implements CommandExecutor, TabCompleter {

    private final PlayerTimeManager timeManager;

    public DebugTimeCommand(PlayerTimeManager timeManager) {
        this.timeManager = timeManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof ConsoleCommandSender) && !sender.hasPermission("timeperday.debug")) {
            sender.sendMessage(error("Du hast keine Berechtigung für diesen Befehl."));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "dayover" -> handleDayOver(sender, args);
            case "warn" -> handleWarn(sender, args);
            case "timeout" -> handleTimeout(sender, args);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleDayOver(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(error("Verwendung: /tpddebug dayover"));
            return;
        }

        timeManager.debugTriggerDayOver();
        sender.sendMessage(Component.text("Debug-Event ausgelöst: Tag vorbei (Spielzeiten/Session reset).",
                NamedTextColor.GREEN));
    }

    private void handleWarn(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(error("Verwendung: /tpddebug warn <Spieler> <Sekunden>"));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(error("Spieler '" + args[1] + "' ist nicht online."));
            return;
        }

        long seconds;
        try {
            seconds = Long.parseLong(args[2]);
            if (seconds < 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(error("Ungültige Sekunden-Angabe: " + args[2]));
            return;
        }

        target.getScheduler().run(timeManager.getPlugin(), task ->
                timeManager.debugTriggerWarning(target, seconds), null);

        sender.sendMessage(Component.text("Warnung an " + target.getName() + " gesendet ("
                + seconds + "s verbleibend).", NamedTextColor.GREEN));
    }

    private void handleTimeout(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(error("Verwendung: /tpddebug timeout <Spieler>"));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(error("Spieler '" + args[1] + "' ist nicht online."));
            return;
        }

        boolean bypassed = timeManager.isWhitelisted(target.getUniqueId())
            || target.hasPermission("timeperday.bypass");

        if (bypassed) {
            sender.sendMessage(Component.text("Timeout nicht ausgelöst: Spieler ist whitelisted oder hat bypass.",
                NamedTextColor.YELLOW));
            return;
        }

        target.getScheduler().run(timeManager.getPlugin(), task ->
            timeManager.debugTriggerTimeout(target), null);

        sender.sendMessage(Component.text("Debug-Timeout für " + target.getName() + " ausgelöst.", NamedTextColor.GREEN));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                 @NotNull Command command,
                                                 @NotNull String label,
                                                 @NotNull String[] args) {
        if (!sender.hasPermission("timeperday.debug")) {
            return List.of();
        }

        if (args.length == 1) {
            return filter(List.of("dayover", "warn", "timeout"), args[0]);
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("warn") || args[0].equalsIgnoreCase("timeout"))) {
            return onlinePlayerNames(args[1]);
        }

        return List.of();
    }

    private List<String> onlinePlayerNames(String prefix) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lowerPrefix))
                .collect(Collectors.toList());
    }

    private List<String> filter(List<String> values, String prefix) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lowerPrefix))
                .collect(Collectors.toList());
    }

    private Component error(String msg) {
        return Component.text(msg, NamedTextColor.RED);
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== DebugTime Befehle ===", NamedTextColor.GOLD));
        helpLine(sender, "/tpddebug dayover", "Simuliert Mitternacht (Tageswerte reset)");
        helpLine(sender, "/tpddebug warn <Spieler> <Sekunden>", "Sendet manuelle Warnung");
        helpLine(sender, "/tpddebug timeout <Spieler>", "Simuliert Zeitablauf + Kick");
    }

    private void helpLine(CommandSender sender, String cmd, String desc) {
        sender.sendMessage(Component.text(cmd, NamedTextColor.YELLOW)
                .append(Component.text(" - " + desc, NamedTextColor.GRAY)));
    }
}

