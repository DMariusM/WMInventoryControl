package destroier.WMInventoryControl.listeners;

import destroier.WMInventoryControl.WMInventoryControl;
import destroier.WMInventoryControl.managers.InventoryManager;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class ItemFrameListener implements Listener {

    private final WMInventoryControl plugin;
    private final InventoryManager inventoryManager;

    public ItemFrameListener(WMInventoryControl plugin) {
        this.plugin = plugin;
        this.inventoryManager = plugin.getInventoryManager();
    }

    @EventHandler
    public void onPlayerInteractWithItemFrame(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame)) {
            return; // Ignore non-item frame interactions
        }

        // Ignore interactions from off-hand (prevents double event firing)
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        FileConfiguration config = plugin.getConfig();

        if (heldItem.getType() == Material.AIR) {
            return;
        }

        if (inventoryManager.isWeaponMarked(heldItem)) {
            inventoryManager.unmarkWeapon(heldItem);
            player.sendMessage("§e(!) Your weapon has been unmarked because it was placed in an Item Frame.");

            if (config.getBoolean("debug-mode")) {
                plugin.getLogger().info("[WMIC] Unmarked weapon placed in an Item Frame: " + heldItem.getType());
            }
        }
    }
}