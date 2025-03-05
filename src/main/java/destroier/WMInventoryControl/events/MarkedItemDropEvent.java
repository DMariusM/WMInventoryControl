package destroier.WMInventoryControl.events;

import destroier.WMInventoryControl.WMInventoryControl;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;


public class MarkedItemDropEvent implements Listener {
    private final WMInventoryControl plugin;

    public MarkedItemDropEvent(WMInventoryControl plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack droppedItem = event.getItemDrop().getItemStack();
        FileConfiguration config = plugin.getConfig();

        if (!plugin.getInventoryManager().isWeaponMarked(droppedItem)) {
            return;
        }

        if (config.getBoolean("debug-mode")) {
            plugin.getLogger().info("Successfully triggered the drop event!");
        }

        if (event.isCancelled()) {
            plugin.getLogger().info("Drop event was cancelled by another plugin! Skipping unmarking.");
            return;
        }

        if (droppedItem.getType() == Material.AIR) {
            return;
        }

        if (plugin.getInventoryManager().isWeaponMarked(droppedItem)) {
            plugin.getInventoryManager().unmarkWeapon(droppedItem);
            event.getPlayer().sendMessage("§a" + "You have unmarked this weapon.");
        }
    }
}