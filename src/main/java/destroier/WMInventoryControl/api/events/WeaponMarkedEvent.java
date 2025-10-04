package destroier.WMInventoryControl.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Fired after WMIC marks an item. */
public class WeaponMarkedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final ItemStack item;
    private final String weaponTitle;

    public WeaponMarkedEvent(@NotNull Player player, @NotNull ItemStack item, @Nullable String weaponTitle) {
        this.player = player;
        this.item = item;
        this.weaponTitle = weaponTitle;
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

    @Override public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}