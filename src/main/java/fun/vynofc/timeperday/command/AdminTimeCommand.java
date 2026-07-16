package fun.vynofc.timeperday.command;

import fun.vynofc.timeperday.TimePerDayPlugin;
import fun.vynofc.timeperday.gui.AdminMenuService;
import fun.vynofc.timeperday.manager.PlayerTimeManager;
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
import java.util.Locale;
import java.util.stream.Collectors;

public class AdminTimeCommand implements CommandExecutor, TabCompleter {

    private final TimePerDayPlugin plugin;
    private final PlayerTimeManager timeManager;
    private final AdminMenuService adminMenuService;

    public AdminTimeCommand(TimePerDayPlugin plugin, PlayerTimeManager timeManager,
                            AdminMenuService adminMenuService) {
        this.plugin = plugin;
        this.timeManager = timeManager;
        this.adminMenuService = adminMenuService;
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

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "set"        -> handleSet(sender, args);
            case "info"       -> handleInfo(sender, args);
            case "setlevel"   -> handleSetLevel(sender, args);
            case "addlevel"   -> handleAddLevel(sender, args);
            case "reset"      -> handleResetAll(sender, args);
            case "resetplayer"-> handleResetPlayer(sender, args);
            case "setdefault" -> handleSetDefault(sender, args);
            case "whitelist"  -> handleWhitelist(sender, args);
            case "gui"        -> handleGui(sender, args);
            case "reload"     -> handleReload(sender);
            default           -> sendHelp(sender);
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Subcommands
    // -------------------------------------------------------------------------

    /** /tpdadmin set <Spieler> <Minuten> */
    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(error("Verwendung: /tpdadmin set <Spieler> <Minuten>"));
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

    /** /tpdadmin info [Spieler] */
    private void handleInfo(CommandSender sender, String[] args) {
        OfflinePlayer target;
        if (args.length < 2) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(error("Verwendung: /tpdadmin info <Spieler>"));
                return;
            }
            target = p;
        } else {
            target = resolvePlayer(sender, args[1]);
            if (target == null) return;
        }

        var snapshot = target.getPlayer() != null
                ? timeManager.getSnapshot(target.getPlayer())
                : timeManager.getSnapshot(target.getUniqueId(), false);
        double progressLevel = timeManager.getProgressLevel(target);

