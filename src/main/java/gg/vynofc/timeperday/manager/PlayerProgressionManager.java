package gg.vynofc.timeperday.manager;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

class PlayerProgressionManager {

    private final PlayerTimeManager manager;

    PlayerProgressionManager(PlayerTimeManager manager) {
        this.manager = manager;
    }

    int getBestKitLevelFor(double level) {
        int best = 0;
        for (Integer threshold : manager.spawnKits.keySet()) {
            if (level >= threshold) {
                best = threshold;
            }
        }
        return best;
    }

    boolean grantDailyKit(Player player) {
        UUID uuid = player.getUniqueId();
        if (manager.isWhitelisted(uuid) || player.hasPermission("timeperday.bypass")) {
            return false;
        }

        String claimed = manager.lastKitClaimDate.getOrDefault(uuid, "");
        if (manager.currentDate.equals(claimed)) {
            return false;
        }

        int kitLevel = getBestKitLevelFor(manager.getTotalLevel(uuid));
        if (kitLevel <= 0) {
            return false;
        }

        var kit = manager.spawnKits.get(kitLevel);
        if (kit == null || kit.isEmpty()) {
            return false;
        }

        for (Map.Entry<Material, Integer> entry : kit.entrySet()) {
            ItemStack stack = new ItemStack(entry.getKey(), entry.getValue());
            var overflow = player.getInventory().addItem(stack);
            if (!overflow.isEmpty()) {
                overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            }
        }

        manager.lastKitClaimDate.put(uuid, manager.currentDate);
        manager.save();
        return true;
    }

    double finalizeSessionProgress(UUID uuid) {
        return finalizeSessionProgress(uuid, manager.sessionPoints.getOrDefault(uuid, 0.0D));
    }

    double finalizeSessionProgress(UUID uuid, double gained) {
        if (gained > 0.0D) {
            manager.totalLevel.merge(uuid, gained, Double::sum);
        }
        manager.sessionPoints.remove(uuid);
        manager.save();
        return gained;
    }

    double finalizeSessionProgress(Player player) {
        UUID uuid = player.getUniqueId();
        if (manager.isWhitelisted(uuid) || player.hasPermission("timeperday.bypass")) {
            manager.sessionPoints.remove(uuid);
            return 0.0D;
        }

        return finalizeSessionProgress(uuid, calculateInventorySessionPoints(player));
    }

    double calculateInventorySessionPoints(Player player) {
        UUID uuid = player.getUniqueId();
        if (manager.isWhitelisted(uuid) || player.hasPermission("timeperday.bypass")) {
            return 0.0D;
        }

        return calculateItemPoints(player.getInventory().getStorageContents())
                + calculateItemPoints(player.getInventory().getArmorContents())
                + calculateItemPoints(new ItemStack[]{player.getInventory().getItemInOffHand()})
                + calculateItemPoints(player.getEnderChest().getContents());
    }

    private double calculateItemPoints(ItemStack[] contents) {
        double total = 0.0D;
        for (ItemStack item : contents) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            double lvl = readItemLevel(item.getType());
            if (lvl > 0.0D) {
                total += lvl * item.getAmount();
            }
        }
        return total;
    }

    private double readItemLevel(Material material) {
        String modernPath = "progression.items." + material.name();
        if (manager.plugin.getConfig().isConfigurationSection(modernPath)) {
            double level = manager.plugin.getConfig().getDouble(modernPath + ".level", 0.0D);
            if (level > 0.0D) {
                return level;
            }
        }
        return 0.0D;
    }
}
