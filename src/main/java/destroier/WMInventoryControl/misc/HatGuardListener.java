package destroier.WMInventoryControl.misc;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import destroier.WMInventoryControl.WMInventoryControl;
import destroier.WMInventoryControl.api.WMIC;
import destroier.WMInventoryControl.api.types.UnmarkCause;
import destroier.WMInventoryControl.debug.Debug;
import destroier.WMInventoryControl.managers.InventoryManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Locale;

/**
 * Prevents marked weapons from being equipped in the head/hat slot.
 *
 * <p>Stops edge cases where a weapon could be worn as armor via swaps or GUI interactions.</p>
 */
public final class HatGuardListener implements Listener {
    private final WMInventoryControl plugin;
    private final InventoryManager inv;

    public HatGuardListener(WMInventoryControl plugin) {
        this.plugin = plugin;
        this.inv = plugin.getInventoryManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArmorChange(PlayerArmorChangeEvent e) {
        if (e.getSlot() != EquipmentSlot.HEAD) return;

        ItemStack newItem = e.getNewItem();
        if (newItem.getType().isAir()) return;
        if (!inv.isWeaponMarked(newItem)) return;

        // revert next tick so we don't fight the mutator
        Bukkit.getScheduler().runTask(plugin, () ->
                revertHat(e.getPlayer(), newItem, e.getOldItem()));
        WMIC.api().unmark(e.getPlayer(), newItem, UnmarkCause.HAT_GUARD);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHatCommand(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage().toLowerCase(Locale.ROOT);
        if (!msg.startsWith("/hat") && !msg.startsWith("/essentials:hat")) return;

        // after Essentials runs we check the helmet
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = e.getPlayer();
            ItemStack hat = p.getInventory().getHelmet();
            if (hat != null && !hat.getType().isAir() && inv.isWeaponMarked(hat)) {
                revertHat(p, hat, null);
            }
        });
    }

    private void revertHat(Player p, ItemStack illegalHat, ItemStack previous) {
        var plInv = p.getInventory();

        // restore previous helmet (if any)
        plInv.setHelmet(previous);

        // return the weapon to the inventory or drop it if full
        ItemStack toReturn = illegalHat.clone();
        HashMap<Integer, ItemStack> leftovers = plInv.addItem(toReturn);
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(it ->
                    p.getWorld().dropItemNaturally(p.getLocation(), it));
        }

        p.updateInventory();
        p.sendMessage("§c(!) Marked weapons cannot be worn as hats.");
        Debug.log(plugin, Debug.DebugKey.HAT_GUARD,"[WMIC] Blocked /hat for " + p.getName() + " with marked item " + illegalHat.getType());
    }
}