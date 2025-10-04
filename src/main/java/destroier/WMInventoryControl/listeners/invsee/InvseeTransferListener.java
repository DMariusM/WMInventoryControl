package destroier.WMInventoryControl.listeners.invsee;

import destroier.WMInventoryControl.WMInventoryControl;
import destroier.WMInventoryControl.api.WMIC;
import destroier.WMInventoryControl.api.types.UnmarkCause;
import destroier.WMInventoryControl.debug.Debug;
import destroier.WMInventoryControl.debug.Debug.DebugKey;
import destroier.WMInventoryControl.managers.InventoryManager;
import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
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

/**
 * Ensures marked items are unmarked when transferred via /invsee.
 *
 * <p>Covers both directions: admin → target (pre, unmark before landing) and
 * target → admin (post, unmark after taking). Includes fallbacks for stack
 * merging, number-key swaps, and dragging using lightweight PDC tags.</p>
 */
public class InvseeTransferListener implements Listener {

    // Admins can mark weapons and place them into the target’s inventory via /invsee
    // We handle BOTH directions:
    //  - PRE (HIGH): unmark items leaving admin -> target (so they land clean)
    //  - POST (MONITOR): unmark items taken from target -> admin

    private final WMInventoryControl plugin;
    private final InventoryManager inventoryManager;

    public InvseeTransferListener(WMInventoryControl plugin) {
        this.plugin = plugin;
        this.inventoryManager = plugin.getInventoryManager();
    }

    private static boolean shouldIgnoreInvseeContext(InventoryView view, Player whoClicked) {
        Inventory top = view.getTopInventory();
        if (!(top.getHolder() instanceof Player target)) return true; // not an /invsee view
        return target.getUniqueId().equals(whoClicked.getUniqueId()); // true only when admin is viewing someone else
    }

    private static boolean clickedIsTargetsInventory(Inventory clicked, InventoryView view) {
        if (clicked == null) return false;
        return clicked.getHolder() == view.getTopInventory().getHolder();
    }

