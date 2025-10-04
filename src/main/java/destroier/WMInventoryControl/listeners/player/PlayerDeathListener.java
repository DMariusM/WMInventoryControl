package destroier.WMInventoryControl.listeners.player;

import destroier.WMInventoryControl.WMInventoryControl;
import destroier.WMInventoryControl.api.WMIC;
import destroier.WMInventoryControl.api.types.UnmarkCause;
import destroier.WMInventoryControl.debug.Debug;
import destroier.WMInventoryControl.managers.InventoryManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Cleans up marked/weight state on player death.
 *
 * <p>Ensures marks and per-combat counters do not persist across deaths.</p>
 */
public class PlayerDeathListener implements Listener {

    private final WMInventoryControl plugin;
    private final InventoryManager inventoryManager;

    public PlayerDeathListener(WMInventoryControl plugin) {
        this.plugin = plugin;
        this.inventoryManager = plugin.getInventoryManager();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        Debug.log(plugin, Debug.DebugKey.PLAYER_DEATH, "[WMIC] Player " + player.getName() + " has died. Checking for marked weapons...");

        // unmark all weapons in the player's drops
        for (ItemStack item : event.getDrops()) {
            if (inventoryManager.isWeaponMarked(item)) {
                WMIC.api().unmark(item, UnmarkCause.DEATH_DROP);
                Debug.log(plugin, Debug.DebugKey.PLAYER_DEATH, "[WMIC] Unmarked weapon from drops: " + item.getType());
            }
        }

        // if keepInventory is enabled, also check the player's inventory
        if (Boolean.TRUE.equals(player.getWorld().getGameRuleValue(org.bukkit.GameRule.KEEP_INVENTORY))) {
            for (ItemStack item : player.getInventory().getContents()) {
                if (item == null) continue; // skip null slots

                if (inventoryManager.isWeaponMarked(item)) {
                    WMIC.api().unmark(item, UnmarkCause.DEATH_KEEP_INVENTORY);
                    Debug.log(plugin, Debug.DebugKey.PLAYER_DEATH, "[WMIC] Unmarked weapon in inventory (keepInventory enabled): " + item.getType());
                }
            }
        }
        player.sendMessage("§c(!) All marked weapons have been unmarked due to your death.");
    }

    // WEIGHT
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        plugin.getWeightManager().clear(e.getEntity().getUniqueId());
    }
}