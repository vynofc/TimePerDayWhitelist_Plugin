package fun.vynofc.timeperday.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class UserSettingsMenuListener implements Listener {

    private final UserSettingsMenuService userSettingsMenuService;

    public UserSettingsMenuListener(UserSettingsMenuService userSettingsMenuService) {
        this.userSettingsMenuService = userSettingsMenuService;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof UserSettingsHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize()) {
            return;
        }

        userSettingsMenuService.handleClick(player, holder, event.getRawSlot());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof UserSettingsHolder) {
            event.setCancelled(true);
        }
    }
}