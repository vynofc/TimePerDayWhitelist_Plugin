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

public class UserSettingsMenuService {

    private final PlayerTimeManager timeManager;

    public UserSettingsMenuService(PlayerTimeManager timeManager) {
        this.timeManager = timeManager;
    }

    public void openSettings(Player player) {
        UserSettingsHolder holder = new UserSettingsHolder(player.getUniqueId());
        Inventory inventory = createInventory(holder, 27, "Einstellungen");

        inventory.setItem(11, placeholderItem(Material.NOTE_BLOCK,
                "Einstellung 1",
                "Diese Einstellung wird in einem zukuenftigen Update verfuegbar sein."));
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
            case 11, 13, 15 -> {
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