package destroier.WMInventoryControl.listeners.weapons;

import destroier.WMInventoryControl.WMInventoryControl;
import destroier.WMInventoryControl.api.WMIC;
import destroier.WMInventoryControl.api.types.DenyReason;
import destroier.WMInventoryControl.debug.Debug;
import destroier.WMInventoryControl.debug.Debug.DebugKey;
import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import me.deecaad.weaponmechanics.weapon.weaponevents.WeaponPreShootEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class WeaponShootListener implements Listener {

    private final WMInventoryControl plugin;

    public WeaponShootListener(WMInventoryControl plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWeaponShoot(WeaponPreShootEvent e) { //WeaponPreShootEvent is the very first event that WM triggers so we use it because we don't want to waste any ammo
        if (!(e.getShooter() instanceof Player p)) return;

        ItemStack held = (e.getHand() == EquipmentSlot.HAND)
                ? p.getInventory().getItemInMainHand()
                : p.getInventory().getItemInOffHand();

        // Not a managed weapon? Let WM proceed
        if (!WMIC.api().isManagedWeapon(held)) return;

        // Already marked? Nothing to do (avoid re-firing each shot)
        if (WMIC.api().isMarked(held)) return;

        var result = WMIC.api().tryMark(p, held);
        if (result.allowed()) {
            Debug.log(plugin, DebugKey.WEAPON_SHOOT,
                    "Marked on use: " + WeaponMechanicsAPI.getWeaponTitle(held));
            return;
        }

        // Only unmanaged should pass through; everything else cancels with reasoned UX
        DenyReason r = result.reason();
        if (r == null || r == DenyReason.NOT_MANAGED) return;

        e.setCancelled(true);
        switch (r) {
            case STACKED_ITEM -> p.sendMessage("§c(!) You can’t use a stacked weapon. Split the stack first.");
            case EXCLUSIVE_CONFLICT ->
                    p.sendMessage("§c(!) You already have a marked weapon from an exclusive group.");
            case POOL_TOTAL_LIMIT ->
                    p.sendMessage("§c(!) That group’s shared limit is full.");
            case POOL_MEMBER_CAP ->
                    p.sendMessage("§c(!) You’ve reached the per-weapon cap for this group.");
            case PER_WEAPON_LIMIT ->
                    p.sendMessage("§c(!) You’ve reached the limit for this weapon.");
            case MARK_BLOCKED_IN_COMBAT ->
                    p.sendMessage("§c(!) Marking is disabled while you’re in combat.");
            case REMARK_BLOCKED_IN_COMBAT ->
                    p.sendMessage("§c(!) You unmarked during combat; re-marking is blocked until combat ends.");
            default ->
                    p.sendMessage("§c(!) Couldn’t mark this weapon right now.");
        }
    }
}