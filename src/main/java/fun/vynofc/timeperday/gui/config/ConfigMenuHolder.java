package fun.vynofc.timeperday.gui.config;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class ConfigMenuHolder implements InventoryHolder {

    private final ConfigPageType pageType;
    private Inventory inventory;

    public ConfigMenuHolder(ConfigPageType pageType) {
        this.pageType = pageType;
    }

    public ConfigPageType getPageType() {
        return pageType;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}