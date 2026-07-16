package gg.vynofc.timeperday.gui;

import gg.vynofc.timeperday.TimePerDayPlugin;
import gg.vynofc.timeperday.manager.PlayerTimeSnapshot;
import gg.vynofc.timeperday.manager.PlayerTimeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class AdminMenuService {

    private static final int PLAYER_LIST_PAGE_SIZE = 45;

    private final TimePerDayPlugin plugin;
    private final PlayerTimeManager timeManager;

    public AdminMenuService(TimePerDayPlugin plugin, PlayerTimeManager timeManager) {
        this.plugin = plugin;
        this.timeManager = timeManager;
    }

    public void openMainMenu(Player admin) {
        AdminMenuHolder holder = new AdminMenuHolder(MenuType.MAIN, null, 0);
        Inventory inventory = createInventory(holder, 27, "TimePerDay Admin");

        inventory.setItem(11, namedItem(Material.CHEST, "Spielerverwaltung",
                "Spieler auswaehlen und Zeit, Level oder Whitelist verwalten."));
        inventory.setItem(13, namedItem(Material.KNOWLEDGE_BOOK, "Plugin neu laden",
                "Konfiguration und Datenbestand des Plugins neu laden."));
        inventory.setItem(15, namedItem(Material.TNT, "Globalen Reset vorbereiten",
                "Oeffnet eine Bestaetigung fuer den kompletten Reset."));
        inventory.setItem(22, namedItem(Material.BARRIER, "Schliessen",
                "Schliesst die Admin-Oberflaeche."));

        admin.openInventory(inventory);
    }

    public void openPlayerSelection(Player admin, int page) {
        List<OfflinePlayer> players = getSelectablePlayers();
        int maxPage = players.isEmpty() ? 0 : (players.size() - 1) / PLAYER_LIST_PAGE_SIZE;
        int safePage = Math.max(0, Math.min(page, maxPage));

        AdminMenuHolder holder = new AdminMenuHolder(MenuType.PLAYER_LIST, null, safePage);
        Inventory inventory = createInventory(holder, 54, "Spielerverwaltung");

        int start = safePage * PLAYER_LIST_PAGE_SIZE;
        for (int slot = 0; slot < PLAYER_LIST_PAGE_SIZE && start + slot < players.size(); slot++) {
            OfflinePlayer target = players.get(start + slot);
            inventory.setItem(slot, playerHead(target,
                    displayName(target),
                    target.isOnline() ? "Status: Online" : "Status: Offline",
                    "Klicke, um Verwaltungsaktionen zu oeffnen."));
        }

        inventory.setItem(45, namedItem(Material.ARROW, "Vorherige Seite",
                safePage > 0 ? "Zur vorherigen Spielerseite wechseln." : "Keine vorherige Seite verfuegbar."));
        inventory.setItem(49, namedItem(Material.COMPASS, "Zurueck zum Hauptmenue",
                "Zur Hauptuebersicht der Admin-Oberflaeche wechseln."));
        inventory.setItem(53, namedItem(Material.ARROW, "Naechste Seite",
                safePage < maxPage ? "Zur naechsten Spielerseite wechseln." : "Keine weitere Seite verfuegbar."));

        admin.openInventory(inventory);
    }

    public void openPlayerActions(Player admin, UUID targetUuid, int sourcePage) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        AdminMenuHolder holder = new AdminMenuHolder(MenuType.PLAYER_ACTIONS, targetUuid, sourcePage);
        Inventory inventory = createInventory(holder, 27, "Admin: " + displayName(target));

        inventory.setItem(4, buildPlayerSummaryItem(target));
        inventory.setItem(10, namedItem(Material.BOOK, "Info in den Chat senden",
                "Sendet eine kompakte Uebersicht zu diesem Spieler in den Chat."));
        inventory.setItem(11, namedItem(Material.CLOCK, "Zeit verwalten",
                "Tageslimit setzen oder die heutige Spielzeit zuruecksetzen."));
        inventory.setItem(13, namedItem(Material.EXPERIENCE_BOTTLE, "Level verwalten",
                "Gesamtlevel setzen oder erhoehen."));
        inventory.setItem(15, namedItem(timeManager.isWhitelisted(targetUuid) ? Material.LIME_DYE : Material.GRAY_DYE,
                timeManager.isWhitelisted(targetUuid) ? "Whitelist aktiv" : "Whitelist inaktiv",
                "Klicke, um den Whitelist-Status umzuschalten."));
        inventory.setItem(16, namedItem(Material.BARRIER, "Heutige Zeit zuruecksetzen",
                "Setzt gespielte Zeit und Session-Punkte fuer heute zurueck."));
        inventory.setItem(18, namedItem(Material.ARROW, "Zurueck zur Spielerliste",
                "Zur Spielerauswahl zurueckkehren."));
        inventory.setItem(22, namedItem(Material.OAK_DOOR, "Schliessen",
                "Schliesst die Admin-Oberflaeche."));

        admin.openInventory(inventory);
    }

    public void openLimitMenu(Player admin, UUID targetUuid, int sourcePage) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        AdminMenuHolder holder = new AdminMenuHolder(MenuType.LIMITS, targetUuid, sourcePage);
        Inventory inventory = createInventory(holder, 27, "Zeit: " + displayName(target));

        inventory.setItem(4, buildPlayerSummaryItem(target));
        inventory.setItem(10, namedItem(Material.CLOCK, "Limit auf 30 Minuten setzen",
                "Setzt das Tageslimit auf 30 Minuten."));
        inventory.setItem(11, namedItem(Material.CLOCK, "Limit auf 60 Minuten setzen",
                "Setzt das Tageslimit auf 60 Minuten."));
        inventory.setItem(12, namedItem(Material.CLOCK, "Limit auf 120 Minuten setzen",
                "Setzt das Tageslimit auf 120 Minuten."));
        inventory.setItem(14, namedItem(Material.LIME_STAINED_GLASS_PANE, "15 Minuten hinzufuegen",
                "Erhoeht das Tageslimit dieses Spielers um 15 Minuten."));
        inventory.setItem(15, namedItem(Material.RED_STAINED_GLASS_PANE, "15 Minuten abziehen",
                "Verringert das Tageslimit dieses Spielers um 15 Minuten, minimal 1 Minute."));
        inventory.setItem(16, namedItem(Material.MILK_BUCKET, "Heutige Zeit resetten",
                "Setzt gespielte Zeit und Session-Punkte fuer heute auf 0."));
        inventory.setItem(18, namedItem(Material.ARROW, "Zurueck",
                "Zur Spieleransicht zurueckkehren."));
        inventory.setItem(22, namedItem(Material.OAK_DOOR, "Schliessen",
                "Schliesst die Admin-Oberflaeche."));

        admin.openInventory(inventory);
    }

    public void openLevelMenu(Player admin, UUID targetUuid, int sourcePage) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        AdminMenuHolder holder = new AdminMenuHolder(MenuType.LEVELS, targetUuid, sourcePage);
        Inventory inventory = createInventory(holder, 27, "Level: " + displayName(target));

        inventory.setItem(4, buildPlayerSummaryItem(target));
        inventory.setItem(10, namedItem(Material.GLASS_BOTTLE, "Level auf 0 setzen",
                "Setzt das Gesamtlevel auf 0."));
        inventory.setItem(11, namedItem(Material.EXPERIENCE_BOTTLE, "Level auf 10 setzen",
                "Setzt das Gesamtlevel auf 10."));
        inventory.setItem(12, namedItem(Material.EXPERIENCE_BOTTLE, "Level auf 50 setzen",
                "Setzt das Gesamtlevel auf 50."));
        inventory.setItem(13, namedItem(Material.EXPERIENCE_BOTTLE, "Level auf 100 setzen",
                "Setzt das Gesamtlevel auf 100."));
        inventory.setItem(14, namedItem(Material.LIME_DYE, "1 Level addieren",
                "Erhoeht das Gesamtlevel um 1."));
        inventory.setItem(15, namedItem(Material.LIME_DYE, "5 Level addieren",
                "Erhoeht das Gesamtlevel um 5."));
        inventory.setItem(16, namedItem(Material.LIME_DYE, "10 Level addieren",
                "Erhoeht das Gesamtlevel um 10."));
        inventory.setItem(18, namedItem(Material.ARROW, "Zurueck",
                "Zur Spieleransicht zurueckkehren."));
        inventory.setItem(22, namedItem(Material.OAK_DOOR, "Schliessen",
                "Schliesst die Admin-Oberflaeche."));

        admin.openInventory(inventory);
    }

    public void openGlobalResetConfirm(Player admin) {
        AdminMenuHolder holder = new AdminMenuHolder(MenuType.GLOBAL_RESET_CONFIRM, null, 0);
        Inventory inventory = createInventory(holder, 27, "Globalen Reset bestaetigen");

        inventory.setItem(11, namedItem(Material.GREEN_WOOL, "Abbrechen",
                "Zurueck ins Hauptmenue, ohne etwas zurueckzusetzen."));
        inventory.setItem(15, namedItem(Material.RED_WOOL, "Reset jetzt ausfuehren",
                "Setzt Zeiten, Level, Session-Daten und Laufzeitzustand zurueck."));

        admin.openInventory(inventory);
    }

    public void handleClick(Player admin, AdminMenuHolder holder, int slot) {
        if (!admin.hasPermission("timeperday.admin")) {
            admin.closeInventory();
            admin.sendMessage(Component.text("Du hast keine Berechtigung fuer diese Admin-Oberflaeche.",
                    NamedTextColor.RED));
            return;
        }

        switch (holder.getType()) {
            case MAIN -> handleMainClick(admin, slot);
            case PLAYER_LIST -> handlePlayerListClick(admin, holder, slot);
            case PLAYER_ACTIONS -> handlePlayerActionsClick(admin, holder, slot);
            case LIMITS -> handleLimitClick(admin, holder, slot);
            case LEVELS -> handleLevelClick(admin, holder, slot);
            case GLOBAL_RESET_CONFIRM -> handleGlobalResetClick(admin, slot);
        }
    }

    private void handleMainClick(Player admin, int slot) {
        switch (slot) {
            case 11 -> openPlayerSelection(admin, 0);
            case 13 -> {
                plugin.reloadConfig();
                timeManager.reload();
                admin.sendMessage(Component.text("Konfiguration neu geladen.", NamedTextColor.GREEN));
                openMainMenu(admin);
            }
            case 15 -> openGlobalResetConfirm(admin);
            case 22 -> admin.closeInventory();
            default -> {
            }
        }
    }

    private void handlePlayerListClick(Player admin, AdminMenuHolder holder, int slot) {
        List<OfflinePlayer> players = getSelectablePlayers();
        int index = holder.getPage() * PLAYER_LIST_PAGE_SIZE + slot;

        if (slot < PLAYER_LIST_PAGE_SIZE) {
            if (index < players.size()) {
                openPlayerActions(admin, players.get(index).getUniqueId(), holder.getPage());
            }
            return;
        }

        switch (slot) {
            case 45 -> openPlayerSelection(admin, holder.getPage() - 1);
            case 49 -> openMainMenu(admin);
            case 53 -> openPlayerSelection(admin, holder.getPage() + 1);
            default -> {
            }
        }
    }

    private void handlePlayerActionsClick(Player admin, AdminMenuHolder holder, int slot) {
        UUID targetUuid = requireTargetUuid(admin, holder);
        if (targetUuid == null) {
            return;
        }

        switch (slot) {
            case 10 -> {
                sendPlayerInfo(admin, Bukkit.getOfflinePlayer(targetUuid));
                openPlayerActions(admin, targetUuid, holder.getPage());
            }
            case 11 -> openLimitMenu(admin, targetUuid, holder.getPage());
            case 13 -> openLevelMenu(admin, targetUuid, holder.getPage());
            case 15 -> {
                boolean newState = !timeManager.isWhitelisted(targetUuid);
                timeManager.setWhitelisted(targetUuid, newState);
                admin.sendMessage(Component.text(
                        displayName(Bukkit.getOfflinePlayer(targetUuid))
                                + (newState ? " zur Whitelist hinzugefuegt." : " von der Whitelist entfernt."),
                        NamedTextColor.GREEN));
                openPlayerActions(admin, targetUuid, holder.getPage());
            }
            case 16 -> {
                timeManager.resetPlayerTime(targetUuid);
                admin.sendMessage(Component.text(
                        "Heutige Zeit von " + displayName(Bukkit.getOfflinePlayer(targetUuid)) + " wurde zurueckgesetzt.",
                        NamedTextColor.GREEN));
                openPlayerActions(admin, targetUuid, holder.getPage());
            }
            case 18 -> openPlayerSelection(admin, holder.getPage());
            case 22 -> admin.closeInventory();
            default -> {
            }
        }
    }

    private void handleLimitClick(Player admin, AdminMenuHolder holder, int slot) {
        UUID targetUuid = requireTargetUuid(admin, holder);
        if (targetUuid == null) {
            return;
        }

        switch (slot) {
            case 10 -> setLimitMinutes(admin, targetUuid, 30, holder.getPage());
            case 11 -> setLimitMinutes(admin, targetUuid, 60, holder.getPage());
            case 12 -> setLimitMinutes(admin, targetUuid, 120, holder.getPage());
            case 14 -> adjustLimitMinutes(admin, targetUuid, 15, holder.getPage());
            case 15 -> adjustLimitMinutes(admin, targetUuid, -15, holder.getPage());
            case 16 -> {
                timeManager.resetPlayerTime(targetUuid);
                admin.sendMessage(Component.text("Heutige Zeit zurueckgesetzt.", NamedTextColor.GREEN));
                openLimitMenu(admin, targetUuid, holder.getPage());
            }
            case 18 -> openPlayerActions(admin, targetUuid, holder.getPage());
            case 22 -> admin.closeInventory();
            default -> {
            }
        }
    }

    private void handleLevelClick(Player admin, AdminMenuHolder holder, int slot) {
        UUID targetUuid = requireTargetUuid(admin, holder);
        if (targetUuid == null) {
            return;
        }

        switch (slot) {
            case 10 -> setLevel(admin, targetUuid, 0.0D, holder.getPage());
            case 11 -> setLevel(admin, targetUuid, 10.0D, holder.getPage());
            case 12 -> setLevel(admin, targetUuid, 50.0D, holder.getPage());
            case 13 -> setLevel(admin, targetUuid, 100.0D, holder.getPage());
            case 14 -> addLevel(admin, targetUuid, 1.0D, holder.getPage());
            case 15 -> addLevel(admin, targetUuid, 5.0D, holder.getPage());
            case 16 -> addLevel(admin, targetUuid, 10.0D, holder.getPage());
            case 18 -> openPlayerActions(admin, targetUuid, holder.getPage());
            case 22 -> admin.closeInventory();
            default -> {
            }
        }
    }

    private void handleGlobalResetClick(Player admin, int slot) {
        switch (slot) {
            case 11 -> openMainMenu(admin);
            case 15 -> {
                timeManager.resetEverything();
                Bukkit.broadcast(Component.text(
                        "Der Serverfortschritt wurde global zurueckgesetzt (Zeit, Level, Session, Spielerzustaende).",
                        NamedTextColor.RED));
                admin.closeInventory();
            }
            default -> {
            }
        }
    }

    private void setLimitMinutes(Player admin, UUID targetUuid, long minutes, int sourcePage) {
        timeManager.setLimit(targetUuid, minutes * 60L);
        admin.sendMessage(Component.text(
                "Tageslimit fuer " + displayName(Bukkit.getOfflinePlayer(targetUuid)) + " auf " + minutes
                        + " Minuten gesetzt.",
                NamedTextColor.GREEN));
        openLimitMenu(admin, targetUuid, sourcePage);
    }

    private void adjustLimitMinutes(Player admin, UUID targetUuid, long deltaMinutes, int sourcePage) {
        long currentSeconds = timeManager.getLimit(targetUuid);
        long updatedMinutes = Math.max(1L, (currentSeconds / 60L) + deltaMinutes);
        timeManager.setLimit(targetUuid, updatedMinutes * 60L);
        admin.sendMessage(Component.text(
                "Tageslimit fuer " + displayName(Bukkit.getOfflinePlayer(targetUuid)) + " auf " + updatedMinutes
                        + " Minuten angepasst.",
                NamedTextColor.GREEN));
        openLimitMenu(admin, targetUuid, sourcePage);
    }

    private void setLevel(Player admin, UUID targetUuid, double level, int sourcePage) {
        timeManager.setTotalLevel(targetUuid, level);
        admin.sendMessage(Component.text(
                "Gesamtlevel fuer " + displayName(Bukkit.getOfflinePlayer(targetUuid)) + " auf "
                        + PlayerTimeManager.formatLevel(level) + " gesetzt.",
                NamedTextColor.GREEN));
        openLevelMenu(admin, targetUuid, sourcePage);
    }

    private void addLevel(Player admin, UUID targetUuid, double amount, int sourcePage) {
        timeManager.addTotalLevel(targetUuid, amount);
        admin.sendMessage(Component.text(
                "Gesamtlevel fuer " + displayName(Bukkit.getOfflinePlayer(targetUuid)) + " um "
                        + PlayerTimeManager.formatLevel(amount) + " erhoeht.",
                NamedTextColor.GREEN));
        openLevelMenu(admin, targetUuid, sourcePage);
    }

    private void sendPlayerInfo(Player admin, OfflinePlayer target) {
        PlayerTimeSnapshot snapshot = snapshot(target);
        double progressLevel = timeManager.getProgressLevel(target);
        admin.sendMessage(Component.text(
                "--- Spielzeitinfo: " + displayName(target) + " ---", NamedTextColor.GOLD));
        admin.sendMessage(info("Gespielt heute", PlayerTimeManager.formatTime(snapshot.played())));
        admin.sendMessage(info("Tageslimit", snapshot.unlimited() ? "Unbegrenzt"
                : PlayerTimeManager.formatTime(snapshot.limit())));
        admin.sendMessage(info("Verbleibend", snapshot.unlimited() ? "Unbegrenzt"
                : PlayerTimeManager.formatTime(snapshot.remaining())));
        admin.sendMessage(info("Session-Punkte", PlayerTimeManager.formatLevel(snapshot.sessionPoints())));
        admin.sendMessage(info("Gesamtlevel", PlayerTimeManager.formatLevel(snapshot.totalLevel())));
        admin.sendMessage(info("Progress-Level", PlayerTimeManager.formatLevel(progressLevel)));
        admin.sendMessage(info("Whitelist (unbegrenzt)", snapshot.whitelisted() ? "Ja" : "Nein"));
    }

    private ItemStack buildPlayerSummaryItem(OfflinePlayer target) {
        PlayerTimeSnapshot snapshot = snapshot(target);
        int bestKitLevel = timeManager.getBestKitLevelFor(snapshot.totalLevel());
        double progressLevel = timeManager.getProgressLevel(target);
        return playerHead(target,
                displayName(target),
                "Gespielt: " + PlayerTimeManager.formatTime(snapshot.played()),
                "Limit: " + (snapshot.unlimited() ? "Unbegrenzt" : PlayerTimeManager.formatTime(snapshot.limit())),
                "Verbleibend: " + (snapshot.unlimited() ? "Unbegrenzt"
                        : PlayerTimeManager.formatTime(snapshot.remaining())),
                "Session: " + PlayerTimeManager.formatLevel(snapshot.sessionPoints()),
                "Gesamtlevel: " + PlayerTimeManager.formatLevel(snapshot.totalLevel()),
                "Progress-Level: " + PlayerTimeManager.formatLevel(progressLevel),
                "Bestes Kit: " + (bestKitLevel > 0 ? bestKitLevel : "Keins"),
                "Whitelist: " + (snapshot.whitelisted() ? "Ja" : "Nein")
        );
    }

    private PlayerTimeSnapshot snapshot(OfflinePlayer target) {
        Player online = target.getPlayer();
        return online != null ? timeManager.getSnapshot(online) : timeManager.getSnapshot(target.getUniqueId(), false);
    }

    private @Nullable UUID requireTargetUuid(Player admin, AdminMenuHolder holder) {
        UUID targetUuid = holder.getTargetUuid();
        if (targetUuid == null) {
            admin.closeInventory();
            admin.sendMessage(Component.text("Dieses Menue enthaelt keinen gueltigen Spielerbezug.",
                    NamedTextColor.RED));
        }
        return targetUuid;
    }

    private List<OfflinePlayer> getSelectablePlayers() {
        return Arrays.stream(Bukkit.getOfflinePlayers())
                .filter(player -> player.isOnline() || player.hasPlayedBefore())
                .sorted(Comparator
                        .comparing(OfflinePlayer::isOnline).reversed()
                        .thenComparing(player -> displayName(player).toLowerCase()))
                .toList();
    }

    private Inventory createInventory(AdminMenuHolder holder, int size, String title) {
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inventory);
        return inventory;
    }

    private ItemStack namedItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD));
        meta.lore(buildLore(loreLines));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack playerHead(OfflinePlayer player, String name, String... loreLines) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(Component.text(name, NamedTextColor.YELLOW));
        meta.lore(buildLore(loreLines));
        item.setItemMeta(meta);
        return item;
    }

    private List<Component> buildLore(String... lines) {
        List<Component> lore = new ArrayList<>(lines.length);
        for (String line : lines) {
            lore.add(Component.text(line, NamedTextColor.GRAY));
        }
        return lore;
    }

    private String displayName(OfflinePlayer player) {
        String name = player.getName();
        return name != null ? name : player.getUniqueId().toString();
    }

    private Component info(String label, String value) {
        return Component.text(label + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.WHITE));
    }
}
