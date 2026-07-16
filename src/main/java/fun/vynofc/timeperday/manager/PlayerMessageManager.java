package fun.vynofc.timeperday.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class PlayerMessageManager {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final PlayerTimeManager manager;

    PlayerMessageManager(PlayerTimeManager manager) {
        this.manager = manager;
    }

    void sendWarning(Player player, long remainingSeconds) {
        player.sendMessage(buildConfiguredMessage(
                manager.plugin.getConfig().getString("messages.warning",
                "<yellow>[!] {remaining} verbleibende Spielzeit heute!"),
                Map.of("{remaining}", PlayerTimeManager.formatTime(remainingSeconds))));
    }

    void kickPlayer(Player player, double gainedLevel, double totalLevelValue) {
        player.kick(buildKickComponent(player.getUniqueId(), gainedLevel, totalLevelValue));
    }

    Component buildJoinKickComponent(UUID uuid) {
        return buildKickComponent(uuid, 0.0D, manager.getTotalLevel(uuid));
    }

    Component buildJoinInfoComponent(Player player, boolean kitGiven) {
        PlayerTimeSnapshot snapshot = manager.getSnapshot(player);
        int kitLevel = manager.getBestKitLevelFor(snapshot.totalLevel());
        Map<String, String> placeholders = buildProfilePlaceholders(
                player.getUniqueId(),
                player.getName(),
                snapshot,
                0.0D,
                snapshot.sessionPoints(),
                snapshot.totalLevel(),
                kitLevel,
                kitGiven
        );

        return buildMultilineMessage(List.of(
                manager.plugin.getConfig().getString(
                        "messages.join-title",
                        "<gradient:#7bed9f:#70a1ff><bold>Willkommen zurueck, {player}!</bold></gradient>"),
                manager.plugin.getConfig().getString(
                        "messages.join-info",
                        "<gray>Verbleibend: <yellow>{remaining}</yellow> <dark_gray>| <gray>Gesamtlevel: "
                                + "<yellow>{total-level}</yellow> <dark_gray>| <gray>Bestes Kit: <yellow>{kit-level}</yellow>"),
                manager.plugin.getConfig().getString(
                        "messages.join-line2",
                        "<gray>Session heute: <yellow>{session-points}</yellow> <dark_gray>| <gray>Kit heute erhalten: "
                                + "<yellow>{kit-given}</yellow>"),
                manager.plugin.getConfig().getString(
                        "messages.join-tip",
                    "<gray>Tipp: Nutze jederzeit <yellow>/tpd time</yellow><gray> fuer deine komplette Profiluebersicht.")
        ), placeholders);
    }

    private Component buildKickComponent(UUID uuid, double gainedLevel, double totalLevelValue) {
        PlayerTimeSnapshot snapshot = manager.getSnapshot(uuid, false);
        int kitLevel = manager.getBestKitLevelFor(totalLevelValue);
        Map<String, String> placeholders = buildProfilePlaceholders(
                uuid,
                resolvePlayerName(uuid),
                snapshot,
                gainedLevel,
                gainedLevel,
                totalLevelValue,
                kitLevel,
                false
        );

        return buildMultilineMessage(List.of(
                manager.plugin.getConfig().getString(
                        "messages.kick-title",
                        "<gradient:#ff6b6b:#ffd166><bold>Spielzeit fuer heute verbraucht</bold></gradient>"),
                manager.plugin.getConfig().getString(
                        "messages.kick-line1",
                        "<gray>Profil von <yellow>{player}</yellow><gray> - gespielt: <yellow>{played}</yellow>"
                                + " <dark_gray>| <gray>Limit: <yellow>{limit}</yellow>"),
                manager.plugin.getConfig().getString(
                        "messages.kick-line2",
                        "<gray>Heute verdient: <yellow>+{gained-level}</yellow> <dark_gray>| <gray>Session: "
                                + "<yellow>{session-points}</yellow>"),
                manager.plugin.getConfig().getString(
                        "messages.kick-line3",
                        "<gray>Gesamtlevel: <yellow>{total-level}</yellow> <dark_gray>| <gray>Bestes Kit: "
                                + "<yellow>{kit-level}</yellow>"),
                manager.plugin.getConfig().getString(
                        "messages.kick-line4",
                    "<gray>Du kannst morgen wieder spielen. Nutze dann <yellow>/tpd time</yellow><gray> fuer dein Profil.")
        ), placeholders);
    }

    private String resolvePlayerName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }

        String offlineName = Bukkit.getOfflinePlayer(uuid).getName();
        return offlineName != null ? offlineName : uuid.toString();
    }

    private Map<String, String> buildProfilePlaceholders(UUID uuid, String playerName, PlayerTimeSnapshot snapshot,
                                                         double gainedLevel, double sessionPointsValue,
                                                         double totalLevelValue, int kitLevel, boolean kitGiven) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{player}", playerName);
        placeholders.put("{played}", PlayerTimeManager.formatTime(snapshot.played()));
        placeholders.put("{limit}", snapshot.unlimited() ? "Unbegrenzt" : PlayerTimeManager.formatTime(snapshot.limit()));
        placeholders.put("{remaining}", snapshot.unlimited() ? "Unbegrenzt" : PlayerTimeManager.formatTime(snapshot.remaining()));
        placeholders.put("{gained-level}", PlayerTimeManager.formatLevel(gainedLevel));
        placeholders.put("{session-points}", PlayerTimeManager.formatLevel(sessionPointsValue));
        placeholders.put("{total-level}", PlayerTimeManager.formatLevel(totalLevelValue));
        placeholders.put("{level}", PlayerTimeManager.formatLevel(totalLevelValue));
        placeholders.put("{progress-level}", PlayerTimeManager.formatLevel(Math.max(totalLevelValue,
                snapshot.totalLevel() + snapshot.sessionPoints())));
        placeholders.put("{kit-level}", kitLevel > 0 ? String.valueOf(kitLevel) : "Keins");
        placeholders.put("{kit-given}", kitGiven ? "Ja" : "Nein");
        placeholders.put("{whitelisted}", snapshot.whitelisted() ? "Ja" : "Nein");
        placeholders.put("{bypass}", snapshot.bypassPermission() ? "Ja" : "Nein");
        placeholders.put("{status}", snapshot.unlimited() ? "Unbegrenzt" : "Begrenzt");
        placeholders.put("{uuid}", uuid.toString());
        return placeholders;
    }

    private Component buildConfiguredMessage(String template, Map<String, String> placeholders) {
        return MINI_MESSAGE.deserialize(applyPlaceholders(template, placeholders));
    }

    private Component buildMultilineMessage(List<String> templates, Map<String, String> placeholders) {
        var builder = Component.text();
        boolean firstLine = true;
        for (String template : templates) {
            if (template == null || template.isBlank()) {
                continue;
            }
            if (!firstLine) {
                builder.appendNewline();
            }
            builder.append(buildConfiguredMessage(template, placeholders));
            firstLine = false;
        }
        return builder.build();
    }

    private String applyPlaceholders(String text, Map<String, String> placeholders) {
        String out = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            out = out.replace(entry.getKey(), entry.getValue());
        }
        return out;
    }
}

