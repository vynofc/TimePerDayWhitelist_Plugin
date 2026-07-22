package fun.vynofc.timeperday.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class AdminMenuHolder implements InventoryHolder {

    private final MenuType type;
    private final UUID targetUuid;
    private final int page;
    private Inventory inventory;

    public AdminMenuHolder(MenuType type, @Nullable UUID targetUuid, int page) {
        this.type = type;
        this.targetUuid = targetUuid;
        this.page = page;
    }

    public MenuType getType() {
        return type;
    }

    public @Nullable UUID getTargetUuid() {
        return targetUuid;
    }

    public int getPage() {
        return page;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}

