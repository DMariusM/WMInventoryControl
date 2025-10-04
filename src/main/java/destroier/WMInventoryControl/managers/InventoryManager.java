package destroier.WMInventoryControl.managers;

import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import destroier.WMInventoryControl.WMInventoryControl;
import destroier.WMInventoryControl.debug.Debug;
import destroier.WMInventoryControl.debug.Debug.DebugKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handles marking/unmarking of items using PersistentDataContainer (PDC).
 * Features:
 * - Mark/Unmark helpers for WM items (adds/removes custom PDC flags).
 * - Queries to detect "marked" or "inventory-drop-marked" items.
 * - Utility to count marked weapons by WeaponMechanics title in a player's inv.
 */
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

        ItemMeta meta = item.getItemMeta();

        if (meta == null) return;

        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(new NamespacedKey(plugin, "WM_Marked"), PersistentDataType.BOOLEAN, true);
        data.set(new NamespacedKey(plugin, "WM_Inventory_Drop"), PersistentDataType.BOOLEAN, true);

        item.setItemMeta(meta); // Save changes

        Debug.log(plugin, DebugKey.INVENTORY_MANAGER, "Successfully marked weapon: " + item.getType());
    }

    /**
     * Checks if a weapon is marked using NBT.
     */
    public boolean isWeaponMarked(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        boolean marked = data.has(new NamespacedKey(plugin, "WM_Marked"), PersistentDataType.BOOLEAN);

        Debug.log(plugin, DebugKey.INVENTORY_MANAGER,
                "Checking weapon: " + item.getType() + " | Is Marked: " + marked);

        return marked;
    }

    public boolean isInventoryMarked(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        boolean marked = data.has(new NamespacedKey(plugin, "WM_Inventory_Drop"), PersistentDataType.BOOLEAN);

        Debug.log(plugin, DebugKey.INVENTORY_MANAGER,
                "Checking weapon: " + item.getType() + " | Is Inventory Marked: " + marked);

        return marked;
    }

    /**
     * Unmarks a weapon by removing the NBT tag.
     */
    public void unmarkWeapon(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();

        // Log all existing PDC keys before removal
        Debug.log(plugin, DebugKey.INVENTORY_MANAGER,
                "Checking PDC tags before unmarking: " + item.getType());

        for (NamespacedKey key : data.getKeys()) {
            Debug.log(plugin, DebugKey.INVENTORY_MANAGER, "    -> Found PDC tag: " + key.getKey());
        }

        // Remove custom tags
        data.remove(new NamespacedKey(plugin, "WM_Marked"));
        data.remove(new NamespacedKey(plugin, "WM_Inventory_Drop"));

        item.setItemMeta(meta); // Save changes

        Debug.log(plugin, DebugKey.INVENTORY_MANAGER, "Unmarked weapon: " + item.getType());
    }

    /**
     * Counts the number of marked weapons a player has.
     */
    public int countMarkedWeapons(Player player, String weaponTitle) {
        int count = 0;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType().isAir()) continue;      // guard
            if (!isWeaponMarked(item)) continue; // cheap fast-path

            String title = WeaponMechanicsAPI.getWeaponTitle(item); // may be null for non-WM items
            if (title != null && title.equalsIgnoreCase(weaponTitle)) {
                count++;
            }
        }

        Debug.log(plugin, DebugKey.INVENTORY_MANAGER,
                "Marked \"" + weaponTitle + "\" count for " + player.getName() + ": " + count);

        return count;
    }
}