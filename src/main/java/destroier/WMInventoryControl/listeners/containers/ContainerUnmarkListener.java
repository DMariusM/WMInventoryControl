package destroier.WMInventoryControl.listeners.containers;

import destroier.WMInventoryControl.WMInventoryControl;
import destroier.WMInventoryControl.api.WMIC;
import destroier.WMInventoryControl.api.types.UnmarkCause;
import destroier.WMInventoryControl.debug.Debug;
import destroier.WMInventoryControl.debug.Debug.DebugKey;
import destroier.WMInventoryControl.managers.ConfigManager;
import destroier.WMInventoryControl.managers.InventoryManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/**
 * Handles inventory interactions with containers.
 *
 * <p>Blocks placing marked weapons into non-accepting UIs (e.g., anvils) and
 * unmarks weapons that successfully enter real storage containers (e.g., chests,
 * barrels, shulkers). Includes guards for cursor placement, shift-clicks,
 * number-key swaps, and combat restrictions.</p>
 */
public class ContainerUnmarkListener implements Listener {

    private final WMInventoryControl plugin;
    private final InventoryManager inventoryManager;
    private final ConfigManager configManager;

    public ContainerUnmarkListener(WMInventoryControl plugin) {
        this.plugin = plugin;
        this.inventoryManager = plugin.getInventoryManager();
        this.configManager = plugin.getConfigManager();
    }

    private boolean isRegularContainer(InventoryType type) {
        return configManager.getRegularContainers().contains(type);
    }

    private boolean isNonAcceptingContainer(InventoryType type) {
        return configManager.getNonAcceptingContainers().contains(type);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory clickedInventory = event.getClickedInventory();
        InventoryView view = event.getView();
        Inventory topInventory = view.getTopInventory();   // “container”/GUI part
        Inventory bottomInventory = view.getBottomInventory(); // player inv
        ItemStack currentItem = event.getCurrentItem();
        ItemStack cursorItem  = event.getCursor();

        var cr = plugin.getCombatRestrictionsManager();
        boolean blockMoveNow = cr != null && cr.isEnabled() && cr.isInCombat(player)
                && plugin.getConfigManager().isBlockMovingMarkedInCombat();

        if (clickedInventory == null) return; // clicked outside or invalid
        if (event.getAction() == InventoryAction.NOTHING) return; // nothing changes

        InventoryType topType = topInventory.getType();
        InventoryType clickedType = clickedInventory.getType();

        boolean isCurrentEmpty = (currentItem == null || currentItem.getType().isAir());
        boolean isCursorEmpty  = cursorItem.getType().isAir();

        // 1) SHIFT-CLICK (move-to-other-inventory)
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {

            // Source must be PLAYER, otherwise the “other” may be the player inv
            boolean fromPlayerInv = clickedInventory.equals(bottomInventory);

            if (fromPlayerInv) {
                // Target is the TOP part of the view
                if (isNonAcceptingContainer(topType) && inventoryManager.isWeaponMarked(currentItem)) {
                    // Don’t unmark, just block the attempt (utility UI rejects it anyway)
                    event.setCancelled(true);
                    if (currentItem != null) {
                        Debug.log(plugin, DebugKey.CONTAINER_BLOCK,
                                "Blocked (shift-click) into: " + topType + " | Item: " + currentItem.getType());
                    }
                    player.sendMessage("§c(!) You cannot place marked weapons into this interface.");
                    return;
                }

                if (isRegularContainer(topType)) {
                    // We allow the move and UNMARK the item being moved.
                    if (!isCurrentEmpty && inventoryManager.isWeaponMarked(currentItem)) {
                        if (blockMoveNow) {
                            event.setCancelled(true);
                            player.updateInventory();
                            player.sendMessage("§cYou can’t move marked weapons while in combat.");
                            return;
                        }
                        WMIC.api().unmark(player, currentItem, UnmarkCause.PUT_IN_CONTAINER);
                        player.sendMessage("§e(!) Your weapon has been unmarked because it was moved into a container.");
                        Debug.log(plugin, DebugKey.CONTAINER_UNMARK,
                                "Unmarked (shift-click) into: " + topType + " | Item: " + currentItem.getType());
                    }
                }
            }

            return; // handled shift-click path
        }

        // 2) LEFT/RIGHT CLICK (place-with-cursor into a slot)
        if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT) {

            // Only care if target is NOT the player inventory
            if (!clickedInventory.equals(bottomInventory)) {
                if (isNonAcceptingContainer(clickedType)) {
                    // If cursor holds a marked weapon and user tries to place it in a NON-ACCEPTING UI -> cancel
                    if (!isCursorEmpty && inventoryManager.isWeaponMarked(cursorItem)) {
                        event.setCancelled(true);
                        Debug.log(plugin, DebugKey.CONTAINER_BLOCK,
                                "Blocked (place) into: " + clickedType + " | Item: " + cursorItem.getType());
                        player.sendMessage("§c(!) You cannot place marked weapons into this interface.");
                        return;
                    }
                } else if (isRegularContainer(clickedType)) {
                    // Real container: if cursor holds a MARKED weapon, UNMARK it
                    if (!isCursorEmpty && inventoryManager.isWeaponMarked(cursorItem)) {
                        if (blockMoveNow) {
                            event.setCancelled(true);
                            player.updateInventory();
                            player.sendMessage("§cYou can’t move marked weapons while in combat.");
                            return;
                        }
                        WMIC.api().unmark(player, cursorItem, UnmarkCause.PUT_IN_CONTAINER);
                        player.sendMessage("§e(!) Your weapon has been unmarked because it was placed in a container.");
                        Debug.log(plugin, DebugKey.CONTAINER_UNMARK,
                                "Unmarked (place) into: " + clickedType + " | Item: " + cursorItem.getType());
                    }
                } else {
                    // Unknown/GUI target: not regular and not in non-accepting.
                    // Intentionally do nothing (allow placement, do NOT unmark).
                    Debug.log(plugin, DebugKey.CONTAINER_BLOCK,
                            "GUI/unknown placement allowed: " + clickedType);
                }
            }

            return; // handled place-with-cursor path
        }

