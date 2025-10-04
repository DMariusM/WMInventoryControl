package destroier.WMInventoryControl.api.events;

import destroier.WMInventoryControl.api.types.UnmarkCause;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Fired after WMIC unmarks an item (player may be null for non-player flows) */
public class WeaponUnmarkedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player; // can be null
    private final ItemStack item;
    private final String weaponTitle; // may be null if not a WM item
    private final UnmarkCause cause;

    public WeaponUnmarkedEvent(@Nullable Player player, @NotNull ItemStack item, @Nullable String weaponTitle, @NotNull UnmarkCause cause) {
        this.player = player;
        this.item = item;
        this.weaponTitle = weaponTitle;
        this.cause = cause;
    }

    public Player getPlayer() {
        return player;
    }

    public ItemStack getItem() {
        return item;
    }

    public String getWeaponTitle() {
        return weaponTitle;
    }

    public UnmarkCause getCause() {
        return cause;
    }

    @Override public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}