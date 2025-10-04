package destroier.WMInventoryControl.listeners.containers;

import destroier.WMInventoryControl.WMInventoryControl;
import destroier.WMInventoryControl.api.WMIC;
import destroier.WMInventoryControl.api.types.UnmarkCause;
import destroier.WMInventoryControl.debug.Debug;
import destroier.WMInventoryControl.managers.InventoryManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Blocks inserting marked weapons into item frames and cleans up on removal.
 *
 * <p>Prevents display-frame interactions from bypassing mark restrictions.</p>
 */
public class ItemFrameListener implements Listener {

    private final WMInventoryControl plugin;
    private final InventoryManager inventoryManager;

    public ItemFrameListener(WMInventoryControl plugin) {
        this.plugin = plugin;
        this.inventoryManager = plugin.getInventoryManager();
    }

    @EventHandler(priority = EventPriority.MONITOR) // don't ignore cancelled; we'll check it
    public void onPlayerInteractWithItemFrame(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame frame)) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return; // avoid double fire
        if (event.isCancelled()) return;

        final Player player = event.getPlayer();

        // after Bukkit processes the interaction, check the frame and cursor
        Bukkit.getScheduler().runTask(plugin, () -> {
            // 1. Item placed into frame
            ItemStack inFrame = frame.getItem();
            if (inventoryManager.isWeaponMarked(inFrame)) {
                WMIC.api().unmark(player, inFrame, UnmarkCause.ITEM_FRAME);
                frame.setItem(inFrame, false);
                player.sendMessage("§c(!) The weapon you placed in the item frame has been unmarked.");
                Debug.log(plugin, Debug.DebugKey.ITEM_FRAME, "[WMIC] Cleaned marked weapon in ItemFrame: " + inFrame.getType());
                return;
            }

            // 2. Item taken from frame (now on cursor)
            ItemStack cursor = player.getItemOnCursor();
            if (inventoryManager.isWeaponMarked(cursor)) {
                WMIC.api().unmark(player, cursor, UnmarkCause.ITEM_FRAME);
                player.sendMessage("§c(!) The weapon you took from the item frame has been unmarked.");
                Debug.log(plugin, Debug.DebugKey.ITEM_FRAME, "[WMIC] Cleaned marked weapon taken from ItemFrame: " + cursor.getType());
            }
        });
    }
}