package fun.vynofc.timeperday.listener;

import fun.vynofc.timeperday.TimePerDayPlugin;
import fun.vynofc.timeperday.border.BorderManager;
import fun.vynofc.timeperday.manager.PlayerTimeManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class PlayerListener implements Listener {

    private final TimePerDayPlugin plugin;
    private final PlayerTimeManager timeManager;
    private final BorderManager borderManager;

    public PlayerListener(TimePerDayPlugin plugin, PlayerTimeManager timeManager, BorderManager borderManager) {
        this.plugin = plugin;
        this.timeManager = timeManager;
        this.borderManager = borderManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();

        ensureBorderExists(player);
        enforceMaxHealthOnJoin(player);
        handleStreakBonus(player);

        timeManager.handlePlayerJoinWorldCheck(player);

        var uuid = player.getUniqueId();

        // Whitelisted oder Bypass-Permission → keine Aktion
        if (timeManager.isWhitelisted(uuid) || player.hasPermission("timeperday.bypass")) {
            return;
        }

        // Ausstehender Day-Over-Reset: Inventar leeren und zu Spawn teleportieren,
        // danach Kit vergeben und Join-Nachricht anzeigen.
        // 1-Tick-Verzögerung: PlayerJoinEvent muss erst abgeschlossen sein,
        // bevor Inventar/Position geändert werden dürfen (gleiches Muster wie Kick).
        if (timeManager.consumePendingDayOverReset(uuid)) {
            player.getScheduler().runDelayed(plugin, task -> {
                timeManager.applyDayOverReset(player);
                boolean kitGiven = timeManager.grantDailyKit(player);
                if (plugin.getConfig().getBoolean("show-on-join", true)) {
                    player.sendMessage(timeManager.buildJoinInfoComponent(player, kitGiven));
                }
            }, null, 1L);
            return;
        }

        long limit = timeManager.getLimit(uuid);
        long played = timeManager.getPlayedToday(uuid);
        long remaining = limit - played;

        if (remaining <= 0) {
            // Limit bereits ausgeschöpft – mit 1 Tick Verzögerung rauswerfen
            // (PlayerJoinEvent muss erst abgeschlossen sein)
            player.getScheduler().runDelayed(plugin, task ->
                    player.kick(timeManager.buildJoinKickComponent(uuid)), null, 1L);
        } else {
            boolean kitGiven = timeManager.grantDailyKit(player);
            if (plugin.getConfig().getBoolean("show-on-join", true)) {
                player.sendMessage(timeManager.buildJoinInfoComponent(player, kitGiven));
            }
        }
    }

    private void ensureBorderExists(org.bukkit.entity.Player player) {
        String worldName = player.getWorld().getName();
        if (borderManager.getBorder(worldName) != null) {
            return;
        }
        double defaultSize = plugin.getConfig().getDouble("world-regeneration.default-border-size", 1000);
        plugin.getLogger().info("Border: Keine Border für Welt '" + worldName
                + "' gefunden, erstelle Standard-Border mit Größe " + ((int) defaultSize));
        borderManager.addBorder(worldName, 0, 0, defaultSize);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!timeManager.isVaultKeepOnDeathEnabled()) {
            return;
        }
        event.setKeepLevel(true);
        event.setDroppedExp(0);
    }

    private void handleStreakBonus(org.bukkit.entity.Player player) {
        if (!timeManager.isStreakBonusEnabled()) {
            return;
        }
        var uuid = player.getUniqueId();
        String today = java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String lastDate = timeManager.getLastLoginDate(uuid);

        if (today.equals(lastDate)) {
            return;
        }

        int streak;
        if (lastDate.isEmpty()) {
            streak = 1;
        } else {
            try {
                LocalDate last = LocalDate.parse(lastDate);
                LocalDate current = LocalDate.parse(today);
                if (last.plusDays(1).equals(current)) {
                    streak = timeManager.getStreakCount(uuid) + 1;
                } else {
                    streak = 1;
                }
            } catch (Exception e) {
                streak = 1;
            }
        }

        timeManager.setStreakCount(uuid, streak);
        timeManager.setLastLoginDate(uuid, today);

        if (streak > 1) {
            double bonus = timeManager.getStreakBonusLevel() * streak;
            timeManager.addTotalLevel(uuid, bonus);
            player.sendMessage(net.kyori.adventure.text.Component.text()
                    .append(net.kyori.adventure.text.Component.text("Streak-Bonus: ", net.kyori.adventure.text.format.NamedTextColor.GOLD))
                    .append(net.kyori.adventure.text.Component.text(streak + " Tage in Folge", net.kyori.adventure.text.format.NamedTextColor.YELLOW))
                    .append(net.kyori.adventure.text.Component.text(" → +" + String.format("%.1f", bonus) + " Level", net.kyori.adventure.text.format.NamedTextColor.GREEN))
                    .build());
        }
    }

    private void enforceMaxHealthOnJoin(org.bukkit.entity.Player player) {
        if (!timeManager.isMaxHealthEnabled()) {
            return;
        }
        Double maxHp = timeManager.getMaxHealth();
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


