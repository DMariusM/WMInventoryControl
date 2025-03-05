package destroier.WMInventoryControl.listeners;

import destroier.WMInventoryControl.WMInventoryControl;
import destroier.WMInventoryControl.managers.InventoryManager;
import destroier.WMInventoryControl.managers.ConfigManager;
import me.deecaad.weaponmechanics.weapon.weaponevents.WeaponPreShootEvent;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class WeaponShootListener implements Listener {

    private final WMInventoryControl plugin;
    private final InventoryManager inventoryManager;
    private final ConfigManager configManager;

    public WeaponShootListener(WMInventoryControl plugin) {
        this.plugin = plugin;
        this.inventoryManager = plugin.getInventoryManager();
        this.configManager = plugin.getConfigManager();
    }

    @EventHandler
    public void onWeaponShoot(WeaponPreShootEvent event) {
        FileConfiguration config = plugin.getConfig();

        if (!(event.getShooter() instanceof Player player)) {
            return;
        }

        String weaponTitle = event.getWeaponTitle();
        EquipmentSlot hand = event.getHand(); // Get the hand being used

        // Detect whether the player is shooting from main hand or off-hand
        boolean isMainHand = (hand == EquipmentSlot.HAND);

        ItemStack heldItem = isMainHand ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInOffHand();

        // Debug: Print raw weapon title
        if (config.getBoolean("debug-mode")) {
            plugin.getLogger().info("Weapon shoot event triggered: " + weaponTitle);
        }

        // Check if weapon is configured
        if (!configManager.isWeaponConfigured(weaponTitle)) {
            return;
        }

        if (heldItem.getAmount() > 1) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "(!) You cannot mark stacked items! You need to mark them one at a time.");
            return;
        }

        int markedWeaponCount = inventoryManager.countMarkedWeapons(player);
        int weaponLimit = configManager.getWeaponLimit(weaponTitle);

        if (config.getBoolean("debug-mode")) {
            plugin.getLogger().info("Player " + player.getName() + " has " + markedWeaponCount + " marked weapons. Limit: " + weaponLimit);
        }

        // Check if weapon is marked
        boolean isMarked = inventoryManager.isWeaponMarked(heldItem);
        if (!isMarked) {
            if (markedWeaponCount <= weaponLimit) {
                inventoryManager.markWeapon(heldItem); // Mark the weapon if within the limit
                if (config.getBoolean("debug-mode")) {
                    plugin.getLogger().info("Successfully marked weapon: " + weaponTitle);
                }
            } else {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "(!) You cannot use or mark this weapon as it exceeds the allowed limit.");
            }
        }
    }
}