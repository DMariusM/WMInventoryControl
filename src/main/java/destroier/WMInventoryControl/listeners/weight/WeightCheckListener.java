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

/**
 * Checks weight limits before a shot while the player is in combat.
 * For each matching weight-group:
 * - INDIVIDUAL: block if current used + cost would exceed max.
 * - SHARED_POOL: block if pool is already full, or if a first-time title would
 *   push the pool over its maximum.
 */
public class WeightCheckListener implements Listener {
    private final WMInventoryControl plugin;
    private final ConfigManager cfg;
    private final WeightManager weights;

    public WeightCheckListener(WMInventoryControl plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
        this.weights = plugin.getWeightManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreShoot(WeaponPreShootEvent e) {
        if (!(e.getShooter() instanceof Player p)) return;

        var cr = plugin.getCombatRestrictionsManager();
        if (cr == null || !cr.isEnabled() || !cr.isInCombat(p)) return;
        if (!cfg.hasAnyWeightGroups()) return;

        String title = e.getWeaponTitle();
        var groups = cfg.getWeightGroupsForWeapon(title);
        if (groups.isEmpty()) return;

        String upper = title.toUpperCase();
        var id = p.getUniqueId();

        for (WeightGroup g : groups) {
            Integer cost = g.items.get(upper);
            if (cost == null || cost <= 0) continue;

            if (g.type == WeightType.INDIVIDUAL) {
                int used = weights.getUsedForWeapon(id, upper);
                Debug.log(plugin, DebugKey.WEIGHT,
                        "INDIVIDUAL check " + upper + " used=" + used + " + cost=" + cost + " > max=" + g.max + "?");

                if (used + cost > g.max) {
                    e.setCancelled(true);
                    p.sendMessage("§c(!) You can’t use §e" + title + "§c (limit: §f" + used + "/" + g.max + "§c).");
                    return;
                }
            } else { // SHARED_POOL
                int used = weights.getUsedForGroup(id, g.name);
                boolean alreadyCounted = weights.hasCountedInGroup(id, g.name, upper);

                // If the pool is already full, block ANY further use from any title in this group
                if (used >= g.max) {
                    Debug.log(plugin, DebugKey.WEIGHT,
                            "SHARED_POOL check " + g.name + " used=" + used + " >= max=" + g.max +
                                    " -> BLOCK (title=" + upper + ")");
                    e.setCancelled(true);
                    p.sendMessage("§c(!) You can’t use §e" + title + "§c (group limit reached: §f" + used + "/" + g.max + "§c).");
                    return;
                }

                int eff = alreadyCounted ? 0 : cost;
                Debug.log(plugin, DebugKey.WEIGHT,
                        "SHARED_POOL check " + g.name + " used=" + used + " + effCost=" + eff +
                                " > max=" + g.max + " ? (title=" + upper + ", alreadyCounted=" + alreadyCounted + ")");

                // If not full yet, only a first-time title may push the pool over the limit
                if (!alreadyCounted && used + cost > g.max) {
                    e.setCancelled(true);
                    p.sendMessage("§c(!) You can’t use §e" + title + "§c (would exceed group: §f" +
                            used + "+" + cost + " > " + g.max + "§c).");
                    return;
                }
            }
        }
    }
}