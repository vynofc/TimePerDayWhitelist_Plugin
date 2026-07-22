package fun.vynofc.timeperday.border;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class BorderListener implements Listener {

    private final BorderManager borderManager;

    public BorderListener(BorderManager borderManager) {
        this.borderManager = borderManager;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null || event.getTo().getWorld() == null) {
            return;
        }

        Player player = event.getPlayer();
        String worldName = event.getTo().getWorld().getName();
        BorderData border = borderManager.getBorder(worldName);
        if (border == null) {
            return;
        }

        if (border.isBypassing(player.getUniqueId())
                || player.hasPermission("timeperday.border.bypass.move")) {
            return;
        }

        Location to = event.getTo();
        if (border.isBounding(to.getX(), to.getZ())) {
            return;
        }

        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            event.setCancelled(true);
            return;
        }
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            event.setCancelled(true);
            return;
        }

        World world = to.getWorld();
        Location spawn = world.getSpawnLocation();
        spawn.setYaw(to.getYaw());
        spawn.setPitch(to.getPitch());
        event.setTo(spawn);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        String worldName = event.getEntity().getWorld().getName();
        BorderData border = borderManager.getBorder(worldName);
        if (border == null) {
            return;
        }

        Location loc = event.getLocation();
        if (!border.isBounding(loc.getX(), loc.getZ())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        String worldName = player.getWorld().getName();
        BorderData border = borderManager.getBorder(worldName);
        if (border == null) {
            return;
        }

        if (border.isBypassing(player.getUniqueId())
                || player.hasPermission("timeperday.border.bypass.break")) {
            return;
        }

        Location loc = event.getBlock().getLocation();
        double x = ((int) loc.getX()) + 0.5;
        double z = ((int) loc.getZ()) + 0.5;
        if (!border.isBounding(x, z)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        String worldName = player.getWorld().getName();
        BorderData border = borderManager.getBorder(worldName);
        if (border == null) {
            return;
        }

        if (border.isBypassing(player.getUniqueId())
                || player.hasPermission("timeperday.border.bypass.place")) {
            return;
        }

        Location loc = event.getBlock().getLocation();
        double x = ((int) loc.getX()) + 0.5;
        double z = ((int) loc.getZ()) + 0.5;
        if (!border.isBounding(x, z)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        borderManager.removePlayerData(event.getPlayer().getUniqueId());
    }
}