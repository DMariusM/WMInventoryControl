package destroier.WMInventoryControl.api.events;

import destroier.WMInventoryControl.api.types.DenyReason;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Fired before WMIC marks an item. Can be cancelled by other plugins.
 * If cancelled, WMIC will not mark. A DenyReason can be attached for context.
 */
public class WeaponPreMarkEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;
    private final @NotNull Player player;
    private final @NotNull ItemStack item;
    private DenyReason denyReason;

    public WeaponPreMarkEvent(@NotNull Player player, @NotNull ItemStack item) {
        this.player = Objects.requireNonNull(player, "player");
        this.item   = Objects.requireNonNull(item, "item");
    }

    public @NotNull Player getPlayer() {
        return player;
    }

    public @NotNull ItemStack getItem() {
        return item;
    }

    public DenyReason getDenyReason() {
        return denyReason;
    }

    public void setDenyReason(DenyReason r) {
        this.denyReason = r;
    }

    @Override public boolean isCancelled() {
        return cancelled;
    }

    @Override public void setCancelled(boolean c) {
        this.cancelled = c;
    }

    @Override public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}