    /*
       PRE: admin -> target (give)
       Run BEFORE Bukkit applies the move so the item lands unmarked.
    */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInvseeGivePre(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player admin)) return;
        if (shouldIgnoreInvseeContext(e.getView(), admin)) return;

        final Inventory clickedInv = e.getClickedInventory();
        final Inventory bottom     = e.getView().getBottomInventory(); // admin inv

        final ItemStack cursor = e.getCursor();

        // SHIFT-CLICK from admin inv into target
        if (e.isShiftClick() && clickedInv != null && clickedInv.equals(bottom)) {
            Debug.log(plugin, DebugKey.INVSEE_TRANSFER,
                    "PRE give (shift): action=" + e.getAction()
                            + " click=" + e.getClick()
                            + " rawSlot=" + e.getRawSlot()
                            + " topSize=" + e.getView().getTopInventory().getSize()
                            + " bottomSize=" + bottom.getSize());

            // 1) Tag all marked items that are ALREADY in target before the move
            final Inventory top = e.getView().getTopInventory();
            tagExistingMarkedInTarget(top);

            // 2) After Bukkit does the move, unmark only the NEW ones (those without the snapshot tag)
            Bukkit.getScheduler().runTask(plugin, () -> {
                unmarkNewlyLandedInTarget(top, (Player) e.getWhoClicked());
                clearSnapshotTagsInTarget(top);
            });
            return;
        }

        // Cursor place into target (LEFT/RIGHT or SWAP_WITH_CURSOR)
        if (clickedIsTargetsInventory(clickedInv, e.getView())) {
            if (e.getClick() == ClickType.LEFT || e.getClick() == ClickType.RIGHT
                    || e.getAction() == InventoryAction.SWAP_WITH_CURSOR) {

                Debug.log(plugin, DebugKey.INVSEE_TRANSFER,
                        "PRE give (place): action=" + e.getAction()
                                + " click=" + e.getClick()
                                + " slot=" + e.getSlot()
                                + " cursor=" + cursor.getType().name());

                if (inventoryManager.isWeaponMarked(cursor)) {
                    unmarkWithNotice(admin, cursor); // unmark BEFORE landing in target
                }
            }

            // NUMBER KEY into target slot (admin hotbar -> target)
            if (e.getClick() == ClickType.NUMBER_KEY) {
                int hb = e.getHotbarButton();
                if (hb >= 0) {
                    ItemStack hot = admin.getInventory().getItem(hb);
                    Debug.log(plugin, DebugKey.INVSEE_TRANSFER,
                            "PRE give (number): hotbar=" + hb
                                    + " action=" + e.getAction()
                                    + " item=" + (hot == null ? "null" : hot.getType().name()));
                    if (hot != null && inventoryManager.isWeaponMarked(hot)) {
                        unmarkWithNotice(admin, hot); // unmark BEFORE swap occurs
                    }
                }
            }
        }
    }

    private NamespacedKey snapKey() {
        return new NamespacedKey(plugin, "wmic_snap_before");
    }

    private void tagExistingMarkedInTarget(Inventory top) {
        for (int i = 0; i < top.getSize(); i++) {
            ItemStack it = top.getItem(i);
            if (it == null || !inventoryManager.isWeaponMarked(it)) continue;
            ItemMeta meta = it.getItemMeta();
            if (meta == null) continue;
            meta.getPersistentDataContainer().set(snapKey(), PersistentDataType.BOOLEAN, true);
            it.setItemMeta(meta);
        }
    }

    private void unmarkNewlyLandedInTarget(Inventory top, Player notifier) {
        for (int i = 0; i < top.getSize(); i++) {
            ItemStack it = top.getItem(i);
            if (it == null) continue;

            ItemMeta meta = it.getItemMeta();
            PersistentDataContainer pdc = (meta == null) ? null : meta.getPersistentDataContainer();
            boolean hadSnapshot = pdc != null && pdc.has(snapKey(), PersistentDataType.BOOLEAN);

            // Newly landed == is marked but was NOT part of the before-snapshot
            if (inventoryManager.isWeaponMarked(it) && !hadSnapshot) {
                String title = WeaponMechanicsAPI.getWeaponTitle(it);
                WMIC.api().unmark(notifier, it, UnmarkCause.INVSEE_TRANSFER);
                if (notifier != null) {
                    notifier.sendMessage("§c(!) The " + (title == null ? "weapon" : title) + " §cwas unmarked.");
                }
                Debug.log(plugin, DebugKey.INVSEE_TRANSFER,
                        "Unmarked newly-landed item in target inv: " + (title == null ? "unknown" : title));
            }
        }
    }

    private void clearSnapshotTagsInTarget(Inventory top) {
        for (int i = 0; i < top.getSize(); i++) {
            ItemStack it = top.getItem(i);
            if (it == null) continue;
            ItemMeta meta = it.getItemMeta();
            if (meta == null) continue;
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (pdc.has(snapKey(), PersistentDataType.BOOLEAN)) {
                pdc.remove(snapKey());
                it.setItemMeta(meta);
            }
        }
    }

    /*
       POST: target -> admin (take)
    */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInvseeClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player admin)) return;
        if (shouldIgnoreInvseeContext(e.getView(), admin)) return;

        Inventory clickedInv = e.getClickedInventory();
        ItemStack current = e.getCurrentItem();

        // Only care about clicks inside the target's inventory for 'take' flows
        if (!clickedIsTargetsInventory(clickedInv, e.getView())) return;

        switch (e.getAction()) {
            case MOVE_TO_OTHER_INVENTORY:
                if (current != null && inventoryManager.isWeaponMarked(current)) {
                    final Player viewer = admin;
                    final Inventory topInv = e.getView().getTopInventory();
                    final int slot = e.getSlot();

                    // remember the title for a fallback if tag is lost due to stack merging
                    final String weaponTitle = WeaponMechanicsAPI.getWeaponTitle(current);

                    // add a unique transfer tag on the source item (still in target’s inv)
                    final String transferId = java.util.UUID.randomUUID().toString();
                    addTransferTag(current, transferId);

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        // 1) Happy-path: find the *exact* item by transfer tag in the taker's inv
                        for (int i = 0; i < viewer.getInventory().getSize(); i++) {
                            ItemStack s = viewer.getInventory().getItem(i);
                            if (hasTransferTag(s, transferId)) {
                                unmarkWithNotice(viewer, s);
                                removeTransferTag(s);
                                return;
                            }
                        }

                        // 2) Fallback: If tag wasn't found (likely merged), unmark the first
                        //    marked item with the same WeaponMechanics title in the taker's inv.
                        if (weaponTitle != null) {
                            for (int i = 0; i < viewer.getInventory().getSize(); i++) {
                                ItemStack s = viewer.getInventory().getItem(i);
                                if (s != null && inventoryManager.isWeaponMarked(s)) {
                                    String t = WeaponMechanicsAPI.getWeaponTitle(s);
                                    if (t != null && t.equalsIgnoreCase(weaponTitle)) {
                                        unmarkWithNotice(viewer, s);
                                        // no transfer tag to remove in this path
                                        return;
                                    }
                                }
                            }
                        }

                        // 3) If we didn’t find it in taker inv, the move probably failed.
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
            case PICKUP_SOME:
                if (current != null && inventoryManager.isWeaponMarked(current)) {
                    unmarkWithNotice(admin, current);
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    ItemStack cursorItem = admin.getItemOnCursor();
                    if (inventoryManager.isWeaponMarked(cursorItem)) {
                        unmarkWithNotice(admin, cursorItem);
                    }
                });
                break;

            case SWAP_WITH_CURSOR:
            case HOTBAR_SWAP:
            case COLLECT_TO_CURSOR:
                Bukkit.getScheduler().runTask(plugin, () -> {
                    ItemStack cur = admin.getItemOnCursor();
                    if (inventoryManager.isWeaponMarked(cur)) {
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
            Bukkit.getScheduler().runTask(plugin, () -> {
                int hb = e.getHotbarButton();
                if (hb >= 0) {
                    ItemStack hot = admin.getInventory().getItem(hb);
                    if (hot != null && inventoryManager.isWeaponMarked(hot)) {
                        unmarkWithNotice(admin, hot);
                    }
                }
                ItemStack cursorItem = admin.getItemOnCursor();
                if (inventoryManager.isWeaponMarked(cursorItem)) {
                    unmarkWithNotice(admin, cursorItem);
                }
            });
        }

        Debug.log(plugin, DebugKey.INVSEE_TRANSFER,
                "Invsee transfer check ran for " + admin.getName() + " action=" + e.getAction());
    }

    // (PRE: target -> admin)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInvseeTakePre(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player admin)) return;
        if (shouldIgnoreInvseeContext(e.getView(), admin)) return;

        // We only care about SHIFT-click (MOVE_TO_OTHER_INVENTORY) coming FROM the target (top inv)
        if (e.getAction() != InventoryAction.MOVE_TO_OTHER_INVENTORY) return;
        Inventory clickedInv = e.getClickedInventory();
        if (!clickedIsTargetsInventory(clickedInv, e.getView())) return;

        ItemStack current = e.getCurrentItem();
        if (current != null && inventoryManager.isWeaponMarked(current)) {
            Debug.log(plugin, DebugKey.INVSEE_TRANSFER,
                    "PRE take (shift-click): unmarking before move; slot=" + e.getSlot());

            // Unmark the item *in place* before Bukkit moves it into the admin's inventory.
            unmarkWithNotice(admin, current);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInvseeDragPre(InventoryDragEvent e) {
        // PRE: admin dragging into target — unmark cursor before it lands
        if (!(e.getWhoClicked() instanceof Player admin)) return;
        if (shouldIgnoreInvseeContext(e.getView(), admin)) return;

        int topSize = e.getView().getTopInventory().getSize();
        boolean affectsTop = e.getRawSlots().stream().anyMatch(raw -> raw < topSize);
        if (!affectsTop) return;

        ItemStack oldCursor = e.getOldCursor();
        if (inventoryManager.isWeaponMarked(oldCursor)) {
            unmarkWithNotice(admin, oldCursor);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInvseeDrag(InventoryDragEvent e) {
        // POST: taking via drag — keep your original cleanup
        if (!(e.getWhoClicked() instanceof Player admin)) return;
        if (shouldIgnoreInvseeContext(e.getView(), admin)) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack cursor = admin.getItemOnCursor();
            if (inventoryManager.isWeaponMarked(cursor)) {
                unmarkWithNotice(admin, cursor);
            }
        });
    }

    private void unmarkWithNotice(Player taker, ItemStack item) {
        if (item != null && inventoryManager.isWeaponMarked(item)) {
            String weaponName = WeaponMechanicsAPI.getWeaponTitle(item);
            WMIC.api().unmark(taker, item, UnmarkCause.INVSEE_TRANSFER);
            if (Debug.enabled(DebugKey.INVSEE_TRANSFER)) {
                taker.sendMessage("§c(!) The " + weaponName + " §cwas unmarked.");
            }
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