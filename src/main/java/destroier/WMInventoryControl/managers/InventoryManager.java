package destroier.WMInventoryControl.managers;

import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import destroier.WMInventoryControl.WMInventoryControl;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class InventoryManager {
    private final WMInventoryControl plugin;

    public InventoryManager(WMInventoryControl plugin) {
        this.plugin = plugin;
    }

    /**
     * Marks a weapon by adding an NBT tag.
     */
    public void markWeapon(ItemStack item) {
        if (item == null) return;

        FileConfiguration config = plugin.getConfig();
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return;

        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(new NamespacedKey(plugin, "WM_Marked"), PersistentDataType.BOOLEAN, true);
        data.set(new NamespacedKey(plugin, "WM_Inventory_Drop"), PersistentDataType.BOOLEAN, true);

        item.setItemMeta(meta); // Save changes

        if (config.getBoolean("debug-mode")) {
            plugin.getLogger().info("[WMIC] Successfully marked weapon: " + item.getType());
        }
    }

    /**
     * Checks if a weapon is marked using NBT.
     */
    public boolean isWeaponMarked(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        boolean marked = data.has(new NamespacedKey(plugin, "WM_Marked"), PersistentDataType.BOOLEAN);

        if (plugin.getConfig().getBoolean("debug-mode")) {
            plugin.getLogger().info("[WMIC] Checking weapon: " + item.getType() + " | Is Marked: " + marked);
        }

        return marked;
    }

    public boolean isInventoryMarked(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        boolean marked = data.has(new NamespacedKey(plugin, "WM_Inventory_Drop"), PersistentDataType.BOOLEAN);

        if (plugin.getConfig().getBoolean("debug-mode")) {
            plugin.getLogger().info("[WMIC] Checking weapon: " + item.getType() + " | Is Inventory Marked: " + marked);
        }
        return marked;
    }

    /**
     * Unmarks a weapon by removing the NBT tag.
     */
    public void unmarkWeapon(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;

        FileConfiguration config = plugin.getConfig();
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();

        // Log all existing PDC keys before removal
        if(plugin.getConfig().getBoolean("debug-mode")) {
            plugin.getLogger().info("[WMIC] Checking PDC tags before unmarking: " + item.getType());
        }

        for (NamespacedKey key : data.getKeys()) {
            if(plugin.getConfig().getBoolean("debug-mode")) {
                plugin.getLogger().info("    ➜ Found PDC tag: " + key.getKey());
            }
        }

        // Remove custom tags
        data.remove(new NamespacedKey(plugin, "WM_Marked"));
        data.remove(new NamespacedKey(plugin, "WM_Inventory_Drop"));

        item.setItemMeta(meta); // Save changes

        if (config.getBoolean("debug-mode")) {
            plugin.getLogger().info("Unmarked weapon: " + item.getType());
        }
    }

    /**
     * Counts the number of marked weapons a player has.
     */
    public int countMarkedWeapons(Player player, String weaponTitle) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isWeaponMarked(item)) {
                // only count if this marked item’s WeaponMechanics title matches
                String title = WeaponMechanicsAPI.getWeaponTitle(item);
                if (title != null && title.equalsIgnoreCase(weaponTitle)) {
                    count++;
                }
            }
        }

        if (plugin.getConfig().getBoolean("debug-mode")) {
            plugin.getLogger().info("[WMIC] Marked “" + weaponTitle + "” count for "
                    + player.getName() + ": " + count);
        }

        return count;
    }
}