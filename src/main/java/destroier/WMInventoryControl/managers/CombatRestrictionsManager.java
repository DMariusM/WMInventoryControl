package destroier.WMInventoryControl.managers;

import com.github.sirblobman.combatlogx.api.ICombatLogX;
import com.github.sirblobman.combatlogx.api.event.PlayerUntagEvent;
import com.github.sirblobman.combatlogx.api.manager.ICombatManager;
import destroier.WMInventoryControl.WMInventoryControl;
import destroier.WMInventoryControl.debug.Debug;
import destroier.WMInventoryControl.debug.Debug.DebugKey;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Integrates with CombatLogX to enforce combat-related restrictions.
 * Responsibilities:
 * - Detect whether a player is currently in combat.
 * - Remember titles that a player unmarked during combat and block re-marking
 *   those titles until the player is untagged.
 * - Clear restrictions on PlayerUntagEvent.
 * Falls back to a no-op if CombatLogX is not present.
 */
public final class CombatRestrictionsManager implements Listener {
    private final WMInventoryControl plugin;
    private ICombatManager clx; // null if CLX not present
    private final Map<UUID, Set<String>> blocked = new ConcurrentHashMap<>();

    public CombatRestrictionsManager(WMInventoryControl plugin) {
        this.plugin = plugin;

        ICombatLogX api = resolveAPI();
        if (api != null) {
            this.clx = api.getCombatManager();
            Bukkit.getPluginManager().registerEvents(this, plugin);
            plugin.getLogger().info("[WMIC] CombatLogX detected: combat-aware re-mark restriction enabled.");
            dbgCombat("Resolved CombatLogX via " + api.getClass().getName());
        } else {
            plugin.getLogger().warning("[WMIC] CombatLogX API not found via ServicesManager or PluginManager: integration disabled.");
            dbgCombat("CLX not found; combat checks will no-op.");
        }
    }

    public boolean isEnabled() {
        return clx != null;
    }

    public boolean isInCombat(Player p) {
        boolean result = clx != null && clx.isInCombat(p);
        // log only when true to avoid spam
        if (result) dbgCombat("isInCombat(" + p.getName() + ") = true");
        return result;
    }

    /** Record that player unmarked this weapon while in combat */
    public void flagUnmarkDuringCombat(Player p, String weaponTitle) {
        if (!isInCombat(p) || weaponTitle == null) return;

        blocked.computeIfAbsent(p.getUniqueId(), k -> ConcurrentHashMap.newKeySet())
                .add(weaponTitle.toUpperCase());

        dbgCombat(p.getName() + " unmarked in combat: '" + weaponTitle + "' -> block re-mark until untag");
    }

    /** Should we block marking this weapon now? */
    public boolean isMarkingBlocked(Player p, String weaponTitle) {
        if (weaponTitle == null) return false;
        Set<String> set = blocked.get(p.getUniqueId());
        boolean blockedNow = set != null && set.contains(weaponTitle.toUpperCase());
        if (blockedNow) dbgCombat("BLOCK mark for '" + weaponTitle + "' on " + p.getName() + " (still tagged)");
        return blockedNow;
    }

    /** Clear all blocks when combat ends for this player */
    @EventHandler
    public void onUntag(PlayerUntagEvent e) {
        blocked.remove(e.getPlayer().getUniqueId());
        dbgCombat(e.getPlayer().getName() + " untagged -> cleared re-mark blocks");
    }

    private ICombatLogX resolveAPI() {
        try {
            ICombatLogX svc = Bukkit.getServicesManager().load(ICombatLogX.class);
            if (svc != null) return svc;
        } catch (Throwable ignored) {}

        try {
            org.bukkit.plugin.Plugin p = Bukkit.getPluginManager().getPlugin("CombatLogX");
            if (p instanceof ICombatLogX) return (ICombatLogX) p;
        } catch (Throwable ignored) {}

        return null;
    }

    private void dbgCombat(String msg) {
        Debug.log(plugin, DebugKey.COMBAT, msg);
    }
}