package fun.vynofc.timeperday.border;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class BorderCommand {

    private final BorderManager borderManager;

    public BorderCommand(BorderManager borderManager) {
        this.borderManager = borderManager;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendHelp(sender);
            return true;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> handleAdd(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "list" -> handleList(sender);
            case "bypass" -> handleBypass(sender, args);
            case "shape" -> handleShape(sender, args);
            case "wrap" -> handleWrap(sender, args);
            case "setcenter" -> handleSetCenter(sender, args);
            case "setradius" -> handleSetRadius(sender, args);
            default -> sendHelp(sender);
        }

        return true;
    }

    public List<String> tabComplete(String[] args) {
        if (args.length == 1) {
            return List.of("add", "remove", "list", "bypass", "shape", "wrap", "setcenter", "setradius");
        }
        if (args.length == 2) {
            String sub = args[1].toLowerCase(Locale.ROOT);
            if ("remove".equals(sub) || "bypass".equals(sub) || "shape".equals(sub)
                    || "wrap".equals(sub) || "setcenter".equals(sub) || "setradius".equals(sub)) {
                return borderManager.getAllBorders().stream().map(BorderData::getWorld).toList();
            }
        }
        if (args.length == 3) {
            if ("bypass".equalsIgnoreCase(args[1])) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            }
            if ("shape".equalsIgnoreCase(args[1])) {
                return List.of("square", "rectangle", "circle", "ellipse");
            }
            if ("wrap".equalsIgnoreCase(args[1])) {
                return List.of("none", "default", "both", "radial", "x", "z", "earth");
            }
        }
        return List.of();
    }

    private void handleAdd(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(error("Verwendung: /tpdadmin border add <Welt> <RadiusX> <RadiusZ> [CenterX] [CenterZ] [Shape] [Wrap]"));
            return;
        }

        String worldName = args[2];
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage(error("Welt '" + worldName + "' nicht gefunden."));
            return;
        }

        double radiusX, radiusZ, centerX = 0, centerZ = 0;
        String shape = "square", wrap = "none";
        try {
            radiusX = Double.parseDouble(args[3]);
            radiusZ = Double.parseDouble(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage(error("Ungueltige Zahl(en) fuer Radius."));
            return;
        }

        int idx = 5;
        if (args.length > idx) {
            try {
                centerX = Double.parseDouble(args[idx]);
                centerZ = Double.parseDouble(args[idx + 1]);
                idx += 2;
            } catch (Exception e) {
                sender.sendMessage(error("Ungueltige Koordinaten. Verwendung: ... [CenterX] [CenterZ]"));
                return;
            }
        }
        if (args.length > idx) {
            shape = args[idx].toLowerCase(Locale.ROOT);
            idx++;
        }
        if (args.length > idx) {
            wrap = args[idx].toLowerCase(Locale.ROOT);
        }

        borderManager.addBorder(worldName, centerX, centerZ, radiusX, radiusZ, shape, wrap);
        sender.sendMessage(Component.text()
                .append(Component.text("Border fuer '", NamedTextColor.GREEN))
                .append(Component.text(worldName, NamedTextColor.YELLOW))
                .append(Component.text("' hinzugefuegt (", NamedTextColor.GREEN))
                .append(Component.text(shape, NamedTextColor.YELLOW))
                .append(Component.text(", Radius=", NamedTextColor.GREEN))
                .append(Component.text(radiusX + "/" + radiusZ, NamedTextColor.YELLOW))
                .append(Component.text(", Wrap=", NamedTextColor.GREEN))
                .append(Component.text(wrap, NamedTextColor.YELLOW))
                .append(Component.text(").", NamedTextColor.GREEN))
                .build());
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(error("Verwendung: /tpdadmin border remove <Welt>"));
            return;
        }

        String worldName = args[2];
        if (borderManager.getBorder(worldName) == null) {
            sender.sendMessage(error("Kein Border fuer Welt '" + worldName + "' gefunden."));
            return;
        }

        borderManager.removeBorder(worldName);
        sender.sendMessage(Component.text()
                .append(Component.text("Border fuer '", NamedTextColor.GREEN))
                .append(Component.text(worldName, NamedTextColor.YELLOW))
                .append(Component.text("' entfernt.", NamedTextColor.GREEN))
                .build());
    }

    private void handleList(CommandSender sender) {
        Collection<BorderData> allBorders = borderManager.getAllBorders();
        if (allBorders.isEmpty()) {
            sender.sendMessage(Component.text("Keine Borders konfiguriert.", NamedTextColor.GRAY));
            return;
        }

        sender.sendMessage(Component.text("--- Borders ---", NamedTextColor.GOLD));
        for (BorderData data : allBorders) {
            sender.sendMessage(Component.text()
                    .append(Component.text(data.getWorld(), NamedTextColor.YELLOW))
                    .append(Component.text(" | Shape: ", NamedTextColor.GRAY))
                    .append(Component.text(data.getShape(), NamedTextColor.WHITE))
                    .append(Component.text(" | Radius: ", NamedTextColor.GRAY))
                    .append(Component.text(data.getRadiusX() + "/" + data.getRadiusZ(), NamedTextColor.WHITE))
                    .append(Component.text(" | Center: ", NamedTextColor.GRAY))
                    .append(Component.text(data.getCenterX() + "/" + data.getCenterZ(), NamedTextColor.WHITE))
                    .append(Component.text(" | Wrap: ", NamedTextColor.GRAY))
                    .append(Component.text(data.getWrap(), NamedTextColor.WHITE))
                    .append(Component.text(" | Bypass: ", NamedTextColor.GRAY))
                    .append(Component.text(String.valueOf(data.getBypassPlayers().size()), NamedTextColor.WHITE))
                    .build());
        }
    }

    private void handleBypass(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(error("Verwendung: /tpdadmin border bypass <Welt> <Spieler>"));
            return;
        }

        String worldName = args[2];
        BorderData data = borderManager.getBorder(worldName);
        if (data == null) {
            sender.sendMessage(error("Kein Border fuer Welt '" + worldName + "' gefunden."));
            return;
        }

        OfflinePlayer target = resolvePlayer(args[3]);
        if (target == null) {
            sender.sendMessage(error("Spieler '" + args[3] + "' nicht gefunden."));
            return;
        }

        boolean newState = !data.isBypassing(target.getUniqueId());
        borderManager.setBypass(worldName, target.getUniqueId(), newState);

        String playerName = target.getName() != null ? target.getName() : target.getUniqueId().toString();
        sender.sendMessage(Component.text()
                .append(Component.text("Border-Bypass fuer ", NamedTextColor.GREEN))
                .append(Component.text(playerName, NamedTextColor.YELLOW))
                .append(Component.text(" in Welt '", NamedTextColor.GREEN))
                .append(Component.text(worldName, NamedTextColor.YELLOW))
                .append(Component.text("': ", NamedTextColor.GREEN))
                .append(Component.text(newState ? "aktiviert" : "deaktiviert", NamedTextColor.YELLOW))
                .build());
    }

    private void handleShape(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(error("Verwendung: /tpdadmin border shape <Welt> <square|rectangle|circle|ellipse>"));
            return;
        }

        String worldName = args[2];
        String shape = args[3].toLowerCase(Locale.ROOT);
        if (borderManager.getBorder(worldName) == null) {
            sender.sendMessage(error("Kein Border fuer Welt '" + worldName + "' gefunden."));
            return;
        }

        borderManager.setBorderShape(worldName, shape);
        sender.sendMessage(Component.text()
                .append(Component.text("Border-Shape fuer '", NamedTextColor.GREEN))
                .append(Component.text(worldName, NamedTextColor.YELLOW))
                .append(Component.text("' auf ", NamedTextColor.GREEN))
                .append(Component.text(shape, NamedTextColor.YELLOW))
                .append(Component.text(" gesetzt.", NamedTextColor.GREEN))
                .build());
    }

    private void handleWrap(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(error("Verwendung: /tpdadmin border wrap <Welt> <none|default|both|radial|x|z|earth>"));
            return;
        }

        String worldName = args[2];
        String wrap = args[3].toLowerCase(Locale.ROOT);
        if (borderManager.getBorder(worldName) == null) {
            sender.sendMessage(error("Kein Border fuer Welt '" + worldName + "' gefunden."));
            return;
        }

        borderManager.setBorderWrap(worldName, wrap);
        sender.sendMessage(Component.text()
                .append(Component.text("Border-Wrap fuer '", NamedTextColor.GREEN))
                .append(Component.text(worldName, NamedTextColor.YELLOW))
                .append(Component.text("' auf ", NamedTextColor.GREEN))
                .append(Component.text(wrap, NamedTextColor.YELLOW))
                .append(Component.text(" gesetzt.", NamedTextColor.GREEN))
                .build());
    }

    private void handleSetCenter(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(error("Verwendung: /tpdadmin border setcenter <Welt> <CenterX> <CenterZ>"));
            return;
        }

        String worldName = args[2];
        if (borderManager.getBorder(worldName) == null) {
            sender.sendMessage(error("Kein Border fuer Welt '" + worldName + "' gefunden."));
            return;
        }

        double centerX, centerZ;
        try {
            centerX = Double.parseDouble(args[3]);
            centerZ = Double.parseDouble(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage(error("Ungueltige Koordinaten."));
            return;
        }

        borderManager.setBorderCenter(worldName, centerX, centerZ);
        sender.sendMessage(Component.text()
                .append(Component.text("Border-Center fuer '", NamedTextColor.GREEN))
                .append(Component.text(worldName, NamedTextColor.YELLOW))
                .append(Component.text("' auf ", NamedTextColor.GREEN))
                .append(Component.text(centerX + "/" + centerZ, NamedTextColor.YELLOW))
                .append(Component.text(" gesetzt.", NamedTextColor.GREEN))
                .build());
    }

    private void handleSetRadius(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(error("Verwendung: /tpdadmin border setradius <Welt> <RadiusX> <RadiusZ>"));
            return;
        }

        String worldName = args[2];
        if (borderManager.getBorder(worldName) == null) {
            sender.sendMessage(error("Kein Border fuer Welt '" + worldName + "' gefunden."));
            return;
        }

        double radiusX, radiusZ;
        try {
            radiusX = Double.parseDouble(args[3]);
            radiusZ = Double.parseDouble(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage(error("Ungueltige Radien."));
            return;
        }

        borderManager.setBorderRadius(worldName, radiusX, radiusZ);
        sender.sendMessage(Component.text()
                .append(Component.text("Border-Radius fuer '", NamedTextColor.GREEN))
                .append(Component.text(worldName, NamedTextColor.YELLOW))
                .append(Component.text("' auf ", NamedTextColor.GREEN))
                .append(Component.text(radiusX + "/" + radiusZ, NamedTextColor.YELLOW))
                .append(Component.text(" gesetzt.", NamedTextColor.GREEN))
                .build());
    }

    private OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (name.equalsIgnoreCase(offline.getName())) {
                return offline;
            }
        }
        try {
            return Bukkit.getOfflinePlayer(UUID.fromString(name));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("--- /tpdadmin border ---", NamedTextColor.GOLD));
        helpLine(sender, "add <Welt> <RadiusX> <RadiusZ> [CenterX] [CenterZ] [Shape] [Wrap]", "Border hinzufuegen");
        helpLine(sender, "remove <Welt>", "Border entfernen");
        helpLine(sender, "list", "Alle Borders anzeigen");
        helpLine(sender, "bypass <Welt> <Spieler>", "Border-Bypass togglen");
        helpLine(sender, "shape <Welt> <square|rectangle|circle|ellipse>", "Border-Form aendern");
        helpLine(sender, "wrap <Welt> <none|default|both|radial|x|z|earth>", "Border-Wrap aendern");
        helpLine(sender, "setcenter <Welt> <CenterX> <CenterZ>", "Border-Center aendern");
        helpLine(sender, "setradius <Welt> <RadiusX> <RadiusZ>", "Border-Radius aendern");
    }

    private void helpLine(CommandSender sender, String cmd, String desc) {
        sender.sendMessage(Component.text(cmd, NamedTextColor.YELLOW)
                .append(Component.text(" - " + desc, NamedTextColor.GRAY)));
    }

    private Component error(String msg) {
        return Component.text(msg, NamedTextColor.RED);
    }
}