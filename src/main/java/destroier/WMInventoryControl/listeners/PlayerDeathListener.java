package destroier.WMInventoryControl.listeners;

import destroier.WMInventoryControl.WMInventoryControl;
import destroier.WMInventoryControl.managers.InventoryManager;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;

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
            plugin.getLogger().info("Player " + player.getName() + " has died. Checking for marked weapons...");
        }

        // Unmark all weapons in the player's drops
        Iterator<ItemStack> iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            ItemStack item = iterator.next();
            if (inventoryManager.isWeaponMarked(item)) {
                inventoryManager.unmarkWeapon(item);
                if (config.getBoolean("debug-mode")) {
                    plugin.getLogger().info("Unmarked weapon from drops: " + item.getType());
                }
            }
        }

        // If keepInventory is enabled, also check the player's inventory
        if (player.hasPermission("wm.keepinventory") || player.getWorld().getGameRuleValue(org.bukkit.GameRule.KEEP_INVENTORY)) {
            for (ItemStack item : player.getInventory().getContents()) {
                if (inventoryManager.isWeaponMarked(item)) {
                    inventoryManager.unmarkWeapon(item);
                    if (config.getBoolean("debug-mode")) {
                        plugin.getLogger().info("Unmarked weapon in inventory (keepInventory enabled): " + item.getType());
                    }
                }
            }
        }

        player.sendMessage(ChatColor.RED + "(!) All marked weapons have been unmarked due to your death.");
    }
}