package destroier.WMInventoryControl.listeners.containers;

import destroier.WMInventoryControl.WMInventoryControl;
import destroier.WMInventoryControl.api.WMIC;
import destroier.WMInventoryControl.api.types.UnmarkCause;
import destroier.WMInventoryControl.debug.Debug;
import destroier.WMInventoryControl.debug.Debug.DebugKey;
import destroier.WMInventoryControl.managers.InventoryManager;
import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.DecoratedPotInventory;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Special-case handling for containers with unique behavior.
 *
 * <p>Supports Decorated Pots (unmarks on the next tick) and Bundles (blocks inserts
 * and cleans bundles after drags).</p>
 */
public class SpecialContainerListener implements Listener {

    private final WMInventoryControl plugin;
    private final InventoryManager inventoryManager;

    public SpecialContainerListener(WMInventoryControl plugin) {
        this.plugin = plugin;
        this.inventoryManager = plugin.getInventoryManager();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRightClickPot(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.isCancelled()) return;

        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.DECORATED_POT) return;

        final Player p = e.getPlayer();
        final Location loc = b.getLocation();

        Debug.log(plugin, DebugKey.SPECIAL_CONTAINER,
                "[pot] -> schedule check @ " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());

        // one tick later so the insert/remove is finalized
        Bukkit.getScheduler().runTask(plugin, () -> {
            BlockState state = b.getState();
            if (!(state instanceof org.bukkit.block.DecoratedPot pot)) return;

            DecoratedPotInventory inv = pot.getInventory();  // Paper API
            ItemStack inside = inv.getItem();

            // 1) Item now inside the pot
            if (inside != null && inventoryManager.isWeaponMarked(inside)) {
                String title = WeaponMechanicsAPI.getWeaponTitle(inside);
                Debug.log(plugin, DebugKey.SPECIAL_CONTAINER,
                        "[pot] found MARKED inside: type=" + inside.getType() + " title=" + title);
                ItemStack cleaned = inside.clone();
                WMIC.api().unmark(p, cleaned, UnmarkCause.DECORATED_POT);
                inv.setItem(cleaned);
                p.sendMessage("§c(!) The weapon you placed in the decorated pot has been unmarked.");
                return;
            }

            // 2) Item taken from pot -> cursor
            ItemStack cursor = p.getItemOnCursor();
            if (inventoryManager.isWeaponMarked(cursor)) {
                String title = WeaponMechanicsAPI.getWeaponTitle(cursor);
                Debug.log(plugin, DebugKey.SPECIAL_CONTAINER,
                        "[pot] unmark on cursor after take: type=" + cursor.getType() + " title=" + title);
                WMIC.api().unmark(p, cursor, UnmarkCause.DECORATED_POT);
                p.sendMessage("§c(!) The weapon you took from the decorated pot has been unmarked.");
            } else {
                Debug.log(plugin, DebugKey.SPECIAL_CONTAINER, "[pot] no marked items found to clean.");
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBundleInsertGuard(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        ItemStack cursor  = e.getCursor();
        ItemStack current = e.getCurrentItem();

        // 1) placing current into a bundle on cursor
        if (isBundle(cursor) && isMarkedWeapon(current)) {
            Debug.log(plugin, DebugKey.SPECIAL_CONTAINER,
                    "[bundle][guard] cancel current -> cursorBundle | click=" + e.getClick() + " action=" + e.getAction());
            e.setCancelled(true);
            p.sendMessage("§c(!) You cannot place marked weapons into bundles.");
            return;
        }

        // 2) placing cursor into a bundle in the slot
        if (isBundle(current) && isMarkedWeapon(cursor)) {
            Debug.log(plugin, DebugKey.SPECIAL_CONTAINER,
                    "[bundle][guard] cancel cursor -> slotBundle | click=" + e.getClick() + " action=" + e.getAction());
            e.setCancelled(true);
            p.sendMessage("§c(!) You cannot place marked weapons into bundles.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBundleDragGuard(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        // dragging a marked weapon and any destination slot currently holds a bundle -> block
        ItemStack dragged = e.getOldCursor();
        if (!isMarkedWeapon(dragged)) return;

        Inventory inv = e.getInventory();
        for (int slot : e.getInventorySlots()) {
            ItemStack target = inv.getItem(slot);
            if (isBundle(target)) {
                Debug.log(plugin, DebugKey.SPECIAL_CONTAINER,
                        "[bundle][guard] cancel drag into slot=" + slot + " (bundle present)");
                e.setCancelled(true);
                p.sendMessage("§c(!) You cannot drag marked weapons into bundles.");
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBundleDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.isCancelled()) return;

        // after drag resolves, either cursor or one of the placed slots might now be a bundle
        Bukkit.getScheduler().runTask(plugin, () -> {
            Debug.log(plugin, DebugKey.SPECIAL_CONTAINER,
                    "[bundle][cleanup] post-drag sweep | slots=" + e.getInventorySlots().size());
            cleanBundleIfNeeded(p, p.getItemOnCursor());

            Inventory inv = e.getInventory();
            for (int slot : e.getInventorySlots()) {
                cleanBundleIfNeeded(p, inv.getItem(slot));
            }
        });
    }

    private void cleanBundleIfNeeded(Player taker, ItemStack stack) {
        if (!isBundle(stack)) return;
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof BundleMeta bundle)) return;

        List<ItemStack> items = new ArrayList<>(bundle.getItems()); // copy
        int cleaned = 0;

        for (int i = 0; i < items.size(); i++) {
            ItemStack it = items.get(i);
            if (it != null && inventoryManager.isWeaponMarked(it)) {
                String title = WeaponMechanicsAPI.getWeaponTitle(it);
                Debug.log(plugin, DebugKey.SPECIAL_CONTAINER,
                        "[bundle][cleanup] unmark inside bundle: type=" + it.getType() + " title=" + title);
                WMIC.api().unmark(taker, it, UnmarkCause.SPECIAL_CONTAINER);
                items.set(i, it);
                cleaned++;
            }
        }

        if (cleaned > 0) {
            bundle.setItems(items);
            stack.setItemMeta(bundle);
            taker.sendMessage("§c(!) A marked weapon inside your bundle has been unmarked.");
        } else {
            Debug.log(plugin, DebugKey.SPECIAL_CONTAINER, "[bundle][cleanup] no marked items found in bundle.");
        }
    }

    private boolean isBundle(ItemStack s) {
        return s != null && s.getType() == Material.BUNDLE;
    }

    private boolean isMarkedWeapon(ItemStack s) {
        return s != null && inventoryManager.isWeaponMarked(s);
    }
}