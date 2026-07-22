package fun.vynofc.timeperday.command;

import fun.vynofc.timeperday.gui.config.ConfigMenuService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ConfigCommand implements CommandExecutor {

    private final ConfigMenuService configMenuService;

    public ConfigCommand(ConfigMenuService configMenuService) {
        this.configMenuService = configMenuService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(
                    "Dieser Befehl kann nur von einem Spieler ausgefuehrt werden.",
                    NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("timeperday.admin")) {
            player.sendMessage(Component.text(
                    "Du hast keine Berechtigung fuer diesen Befehl.", NamedTextColor.RED));
            return true;
        }

        configMenuService.openMainPage(player);
        return true;
    }
}