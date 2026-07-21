package fun.vynofc.timeperday.gui;

import fun.vynofc.timeperday.manager.PlayerTimeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UserSettingsMenuService {

    private final PlayerTimeManager timeManager;

    public UserSettingsMenuService(PlayerTimeManager timeManager) {
        this.timeManager = timeManager;
    }

    public void openSettings(Player player) {
        UUID uuid = player.getUniqueId();
        UserSettingsHolder holder = new UserSettingsHolder(uuid);
        Inventory inventory = createInventory(holder, 27, "Einstellungen");

        boolean actionBarEnabled = timeManager.isShowActionBarEnabled(uuid);
        inventory.setItem(11, toggleItem(
                Material.CLOCK,
                "Verbleibende Zeit in Action-Bar",
                actionBarEnabled,
                "Zeigt die verbleibende Spielzeit live in der Action-Bar an.",
                actionBarEnabled ? "Aktuell: Aktiviert" : "Aktuell: Deaktiviert",
                "Klicke zum Umschalten."));

        inventory.setItem(13, placeholderItem(Material.BELL,
                "Einstellung 2",
                "Diese Einstellung wird in einem zukuenftigen Update verfuegbar sein."));
        inventory.setItem(15, placeholderItem(Material.JUKEBOX,
                "Einstellung 3",
                "Diese Einstellung wird in einem zukuenftigen Update verfuegbar sein."));
        inventory.setItem(22, namedItem(Material.BARRIER, "Schliessen",
                "Schliesst das Einstellungs-Menue."));

        player.openInventory(inventory);
    }

    public void handleClick(Player player, UserSettingsHolder holder, int slot) {
        if (!player.getUniqueId().equals(holder.getOwnerUuid())) {
            player.closeInventory();
            player.sendMessage(Component.text("Dieses Menue gehoert einem anderen Spieler.",
                    NamedTextColor.RED));
            return;
        }

        switch (slot) {
            case 11 -> {
                UUID uuid = player.getUniqueId();
                boolean newState = !timeManager.isShowActionBarEnabled(uuid);
                timeManager.setShowActionBar(uuid, newState);
                player.sendMessage(Component.text()
                        .append(Component.text("Action-Bar-Anzeige ", NamedTextColor.GREEN))
                        .append(Component.text(newState ? "aktiviert." : "deaktiviert.", NamedTextColor.YELLOW))
                        .build());
                openSettings(player);
            }
            case 13, 15 -> {
                player.sendMessage(Component.text(
                        "Diese Einstellung ist noch nicht verfuegbar.",
                        NamedTextColor.YELLOW));
            }
            case 22 -> player.closeInventory();
            default -> {
            }
        }
    }

    private Inventory createInventory(UserSettingsHolder holder, int size, String title) {
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

    private ItemStack toggleItem(Material material, String name, boolean enabled, String... loreLines) {
        ItemStack item = new ItemStack(enabled ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, enabled ? NamedTextColor.GREEN : NamedTextColor.RED));
        meta.lore(buildLore(loreLines));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack placeholderItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
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
}