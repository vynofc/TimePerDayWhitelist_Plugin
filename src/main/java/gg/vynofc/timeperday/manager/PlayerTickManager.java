package gg.vynofc.timeperday.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

class PlayerTickManager {

    private final PlayerTimeManager manager;

    PlayerTickManager(PlayerTimeManager manager) {
        this.manager = manager;
    }

    void tickOnlinePlayers() {
        String today = LocalDate.now().format(PlayerTimeManager.getDateFormatter());
        if (!today.equals(manager.currentDate)) {
            manager.triggerDayOver(today, "Taegliche Spielzeiten zurueckgesetzt (Mitternacht).");
            return;
        }

        List<Long> warningThresholds = manager.plugin.getConfig().getLongList("warnings");
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(manager.plugin, task -> tickPlayer(player, warningThresholds), null);
        }

        manager.tickCount++;
        if (manager.tickCount % 300 == 0) {
            manager.save();
        }
    }

    boolean debugTriggerTimeout(Player player) {
        UUID uuid = player.getUniqueId();
        if (manager.isWhitelisted(uuid) || player.hasPermission("timeperday.bypass")) {
            return false;
        }

        manager.playedToday.put(uuid, manager.getLimit(uuid));
        double gained = manager.progressionManager.finalizeSessionProgress(player);
        manager.messageManager.kickPlayer(player, gained, manager.getTotalLevel(uuid));
        return true;
    }

    void debugTriggerWarning(Player player, long remainingSeconds) {
        manager.messageManager.sendWarning(player, Math.max(0L, remainingSeconds));
    }

    private void tickPlayer(Player player, List<Long> warningThresholds) {
        if (!player.isOnline()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (manager.isWhitelisted(uuid) || player.hasPermission("timeperday.bypass")) {
            return;
        }

        long played = manager.playedToday.merge(uuid, 1L, Long::sum);
        long limit = manager.getLimit(uuid);
        long remaining = limit - played;

        if (warningThresholds.contains(remaining)) {
            manager.messageManager.sendWarning(player, remaining);
        }

        if (remaining <= 0) {
            double gained = manager.progressionManager.finalizeSessionProgress(player);
            manager.messageManager.kickPlayer(player, gained, manager.getTotalLevel(uuid));
        }
    }
}
