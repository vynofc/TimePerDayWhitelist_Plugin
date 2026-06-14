package de.niliees.timeperday.command;

import de.niliees.timeperday.TimePerDayPlugin;
import de.niliees.timeperday.manager.PlayerTimeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TimeCommand implements CommandExecutor, TabCompleter {

    private final TimePerDayPlugin plugin;
    private final PlayerTimeManager timeManager;

    public TimeCommand(TimePerDayPlugin plugin, PlayerTimeManager timeManager) {
        this.plugin = plugin;
        this.timeManager = timeManager;
    }

    // -------------------------------------------------------------------------
    // Befehlsverarbeitung
    // -------------------------------------------------------------------------

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("timeperday.admin")) {
            sender.sendMessage(Component.text(
                    "Du hast keine Berechtigung für diesen Befehl.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "set"        -> handleSet(sender, args);
            case "info"       -> handleInfo(sender, args);
            case "reset"      -> handleReset(sender, args);
            case "setdefault" -> handleSetDefault(sender, args);
            case "whitelist"  -> handleWhitelist(sender, args);
            case "reload"     -> handleReload(sender);
            default           -> sendHelp(sender);
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Subcommands
    // -------------------------------------------------------------------------

    /** /timeperday set <Spieler> <Minuten> */
    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(error("Verwendung: /timeperday set <Spieler> <Minuten>"));
            return;
        }
        OfflinePlayer target = resolvePlayer(sender, args[1]);
        if (target == null) return;

        try {
            long minutes = Long.parseLong(args[2]);
            if (minutes < 1) throw new NumberFormatException();
            timeManager.setLimit(target.getUniqueId(), minutes * 60L);
            sender.sendMessage(Component.text()
                    .append(Component.text("Spielzeitlimit für ", NamedTextColor.GREEN))
                    .append(Component.text(safeName(target), NamedTextColor.YELLOW))
                    .append(Component.text(" auf ", NamedTextColor.GREEN))
                    .append(Component.text(minutes + " Minuten/Tag", NamedTextColor.YELLOW))
                    .append(Component.text(" gesetzt.", NamedTextColor.GREEN))
                    .build());
        } catch (NumberFormatException e) {
            sender.sendMessage(error("Ungültige Minuten-Angabe: " + args[2]));
        }
    }

    /** /timeperday info [Spieler] */
    private void handleInfo(CommandSender sender, String[] args) {
        OfflinePlayer target;
        if (args.length < 2) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(error("Verwendung: /timeperday info <Spieler>"));
                return;
            }
            target = p;
        } else {
            target = resolvePlayer(sender, args[1]);
            if (target == null) return;
        }

        var uuid = target.getUniqueId();
        long played    = timeManager.getPlayedToday(uuid);
        long limit     = timeManager.getLimit(uuid);
        long remaining = Math.max(0L, limit - played);
        boolean wl     = timeManager.isWhitelisted(uuid);

        sender.sendMessage(Component.text(
                "--- Spielzeitinfo: " + safeName(target) + " ---", NamedTextColor.GOLD));
        sender.sendMessage(info("Gespielt heute", PlayerTimeManager.formatTime(played)));
        sender.sendMessage(info("Tageslimit", PlayerTimeManager.formatTime(limit)));
        sender.sendMessage(info("Verbleibend", PlayerTimeManager.formatTime(remaining)));
        sender.sendMessage(info("Whitelist (unbegrenzt)", wl ? "Ja" : "Nein"));
    }

    /** /timeperday reset <Spieler> */
    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(error("Verwendung: /timeperday reset <Spieler>"));
            return;
        }
        OfflinePlayer target = resolvePlayer(sender, args[1]);
        if (target == null) return;

        timeManager.resetPlayerTime(target.getUniqueId());
        sender.sendMessage(Component.text()
                .append(Component.text("Spielzeit für ", NamedTextColor.GREEN))
                .append(Component.text(safeName(target), NamedTextColor.YELLOW))
                .append(Component.text(" zurückgesetzt.", NamedTextColor.GREEN))
                .build());
    }

    /** /timeperday setdefault <Minuten> */
    private void handleSetDefault(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(error("Verwendung: /timeperday setdefault <Minuten>"));
            return;
        }
        try {
            long minutes = Long.parseLong(args[1]);
            if (minutes < 1) throw new NumberFormatException();
            timeManager.setDefaultLimitSeconds(minutes * 60L);
            sender.sendMessage(Component.text(
                    "Standard-Spielzeitlimit auf " + minutes + " Minuten/Tag gesetzt.",
                    NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            sender.sendMessage(error("Ungültige Minuten-Angabe: " + args[1]));
        }
    }

    /** /timeperday whitelist <add|remove> <Spieler> */
    private void handleWhitelist(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(error("Verwendung: /timeperday whitelist <add|remove> <Spieler>"));
            return;
        }
        OfflinePlayer target = resolvePlayer(sender, args[2]);
        if (target == null) return;

        switch (args[1].toLowerCase()) {
            case "add" -> {
                timeManager.setWhitelisted(target.getUniqueId(), true);
                sender.sendMessage(Component.text(
                        safeName(target) + " zur Whitelist hinzugefügt (unbegrenzte Spielzeit).",
                        NamedTextColor.GREEN));
            }
            case "remove" -> {
                timeManager.setWhitelisted(target.getUniqueId(), false);
                sender.sendMessage(Component.text(
                        safeName(target) + " von der Whitelist entfernt.",
                        NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(error(
                    "Verwendung: /timeperday whitelist <add|remove> <Spieler>"));
        }
    }

    /** /timeperday reload */
    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        timeManager.reload();
        sender.sendMessage(Component.text("Konfiguration neu geladen.", NamedTextColor.GREEN));
    }

    // -------------------------------------------------------------------------
    // Tab-Vervollständigung
    // -------------------------------------------------------------------------

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String label,
                                                @NotNull String[] args) {
        if (!sender.hasPermission("timeperday.admin")) return List.of();

        if (args.length == 1) {
            return filter(Arrays.asList("set", "info", "reset", "setdefault", "whitelist", "reload"),
                    args[0]);
        }

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "set", "info", "reset" -> onlinePlayerNames(args[1]);
                case "whitelist"            -> filter(List.of("add", "remove"), args[1]);
                default                     -> List.of();
            };
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("whitelist")) {
            return onlinePlayerNames(args[2]);
        }

        return List.of();
    }

    // -------------------------------------------------------------------------
    // Interne Helfer
    // -------------------------------------------------------------------------

    /**
     * Löst einen Spielernamen zu einem OfflinePlayer auf.
     * Schlägt fehl und sendet eine Fehlermeldung, wenn der Spieler unbekannt ist.
     */
    @SuppressWarnings("deprecation")
    private @Nullable OfflinePlayer resolvePlayer(CommandSender sender, String name) {
        // Zuerst online suchen (schnell & sicher)
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;

        // Dann offline – getOfflinePlayer(String) ist deprecated wegen möglichem
        // Netzwerkaufruf, für Admin-Befehle aber akzeptabel
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (!offline.hasPlayedBefore()) {
            sender.sendMessage(error("Spieler '" + name + "' nicht gefunden."));
            return null;
        }
        return offline;
    }

    private String safeName(OfflinePlayer p) {
        String n = p.getName();
        return n != null ? n : p.getUniqueId().toString();
    }

    private Component error(String msg) {
        return Component.text(msg, NamedTextColor.RED);
    }

    private Component info(String label, String value) {
        return Component.text(label + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.WHITE));
    }

    private List<String> onlinePlayerNames(String prefix) {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }

    private List<String> filter(List<String> list, String prefix) {
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== TimePerDay Befehle ===", NamedTextColor.GOLD));
        helpLine(sender, "/timeperday set <Spieler> <Minuten>",        "Individuelles Tageslimit setzen");
        helpLine(sender, "/timeperday info [Spieler]",                 "Spielzeitinfo anzeigen");
        helpLine(sender, "/timeperday reset <Spieler>",                "Heutige Spielzeit zurücksetzen");
        helpLine(sender, "/timeperday setdefault <Minuten>",           "Standard-Tageslimit setzen");
        helpLine(sender, "/timeperday whitelist <add|remove> <Spieler>", "Whitelist verwalten (unbegrenzt)");
        helpLine(sender, "/timeperday reload",                         "Konfiguration neu laden");
    }

    private void helpLine(CommandSender sender, String cmd, String desc) {
        sender.sendMessage(Component.text(cmd, NamedTextColor.YELLOW)
                .append(Component.text(" – " + desc, NamedTextColor.GRAY)));
    }
}
