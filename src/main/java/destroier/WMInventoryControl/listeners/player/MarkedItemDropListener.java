package destroier.WMInventoryControl.listeners.player;

import destroier.WMInventoryControl.WMInventoryControl;
import destroier.WMInventoryControl.api.WMIC;
import destroier.WMInventoryControl.api.types.UnmarkCause;
import destroier.WMInventoryControl.debug.Debug;
import destroier.WMInventoryControl.debug.Debug.DebugKey;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Unmarks a weapon when the player drops it and blocks drops while in combat.
 *
 * <p>Prevents throwing marked items during combat and avoids marked items persisting on the ground.</p>
 */
public class MarkedItemDropListener implements Listener {
    private final WMInventoryControl plugin;

    public MarkedItemDropListener(WMInventoryControl plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack droppedItem = event.getItemDrop().getItemStack();

        if (!plugin.getInventoryManager().isWeaponMarked(droppedItem)) {
            return;
        }

        Debug.log(plugin, DebugKey.PLAYER_DROP, "Successfully triggered the drop event!");

        if (event.isCancelled()) {
            Debug.log(plugin, DebugKey.PLAYER_DROP, "Drop event was cancelled by another plugin! Skipping unmarking.");
            return;
        }

        if (droppedItem.getType() == Material.AIR) {
            return;
        }

        if (plugin.getCombatRestrictionsManager().isInCombat(player)
                && plugin.getConfigManager().isBlockDroppingInCombat()) {
            player.sendMessage("§c(!) You can't drop an item while in combat!");
            event.setCancelled(true);
            return;
        }

        if (plugin.getInventoryManager().isWeaponMarked(droppedItem)) {
            WMIC.api().unmark(player, droppedItem, UnmarkCause.PLAYER_DROP);
            player.sendMessage("§c(!) You have unmarked this weapon.");
        }
    }
}