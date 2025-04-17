package destroier.WMInventoryControl.listeners;

import destroier.WMInventoryControl.WMInventoryControl;
import destroier.WMInventoryControl.managers.InventoryManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerDeathListener implements Listener {

    private final WMInventoryControl plugin;
    private final InventoryManager inventoryManager;

    public PlayerDeathListener(WMInventoryControl plugin) {
        this.plugin = plugin;
        this.inventoryManager = plugin.getInventoryManager();
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        FileConfiguration config = plugin.getConfig();

        if (config.getBoolean("debug-mode")) {
            plugin.getLogger().info("[WMIC] Player " + player.getName() + " has died. Checking for marked weapons...");
        }

        // Unmark all weapons in the player's drops
        for (ItemStack item : event.getDrops()) {
            if (inventoryManager.isWeaponMarked(item)) {
                inventoryManager.unmarkWeapon(item);
                if (config.getBoolean("debug-mode")) {
                    plugin.getLogger().info("[WMIC] Unmarked weapon from drops: " + item.getType());
                }
            }
        }

        // If keepInventory is enabled, also check the player's inventory
        if (player.hasPermission("wm.keepinventory") || Boolean.TRUE.equals(player.getWorld().getGameRuleValue(org.bukkit.GameRule.KEEP_INVENTORY))) {
            for (ItemStack item : player.getInventory().getContents()) {
                if (item == null) continue; // Skip null slots

                if (inventoryManager.isWeaponMarked(item)) {
                    inventoryManager.unmarkWeapon(item);
                    if (config.getBoolean("debug-mode")) {
                        plugin.getLogger().info("[WMIC] Unmarked weapon in inventory (keepInventory enabled): " + item.getType());
                    }
                }
            }
        }
        player.sendMessage("§c(!) All marked weapons have been unmarked due to your death.");
    }
}