        // 3) NUMBER-KEY (hotbar swap into target slot)
        if (event.getClick() == ClickType.NUMBER_KEY) {
            int hotbar = event.getHotbarButton();
            if (hotbar < 0 || hotbar > 8) return;

            ItemStack hotbarItem = player.getInventory().getItem(hotbar);

            // Only if clicking into TOP inventory (not player inv)
            if (!clickedInventory.equals(bottomInventory)) {
                if (isNonAcceptingContainer(clickedType)) {
                    if (hotbarItem != null && inventoryManager.isWeaponMarked(hotbarItem)) {
                        event.setCancelled(true);
                        Debug.log(plugin, DebugKey.CONTAINER_BLOCK,
                                "Blocked (number-key) into: " + clickedType + " | Item: " + hotbarItem.getType());
                        player.sendMessage("§c(!) You cannot place marked weapons into this interface.");
                    }
                } else if (isRegularContainer(clickedType)) {
                    if (hotbarItem != null && inventoryManager.isWeaponMarked(hotbarItem)) {
                        if (blockMoveNow) {
                            event.setCancelled(true);
                            player.updateInventory();
                            player.sendMessage("§cYou can’t move marked weapons while in combat.");
                            return;
                        }
                        WMIC.api().unmark(player, hotbarItem, UnmarkCause.PUT_IN_CONTAINER);
                        player.sendMessage("§e(!) Your marked weapon was unmarked after swapping it into a container.");
                        Debug.log(plugin, DebugKey.CONTAINER_UNMARK,
                                "Unmarked (number-key) into: " + clickedType + " | Item: " + hotbarItem.getType());
                    }
                }
            }
        }
    }

    // Combat guard from your original file is left as-is (no changes)
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClickPrevent(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        var cr = plugin.getCombatRestrictionsManager();
        boolean combatMoveBlock = cr != null && cr.isEnabled() && cr.isInCombat(p)
                && plugin.getConfigManager().isBlockMovingMarkedInCombat();
        if (!combatMoveBlock) return;

        // Picking up a MARKED item from player inventory while in combat -> block
        if (e.getClickedInventory() != null && e.getClickedInventory().equals(e.getView().getBottomInventory())) {
            ItemStack current = e.getCurrentItem();
            if (current != null && !current.getType().isAir() && plugin.getInventoryManager().isWeaponMarked(current)) {
                InventoryAction a = e.getAction();
                boolean picksUp =
                        a == InventoryAction.PICKUP_ALL
                                || a == InventoryAction.PICKUP_HALF
                                || a == InventoryAction.PICKUP_SOME
                                || a == InventoryAction.PICKUP_ONE
                                || a == InventoryAction.SWAP_WITH_CURSOR
                                || e.getClick() == ClickType.SWAP_OFFHAND
                                || e.getClick() == ClickType.DOUBLE_CLICK;

                if (picksUp) {
                    e.setCancelled(true);
                    p.updateInventory();
                    p.sendMessage("§cYou can’t move marked weapons while in combat.");
                    return;
                }
            }
        }

        // Safety: clicking OUTSIDE with a marked cursor item (void case)
        ItemStack cursor = e.getCursor();
        boolean outsideClick = (e.getClickedInventory() == null);
        if (outsideClick && !cursor.getType().isAir() && plugin.getInventoryManager().isWeaponMarked(cursor)) {
            e.setCancelled(true);
            p.updateInventory();
            p.sendMessage("§cYou can’t drop marked weapons while in combat.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShiftMoveWhileCombat(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        var cr = plugin.getCombatRestrictionsManager();
        boolean combatMoveBlock = cr != null && cr.isEnabled() && cr.isInCombat(p)
                && plugin.getConfigManager().isBlockMovingMarkedInCombat();
        if (!combatMoveBlock) return;

        if (e.getAction() != InventoryAction.MOVE_TO_OTHER_INVENTORY) return;
        if (e.getClickedInventory() == null || e.getClickedInventory().getType() != InventoryType.PLAYER) return;

        ItemStack current = e.getCurrentItem();
        if (current == null || current.getType().isAir() || !inventoryManager.isWeaponMarked(current)) return;

        Inventory top = e.getView().getTopInventory();
        if (isRegularContainer(top.getType())) {
            e.setCancelled(true);
            p.updateInventory();
            p.sendMessage("§cYou can’t move marked weapons into containers while in combat.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        var cr = plugin.getCombatRestrictionsManager();
        boolean combatMoveBlock = cr != null && cr.isEnabled() && cr.isInCombat(p)
                && plugin.getConfigManager().isBlockMovingMarkedInCombat();
        if (!combatMoveBlock) return;

        ItemStack oldCursor = e.getOldCursor();
        if (!oldCursor.getType().isAir() && plugin.getInventoryManager().isWeaponMarked(oldCursor)) {
            e.setCancelled(true);
            p.updateInventory();
            p.sendMessage("§cYou can’t drag marked weapons while in combat.");
        }
    }
}