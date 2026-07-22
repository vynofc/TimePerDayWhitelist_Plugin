package fun.vynofc.timeperday.manager;

import org.bukkit.Bukkit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

class PlayerTickManager {

    private final PlayerTimeManager manager;
    private volatile Set<Long> warningThresholds = Set.of();

    PlayerTickManager(PlayerTimeManager manager) {
        this.manager = manager;
    }

    void reloadWarningThresholds() {
        List<Long> configuredThresholds = manager.plugin.getConfig().getLongList("warnings");
        Set<Long> normalized = new HashSet<>();
        for (Long threshold : configuredThresholds) {
            if (threshold != null && threshold >= 0L) {
                normalized.add(threshold);
            }
        }
        warningThresholds = normalized;
    }

    void tickOnlinePlayers() {
        String today = LocalDate.now(manager.getResetZoneId()).format(PlayerTimeManager.getDateFormatter());
        if (!today.equals(manager.currentDate)) {
            manager.triggerDayOver(today, "Taegliche Spielzeiten zurueckgesetzt (Mitternacht).");
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(manager.plugin, task -> tickPlayer(player), null);
        }

        manager.tickCount++;
        if (manager.tickCount % manager.getAutoSaveIntervalTicks() == 0) {
            manager.flushIfDirty();
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

    private Component buildActionBarMessage(long remainingSeconds) {
        long safeRemaining = Math.max(0L, remainingSeconds);
        long h = safeRemaining / 3600;
        long m = (safeRemaining % 3600) / 60;
        long s = safeRemaining % 60;

        String timeText;
        if (h > 0) {
            timeText = h + "h " + m + "m " + s + "s";
        } else if (m > 0) {
            timeText = m + "m " + s + "s";
        } else {
            timeText = s + "s";
        }

        return Component.text("Verbleibend: ", NamedTextColor.GRAY)
                .append(Component.text(timeText, NamedTextColor.GREEN));
    }

    private void tickPlayer(Player player) {
        if (!player.isOnline()) {
            return;
        }

        enforceMaxHealth(player);

        UUID uuid = player.getUniqueId();
        if (manager.isWhitelisted(uuid) || player.hasPermission("timeperday.bypass")) {
            return;
        }

        long played = manager.playedToday.merge(uuid, 1L, Long::sum);
        long limit = manager.getLimit(uuid);
        long remaining = limit - played;

        if (manager.isShowActionBarEnabled(uuid)) {
            player.sendActionBar(buildActionBarMessage(remaining));
        }

        if (warningThresholds.contains(remaining)) {
            manager.messageManager.sendWarning(player, remaining);
        }

        if (remaining <= 0) {
            double gained = manager.progressionManager.finalizeSessionProgress(player);
            manager.messageManager.kickPlayer(player, gained, manager.getTotalLevel(uuid));
        }
    }

    private void enforceMaxHealth(Player player) {
        if (!manager.isMaxHealthEnabled()) {
            return;
        }
        Double maxHp = manager.getMaxHealth();
        if (maxHp == null) {
            return;
        }
        var attr = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (attr == null) {
            return;
        }
        if (attr.getValue() != maxHp) {
            attr.setBaseValue(maxHp);
        }
        if (player.getHealth() > maxHp) {
            player.setHealth(maxHp);
        }
    }
}

