package gg.vynofc.timeperday.listener;

import gg.vynofc.timeperday.manager.PlayerTimeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;

public class PlayerItemListener implements Listener {

    private final PlayerTimeManager timeManager;

    public PlayerItemListener(PlayerTimeManager timeManager) {
        this.timeManager = timeManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        var stack = event.getItem().getItemStack();
        double gained = timeManager.addSessionPoints(player, stack.getType(), stack.getAmount());
        if (gained > 0.0D) {
            player.sendActionBar(Component.text("+" + PlayerTimeManager.formatLevel(gained) + " Level",
                    NamedTextColor.GREEN));
        }
    }
}
