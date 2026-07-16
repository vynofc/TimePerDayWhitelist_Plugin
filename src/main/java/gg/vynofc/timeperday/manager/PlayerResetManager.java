package gg.vynofc.timeperday.manager;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

class PlayerResetManager {

    private final PlayerTimeManager manager;

    PlayerResetManager(PlayerTimeManager manager) {
        this.manager = manager;
    }

    void triggerDayOver(String newDate, String logMessage) {
        Set<UUID> allTrackedUuids = new HashSet<>(manager.playedToday.keySet());
        allTrackedUuids.addAll(manager.sessionPoints.keySet());
        allTrackedUuids.addAll(manager.lastKitClaimDate.keySet());
        allTrackedUuids.addAll(manager.totalLevel.keySet());
        allTrackedUuids.addAll(manager.playerLimits.keySet());

        Set<UUID> onlineAtTrigger = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            onlineAtTrigger.add(player.getUniqueId());
        }

        manager.currentDate = newDate;
        manager.playedToday.clear();
        manager.lastKitClaimDate.clear();
        manager.plugin.getLogger().info(logMessage);
        manager.save();

        for (UUID uuid : allTrackedUuids) {
            if (onlineAtTrigger.contains(uuid)) {
                continue;
            }
            manager.sessionPoints.remove(uuid);
            if (!manager.isWhitelisted(uuid)) {
                manager.pendingDayOverReset.add(uuid);
            }
        }

        manager.plugin.getServer().getGlobalRegionScheduler().run(manager.plugin, task -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                if (!onlineAtTrigger.contains(uuid)) {
                    continue;
                }

                if (!manager.isWhitelisted(uuid) && !player.hasPermission("timeperday.bypass")) {
                    player.getScheduler().run(manager.plugin, scheduledTask -> {
                        resetOnlinePlayerState(player);
                        manager.grantDailyKit(player);
                    }, null);
                } else {
                    manager.sessionPoints.remove(uuid);
                }
            }
        });
    }

    synchronized void resetEverything() {
        manager.playedToday.clear();
        manager.playerLimits.clear();
        manager.whitelist.clear();
        manager.sessionPoints.clear();
        manager.totalLevel.clear();
        manager.lastKitClaimDate.clear();

        manager.currentDate = LocalDate.now().format(PlayerTimeManager.getDateFormatter());
        manager.save();

        manager.plugin.getServer().getGlobalRegionScheduler().run(manager.plugin, task -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.getScheduler().run(manager.plugin, scheduledTask -> resetOnlinePlayerState(player), null);
            }

            for (World world : Bukkit.getWorlds()) {
                resetWorldRuntimeState(world);
            }
        });
    }

    void resetOnlinePlayerState(Player player) {
        manager.progressionManager.finalizeSessionProgress(player);

        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        player.getEnderChest().clear();

        player.setLevel(0);
        player.setExp(0.0f);
        player.setTotalExperience(0);

        var healthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            player.setHealth(healthAttr.getValue());
        }
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setFireTicks(0);
        player.setFallDistance(0.0f);

        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(player.getWorld().getSpawnLocation());
    }

    private void resetWorldRuntimeState(World world) {
        world.setTime(0L);
        world.setStorm(false);
        world.setThundering(false);

        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        }
    }
}
