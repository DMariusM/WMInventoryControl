package destroier.WMInventoryControl.listeners.weight;

import destroier.WMInventoryControl.WMInventoryControl;
import destroier.WMInventoryControl.debug.Debug;
import destroier.WMInventoryControl.debug.Debug.DebugKey;
import destroier.WMInventoryControl.managers.ConfigManager;
import destroier.WMInventoryControl.managers.ConfigManager.WeightGroup;
import destroier.WMInventoryControl.managers.ConfigManager.WeightType;
import destroier.WMInventoryControl.managers.WeightManager;
import me.deecaad.weaponmechanics.weapon.weaponevents.PrepareWeaponShootEvent;
import me.deecaad.weaponmechanics.weapon.weaponevents.WeaponPreShootEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

/**
 * Accrues "weight" while a player is in combat and uses configured weapons.
 * For each matching weight-group:
 * - INDIVIDUAL: add the weapon's cost every time.
 * - SHARED_POOL: add the cost only the first time a given title is used
 *   during the ongoing combat (per player per group).
 */
public class WeightAccrualListener implements Listener {
    private final WMInventoryControl plugin;
    private final ConfigManager cfg;
    private final WeightManager weights;

    public WeightAccrualListener(WMInventoryControl plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
        this.weights = plugin.getWeightManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPreShootAccrue(PrepareWeaponShootEvent e) {
        if (!(e.getShooter() instanceof Player p)) return;

        var cr = plugin.getCombatRestrictionsManager();
        if (cr == null || !cr.isEnabled() || !cr.isInCombat(p)) return;
        if (!cfg.hasAnyWeightGroups()) return;

        String title = e.getWeaponTitle();
        var groups = cfg.getWeightGroupsForWeapon(title);
        if (groups.isEmpty()) return;

        String upper = title.toUpperCase();
        UUID id = p.getUniqueId();

        for (WeightGroup g : groups) {
            Integer cost = g.items.get(upper);
            if (cost == null || cost <= 0) continue;

            if (g.type == WeightType.INDIVIDUAL) {
                weights.addForWeapon(id, upper, cost);
                int used = weights.getUsedForWeapon(id, upper);
                Debug.log(plugin, DebugKey.WEIGHT,
                        "INDIVIDUAL add " + cost + " -> used=" + used + "/" + g.max + " for " + upper);
            } else { // SHARED_POOL
                boolean first = weights.addForGroupIfFirst(id, g.name, upper, cost);
                int used = weights.getUsedForGroup(id, g.name);
                Debug.log(plugin, DebugKey.WEIGHT,
                        "SHARED_POOL add " +
                                (first ? cost + " (first time " + upper + ")" : "0 (already counted)") +
                                " -> used=" + used + "/" + g.max + " in group=" + g.name);
            }
        }
    }
}