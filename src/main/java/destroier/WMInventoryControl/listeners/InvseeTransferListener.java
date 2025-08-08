package destroier.WMInventoryControl.listeners;

import destroier.WMInventoryControl.WMInventoryControl;
import destroier.WMInventoryControl.managers.InventoryManager;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class InvseeTransferListener implements Listener {

    private final WMInventoryControl plugin;
    private final InventoryManager inventoryManager;

    public InvseeTransferListener(WMInventoryControl plugin) {
        this.plugin = plugin;
        this.inventoryManager = plugin.getInventoryManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private boolean isViewingOtherPlayersInv(InventoryView view, Player whoClicked) {
        Inventory top = view.getTopInventory();
        if (top == null) return false;
        return top.getHolder() instanceof Player target
                && !target.getUniqueId().equals(whoClicked.getUniqueId());
    }

    private boolean clickedIsTargetsInventory(Inventory clicked, InventoryView view) {
        if (clicked == null) return false;
        Inventory top = view.getTopInventory();
        if (top == null) return false;
        return clicked.getHolder() == top.getHolder();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInvseeClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player admin)) return;
        if (!isViewingOtherPlayersInv(e.getView(), admin)) return;

        Inventory clickedInv = e.getClickedInventory();
        ItemStack current = e.getCurrentItem();

        // Only care about clicks inside the *target's* inventory
        if (!clickedIsTargetsInventory(clickedInv, e.getView())) return;

        switch (e.getAction()) {
            case MOVE_TO_OTHER_INVENTORY:
                if (current != null && inventoryManager.isWeaponMarked(current)) {
                    final Player viewer = admin;
                    final Inventory topInv = e.getView().getTopInventory();
                    final int slot = e.getSlot();

                    // --- add a temporary transfer tag on the item in the target's inventory ---
                    final String transferId = java.util.UUID.randomUUID().toString();
                    addTransferTag(current, transferId);

                    // After Bukkit processes the move, find ONLY the item with that tag in admin inv
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        // 1) Try to find it in the taker's inventory and unmark just that one
                        for (int i = 0; i < viewer.getInventory().getSize(); i++) {
                            ItemStack s = viewer.getInventory().getItem(i);
                            if (hasTransferTag(s, transferId)) {
                                // clean: unmark and remove temp tag
                                unmarkWithNotice(viewer, s);
                                removeTransferTag(s);
                                return; // done
                            }
                        }

                        // 2) If we didn’t find it in taker inv, the move probably failed.
                        //    Clean up the temp tag in the original slot so we don't leave junk tags around.
                        ItemStack stillThere = topInv.getItem(slot);
                        if (hasTransferTag(stillThere, transferId)) {
                            removeTransferTag(stillThere);
                        }
                    });
                }
                break;

            case PICKUP_ALL:
            case PICKUP_HALF:
            case PICKUP_ONE:
            case PICKUP_SOME: // pick to cursor
                if (current != null && inventoryManager.isWeaponMarked(current)) {
                    unmarkWithNotice(admin, current);
                }
                // make sure cursor copy is also unmarked after Bukkit finalizes the move
                Bukkit.getScheduler().runTask(plugin, () -> {
                    ItemStack cursorItem = admin.getItemOnCursor();
                    if (cursorItem != null && inventoryManager.isWeaponMarked(cursorItem)) {
                        unmarkWithNotice(admin, cursorItem);
                    }
                });
                break;

            case SWAP_WITH_CURSOR:
            case HOTBAR_SWAP:
            case HOTBAR_MOVE_AND_READD:
            case COLLECT_TO_CURSOR:
                // After the swap, ensure anything that lands on admin's side is unmarked
                Bukkit.getScheduler().runTask(plugin, () -> {
                    ItemStack cur = admin.getItemOnCursor();
                    if (cur != null && inventoryManager.isWeaponMarked(cur)) {
                        unmarkWithNotice(admin, cur);
                    }
                    int hb = e.getHotbarButton();
                    if (hb >= 0) {
                        ItemStack hotbar = admin.getInventory().getItem(hb);
                        if (hotbar != null && inventoryManager.isWeaponMarked(hotbar)) {
                            unmarkWithNotice(admin, hotbar);
                        }
                    }
                });
                break;

            default:
                // no-op
        }

        if (e.getClick() == ClickType.NUMBER_KEY) {
            // Only when swapping with a hotbar key while clicking inside target's inventory
            Bukkit.getScheduler().runTask(plugin, () -> {
                int hb = e.getHotbarButton();
                if (hb >= 0) {
                    ItemStack hot = admin.getInventory().getItem(hb);
                    if (hot != null && inventoryManager.isWeaponMarked(hot)) {
                        unmarkWithNotice(admin, hot); // item that came from target -> admin hotbar
                    }
                }
                ItemStack cursorItem = admin.getItemOnCursor();
                if (cursorItem != null && inventoryManager.isWeaponMarked(cursorItem)) {
                    unmarkWithNotice(admin, cursorItem);
                }
            });
        }

        if (plugin.getConfig().getBoolean("debug-mode")) {
            plugin.getLogger().info("[WMIC] Invsee transfer check ran for " + admin.getName() + " action=" + e.getAction());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInvseeDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player admin)) return;
        if (!isViewingOtherPlayersInv(e.getView(), admin)) return;

        // If the drag picked up from target inventory and leaves item on admin cursor/inventory,
        // we make sure the cursor is clean after the drag completes.
        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack cursor = admin.getItemOnCursor();
            if (inventoryManager.isWeaponMarked(cursor)) {
                unmarkWithNotice(admin, cursor);
            }
        });
    }

    private void unmarkWithNotice(Player taker, ItemStack item) {
        if (item != null && inventoryManager.isWeaponMarked(item)) {
            String weaponName = item.getItemMeta() != null && item.getItemMeta().hasDisplayName()
                    ? item.getItemMeta().getDisplayName()
                    : "weapon";
            inventoryManager.unmarkWeapon(item);
            taker.sendMessage("§c(!) The " + weaponName + " you took from another player has been unmarked.");
        }
    }

    private NamespacedKey transferKey() {
        return new NamespacedKey(plugin, "invsee_transfer");
    }

    private void addTransferTag(ItemStack item, String id) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(transferKey(), PersistentDataType.STRING, id);
        item.setItemMeta(meta);
    }

    private boolean hasTransferTag(ItemStack item, String id) {
        if (item == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String val = pdc.get(transferKey(), PersistentDataType.STRING);
        return id.equals(val);
    }

    private void removeTransferTag(ItemStack item) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().remove(transferKey());
        item.setItemMeta(meta);
    }
}