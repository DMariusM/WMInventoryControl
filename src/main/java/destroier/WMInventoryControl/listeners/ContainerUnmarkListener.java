package destroier.WMInventoryControl.listeners;

import destroier.WMInventoryControl.WMInventoryControl;
import destroier.WMInventoryControl.managers.InventoryManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

public class ContainerUnmarkListener implements Listener {

    private final WMInventoryControl plugin;
    private final InventoryManager inventoryManager;

    // List of container types that should unmark weapons
    private final List<InventoryType> containerTypes = Arrays.asList(
            InventoryType.CHEST,
            InventoryType.BARREL,
            InventoryType.ENDER_CHEST,
            InventoryType.FURNACE,
            InventoryType.BLAST_FURNACE,
            InventoryType.SMOKER,
            InventoryType.BREWING,
            InventoryType.DISPENSER,
            InventoryType.DROPPER,
            InventoryType.WORKBENCH,
            InventoryType.HOPPER,
            InventoryType.CRAFTING,
            InventoryType.ENCHANTING,
            InventoryType.ANVIL,
            InventoryType.GRINDSTONE,
            InventoryType.CARTOGRAPHY,
            InventoryType.LOOM,
            InventoryType.SMITHING,
            InventoryType.STONECUTTER,
            InventoryType.SHULKER_BOX,
            InventoryType.DECORATED_POT
    );

    public boolean isRealContainer(InventoryType type) {
        return containerTypes.contains(type);
    }

    public ContainerUnmarkListener(WMInventoryControl plugin) {
        this.plugin = plugin;
        this.inventoryManager = plugin.getInventoryManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory clickedInventory = event.getClickedInventory();
        Inventory targetInventory = event.getView().getTopInventory(); // The inventory being interacted with
        ItemStack currentItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        if (clickedInventory == null) {
            return;
        }

        boolean isCurrentItemEmpty = (currentItem == null || currentItem.getType().isAir());
        boolean isCursorItemEmpty = cursorItem.getType().isAir(); // Removed redundant null check

        if (isCurrentItemEmpty && isCursorItemEmpty) {
            return;
        }

        // Detect SHIFT-CLICK to move to a container
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            if (containerTypes.contains(targetInventory.getType())) {
                if (!isCurrentItemEmpty && inventoryManager.isWeaponMarked(currentItem)) {
                    inventoryManager.unmarkWeapon(currentItem);
                    player.sendMessage("§e(!) Your weapon has been unmarked because it was moved into a container.");

                    if (plugin.getConfig().getBoolean("debug-mode")) {
                        plugin.getLogger().info("[WMIC] Unmarked weapon moved into: " + targetInventory.getType() + " | Item: " + currentItem.getType());
                    }
                }
            }
        }

        // Detect CLICK AND DRAG PLACEMENT into a container
        if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT) {
            if (clickedInventory.getType() != InventoryType.PLAYER && containerTypes.contains(clickedInventory.getType())) {
                if (!isCursorItemEmpty && inventoryManager.isWeaponMarked(cursorItem)) {
                    inventoryManager.unmarkWeapon(cursorItem);
                    player.sendMessage("§e(!) Your weapon has been unmarked because it was placed in a container.");

                    if (plugin.getConfig().getBoolean("debug-mode")) {
                        plugin.getLogger().info("[WMIC] Unmarked weapon placed into: " + clickedInventory.getType() + " | Item: " + cursorItem.getType());
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClickContainer(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        Inventory clickedInventory = event.getClickedInventory();
        Inventory topInventory = event.getView().getTopInventory(); // The "main" inventory being interacted with

        if (clickedInventory == null || clickedItem == null || clickedItem.getType().isAir()) {
            return;
        }

        InventoryType clickedType = clickedInventory.getType();
        InventoryType topType = topInventory.getType();

        if (clickedType == InventoryType.PLAYER || topType == InventoryType.PLAYER || topType == InventoryType.CRAFTING) {
            return; // Allow normal inventory interactions
        }

        // we check if the container is among the list of minecraft containers if not it means it's an Inventory GUI
        if (!isRealContainer(topType)) {
            if (plugin.getInventoryManager().isWeaponMarked(clickedItem)) {
                plugin.getInventoryManager().unmarkWeapon(clickedItem);
                player.sendMessage("§e(!) You have unmarked a weapon by placing it in a GUI-based inventory!");

                if (plugin.getConfig().getBoolean("debug-mode")) {
                    plugin.getLogger().info("[WMIC] Player " + player.getName() + " unmarked a marked weapon by placing it in: " + topType + " inventory GUI");
                }
            }
        }
    }

    @EventHandler
    public void onHotbarSwap(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory topInventory = event.getView().getTopInventory(); // The inventory being interacted with
        int hotbarButton = event.getHotbarButton();

        // check if this is a hotbar number key swap
        if (event.getClick() == ClickType.NUMBER_KEY && hotbarButton >= 0 && hotbarButton <= 8) {
            ItemStack hotbarItem = player.getInventory().getItem(hotbarButton);
            ItemStack inventoryItem = event.getCurrentItem();

            if (hotbarItem != null && inventoryManager.isWeaponMarked(hotbarItem)) {
                // If the item is moved into a non-player container, unmark it
                if (topInventory.getHolder() != player) { // Removed null check
                    inventoryManager.unmarkWeapon(hotbarItem);
                    player.sendMessage("§e(!) Your marked weapon was unmarked after swapping it into a container.");
                    if (plugin.getConfig().getBoolean("debug-mode")) {
                        plugin.getLogger().info("[WMIC] Unmarked weapon after hotbar swap into a container: " + hotbarItem.getType());
                    }
                }
            }

            // check if the item being swapped into the hotbar was marked
            if (inventoryItem != null && inventoryManager.isWeaponMarked(inventoryItem)) {
                if (plugin.getConfig().getBoolean("debug-mode")) {
                    plugin.getLogger().info("[WMIC] Hotbar swap detected, keeping marking intact for: " + inventoryItem.getType());
                }
            }
        }
    }
}