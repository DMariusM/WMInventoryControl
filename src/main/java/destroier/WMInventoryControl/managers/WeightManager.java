package destroier.WMInventoryControl.managers;

import java.util.*;
import destroier.WMInventoryControl.WMInventoryControl;
import destroier.WMInventoryControl.debug.Debug;
import destroier.WMInventoryControl.debug.Debug.DebugKey;

/**
 * Per-player in-memory tracking of accrued "weight" in combat.
 * Tracks:
 * - INDIVIDUAL: used weight per weapon title (uppercased).
 * - SHARED_POOL: used weight per group, plus which titles have been counted
 *   in each group to only charge the first use per title.
 * Provides simple add/get/clear primitives; state is reset by listeners or on
 * demand after combat ends.
 */
public class WeightManager {
    private WMInventoryControl plugin;

    private final Map<UUID, Map<String, Integer>> perWeapon = new HashMap<>();   // INDIVIDUAL
    private final Map<UUID, Map<String, Integer>> perGroup  = new HashMap<>();   // SHARED_POOL
    private final Map<UUID, Map<String, Set<String>>> seenTitles = new HashMap<>(); // SHARED_POOL "counted"


    public WeightManager() { }

    public WeightManager(WMInventoryControl plugin) {
        this.plugin = plugin;
    }

    public void setPlugin(WMInventoryControl plugin) {
        this.plugin = plugin;
    }

    private void dbg(String msg) {
        if (plugin != null) Debug.log(plugin, DebugKey.WEIGHT, msg);
    }

    private Map<String, Integer> weaponMap(UUID id) {
        return perWeapon.computeIfAbsent(id, k -> new HashMap<>());
    }

    private Map<String, Integer> groupMap (UUID id) {
        return perGroup .computeIfAbsent(id, k -> new HashMap<>());
    }

    private Map<String, Set<String>> seenMap(UUID id) {
        return seenTitles.computeIfAbsent(id, k -> new HashMap<>());
    }

    public int getUsedForWeapon(UUID id, String weaponUpper) {
        return weaponMap(id).getOrDefault(weaponUpper, 0);
    }

    public int getUsedForGroup(UUID id, String groupName) {
        return groupMap(id).getOrDefault(groupName, 0);
    }

    public int getTotalUsedForWeapons(UUID id) {
        return weaponMap(id).values().stream().mapToInt(Integer::intValue).sum();
    }
    public int getTotalUsedForGroups(UUID id) {
        return groupMap(id).values().stream().mapToInt(Integer::intValue).sum();
    }

    public boolean hasCountedInGroup(UUID id, String group, String w) {
        return seenMap(id).getOrDefault(group, Collections.emptySet()).contains(w);
    }

    public void addForWeapon(UUID id, String weaponUpper, int delta) {
        if (delta > 0) {
            int before = getUsedForWeapon(id, weaponUpper);
            weaponMap(id).merge(weaponUpper, delta, Integer::sum);
            int after = getUsedForWeapon(id, weaponUpper);
            dbg("INDIVIDUAL add: +" + delta + " to " + weaponUpper + " -> " + before + " -> " + after);
        }
    }

    public void addForGroup(UUID id, String groupName, int delta) {
        if (delta > 0) {
            int before = getUsedForGroup(id, groupName);
            groupMap(id).merge(groupName, delta, Integer::sum);
            int after = getUsedForGroup(id, groupName);
            dbg("SHARED_POOL add: +" + delta + " to group '" + groupName + "' -> " + before + " -> " + after);
        }
    }

    /** Adds cost only the FIRST time this weapon title appears in the pool this combat. */
    public boolean addForGroupIfFirst(UUID id, String groupName, String weaponUpper, int delta) {
        var seen = seenMap(id).computeIfAbsent(groupName, g -> new HashSet<>());
        boolean firstCharge = seen.add(weaponUpper);
        if (!firstCharge) {
            dbg("SHARED_POOL skip charge (already counted) for " + weaponUpper + " in group '" + groupName + "'");
            return false; // already charged
        }
        addForGroup(id, groupName, delta);
        dbg("SHARED_POOL first-time charge +" + delta + " for " + weaponUpper + " in group '" + groupName + "'");
        return true;
    }

    public void clear(UUID id) {
        perWeapon.remove(id);
        perGroup.remove(id);
        seenTitles.remove(id);
        dbg("Cleared weights for player " + id);
    }

    public void clearAll() {
        perWeapon.clear();
        perGroup.clear();
        seenTitles.clear();
        dbg("Cleared ALL weight data");
    }
}