        sender.sendMessage(Component.text(
                "--- Spielzeitinfo: " + safeName(target) + " ---", NamedTextColor.GOLD));
        sender.sendMessage(info("Gespielt heute", PlayerTimeManager.formatTime(snapshot.played())));
        sender.sendMessage(info("Tageslimit", snapshot.unlimited()
                ? "Unbegrenzt"
                : PlayerTimeManager.formatTime(snapshot.limit())));
        sender.sendMessage(info("Verbleibend", snapshot.unlimited()
                ? "Unbegrenzt"
                : PlayerTimeManager.formatTime(snapshot.remaining())));
        sender.sendMessage(info("Session-Punkte", PlayerTimeManager.formatLevel(snapshot.sessionPoints())));
        sender.sendMessage(info("Gesamtlevel", PlayerTimeManager.formatLevel(snapshot.totalLevel())));
        sender.sendMessage(info("Progress-Level", PlayerTimeManager.formatLevel(progressLevel)));
        sender.sendMessage(info("Whitelist (unbegrenzt)", snapshot.whitelisted() ? "Ja" : "Nein"));
    }

    /** /tpdadmin setlevel <Spieler> <Level> */
    private void handleSetLevel(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(error("Verwendung: /tpdadmin setlevel <Spieler> <Level>"));
            return;
        }
        OfflinePlayer target = resolvePlayer(sender, args[1]);
        if (target == null) return;

        try {
            double level = Double.parseDouble(args[2]);
            if (level < 0.0D) throw new NumberFormatException();
            timeManager.setTotalLevel(target.getUniqueId(), level);
            sender.sendMessage(Component.text(
                    "Gesamtlevel von " + safeName(target) + " auf "
                            + PlayerTimeManager.formatLevel(level) + " gesetzt.",
                    NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            sender.sendMessage(error("Ungültiger Level-Wert: " + args[2]));
        }
    }

    /** /tpdadmin addlevel <Spieler> <Level> */
    private void handleAddLevel(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(error("Verwendung: /tpdadmin addlevel <Spieler> <Level>"));
            return;
        }
        OfflinePlayer target = resolvePlayer(sender, args[1]);
        if (target == null) return;

        try {
            double amount = Double.parseDouble(args[2]);
            if (amount <= 0.0D) throw new NumberFormatException();
            timeManager.addTotalLevel(target.getUniqueId(), amount);
            sender.sendMessage(Component.text(
                    "Gesamtlevel von " + safeName(target) + " um "
                            + PlayerTimeManager.formatLevel(amount) + " erhöht.",
                    NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            sender.sendMessage(error("Ungültiger Level-Wert: " + args[2]));
        }
    }

    /** /tpdadmin reset */
    private void handleResetAll(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(error("Verwendung: /tpdadmin reset"));
            sender.sendMessage(Component.text(
                    "Für einzelnen Spieler: /tpdadmin resetplayer <Spieler>",
                    NamedTextColor.YELLOW));
            return;
        }

        timeManager.resetEverything();
        Bukkit.broadcast(Component.text(
                "Der Serverfortschritt wurde global zurückgesetzt (Zeit, Level, Session, Spielerzustände).",
                NamedTextColor.RED));
    }

    /** /tpdadmin resetplayer <Spieler> */
    private void handleResetPlayer(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(error("Verwendung: /tpdadmin resetplayer <Spieler>"));
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

    /** /tpdadmin setdefault <Minuten> */
    private void handleSetDefault(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(error("Verwendung: /tpdadmin setdefault <Minuten>"));
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

    /** /tpdadmin whitelist <add|remove> <Spieler> */
    private void handleWhitelist(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(error("Verwendung: /tpdadmin whitelist <add|remove> <Spieler>"));
            return;
        }
        OfflinePlayer target = resolvePlayer(sender, args[2]);
        if (target == null) return;

        switch (args[1].toLowerCase(Locale.ROOT)) {
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
                    "Verwendung: /tpdadmin whitelist <add|remove> <Spieler>"));
        }
    }

    /** /tpdadmin reload */
    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        timeManager.reload();
        sender.sendMessage(Component.text("Konfiguration neu geladen.", NamedTextColor.GREEN));
    }

    /** /tpdadmin gui */
    private void handleGui(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(error("Die Admin-GUI kann nur von Spielern geöffnet werden."));
            return;
        }
        if (args.length > 1) {
            sender.sendMessage(error("Verwendung: /tpdadmin gui"));
            return;
        }
        adminMenuService.openMainMenu(player);
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
            return filter(Arrays.asList("set", "info", "setlevel", "addlevel", "reset", "resetplayer",
                    "setdefault", "whitelist", "gui", "reload"),
                    args[0]);
        }

        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "set", "info", "setlevel", "addlevel", "resetplayer" -> knownPlayerNames(args[1]);
                case "whitelist"            -> filter(List.of("add", "remove"), args[1]);
                default                     -> List.of();
            };
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("whitelist")) {
            return knownPlayerNames(args[2]);
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

    private List<String> knownPlayerNames(String prefix) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        return Arrays.stream(Bukkit.getOfflinePlayers())
                .map(OfflinePlayer::getName)
                .filter(name -> name != null && !name.isBlank())
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lowerPrefix))
                .distinct()
                .collect(Collectors.toList());
    }

    private List<String> filter(List<String> list, String prefix) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        return list.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lowerPrefix))
                .collect(Collectors.toList());
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== TimePerDay Befehle ===", NamedTextColor.GOLD));
        helpLine(sender, "/tpdadmin set <Spieler> <Minuten>",        "Individuelles Tageslimit setzen");
        helpLine(sender, "/tpdadmin info [Spieler]",                 "Spielzeitinfo anzeigen");
        helpLine(sender, "/tpdadmin setlevel <Spieler> <Level>",     "Gesamtlevel setzen");
        helpLine(sender, "/tpdadmin addlevel <Spieler> <Level>",     "Gesamtlevel erhöhen");
        helpLine(sender, "/tpdadmin reset",                          "GLOBAL: Alles zurücksetzen");
        helpLine(sender, "/tpdadmin resetplayer <Spieler>",          "Einzelnen Spieler zurücksetzen");
        helpLine(sender, "/tpdadmin setdefault <Minuten>",           "Standard-Tageslimit setzen");
        helpLine(sender, "/tpdadmin whitelist <add|remove> <Spieler>", "Whitelist verwalten (unbegrenzt)");
        helpLine(sender, "/tpdadmin gui",                            "Ingame-Adminoberfläche öffnen");
        helpLine(sender, "/tpdadmin reload",                         "Konfiguration neu laden");
    }

    private void helpLine(CommandSender sender, String cmd, String desc) {
        sender.sendMessage(Component.text(cmd, NamedTextColor.YELLOW)
                .append(Component.text(" – " + desc, NamedTextColor.GRAY)));
    }
}


