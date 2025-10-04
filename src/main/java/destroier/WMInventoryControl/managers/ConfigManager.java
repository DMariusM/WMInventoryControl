package destroier.WMInventoryControl.managers;

import destroier.WMInventoryControl.WMInventoryControl;
import destroier.WMInventoryControl.debug.Debug;
import destroier.WMInventoryControl.debug.Debug.DebugKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;

import java.util.*;

/**
 * Loads and exposes configuration-driven rules and lookups.
 * Responsibilities:
 * - "groups": parse exclusive/pool groups, per-member caps, priorities; index
 *   membership for fast lookups.
 * - "weapon-limits": per-title limits (unlimited if not set).
 * - "weight-groups": parse INDIVIDUAL/SHARED_POOL groups and cost maps.
 * - "containers": load regular and non-accepting containers.
 * - Provide fast helpers (e.g., managed weapon set, group lookups, caps).
 * This class is read-mostly after reload; it precomputes indexes to keep the
 * hot paths in listeners efficient.
 */
public class ConfigManager {

    private final WMInventoryControl plugin;
    private boolean removeWeightPostCombat = true;
    private int weightResetTimeoutSeconds = 0;

    private final Map<String, Set<GroupDef>> groupsByMember = new HashMap<>(); // WEAPON_TITLE -> groups
    private final List<GroupDef> allGroups = new ArrayList<>();                // for optional debug dump

    private Map<String, WeightGroup> weightGroupsByName = Collections.emptyMap();
    private Map<String, Set<WeightGroup>> weightGroupsByWeapon = Collections.emptyMap();

    // quick membership check for listener hot path
    private final Set<String> managedWeapons = new HashSet<>();

    private EnumSet<InventoryType> regularContainers = EnumSet.noneOf(InventoryType.class);
    private EnumSet<InventoryType> nonAcceptingContainers = EnumSet.noneOf(InventoryType.class);

    private boolean blockMarkingInCombat = false;
    private boolean blockRemarkingInCombat = true;
    private boolean blockDroppingInCombat = true;
    private boolean blockMovingMarkedInCombat = true;

    public void reloadCombatOptions() {
        var c = plugin.getConfig().getConfigurationSection("combat-restrictions");
        if (c == null) {
            // keep defaults
            return;
        }
        blockMarkingInCombat       = c.getBoolean("block-marking-in-combat", false);
        blockRemarkingInCombat     = c.getBoolean("block-re-marking-in-combat", true);
        blockDroppingInCombat      = c.getBoolean("block-dropping-in-combat", true);
        blockMovingMarkedInCombat  = c.getBoolean("block-moving-marked-in-combat", true);

        Debug.log(plugin, Debug.DebugKey.COMBAT, "[WMIC] combat-restrictions: " +
                "mark=" + blockMarkingInCombat +
                ", remark=" + blockRemarkingInCombat +
                ", drop=" + blockDroppingInCombat +
                ", move=" + blockMovingMarkedInCombat);
    }

    public boolean isBlockMarkingInCombat() {
        return blockMarkingInCombat;
    }

    public boolean isBlockRemarkingInCombat() {
        return blockRemarkingInCombat;
    }

    public boolean isBlockDroppingInCombat()
    {
        return blockDroppingInCombat;
    }

    public boolean isBlockMovingMarkedInCombat() {
        return blockMovingMarkedInCombat;
    }

    public ConfigManager(WMInventoryControl plugin) {
        this.plugin = plugin;
    }

    private static final String PATH_GROUPS = "groups";
    private static final String PATH_WEAPON_LIMITS = "weapon-limits";
    private static final String PATH_WEIGHT_GROUPS = "weight-groups";
    private static final String PATH_CONTAINERS_REGULAR = "containers.regular";
    private static final String PATH_CONTAINERS_BLOCK = "containers.non-accepting";

    /**
     * Group behaviour:
     * {@code EXCLUSIVE} (only one member may be marked) or
     * {@code POOL} (shared limit with optional per-member caps).
     */
    public enum GroupMode { EXCLUSIVE, POOL }

    public static final class GroupDef {
        public final String name;
        public final GroupMode mode;
        public final int limit;      // <=0 => unlimited for POOL
        public final int priority;   // 0 default
        public final Set<String> members;            // UPPERCASE
        public final Map<String, Integer> memberCaps; // UPPERCASE -> cap (<=0 => unlimited)

        /**
         * Immutable description of a configured weapon group.
         *
         * <p>Holds the group mode, optional shared limit, priority for tie-breaks,
         * members (upper-cased), and optional per-member caps.</p>
         */
        GroupDef(String name, GroupMode mode, int limit, int priority,
                 Set<String> members, Map<String, Integer> memberCaps) {
            this.name = name;
            this.mode = mode;
            this.limit = limit;
            this.priority = priority;
            this.members = members;
            this.memberCaps = memberCaps;
        }
    }

    /**
     * Weight accumulation mode: {@code INDIVIDUAL} per title, or
     * {@code SHARED_POOL} across multiple titles.
     */
    public enum WeightType { INDIVIDUAL, SHARED_POOL }

    /**
     * Immutable description of a weight group and its item costs.
     *
     * <p>Contains group name, type, maximum weight, and a map of
     * {@code TITLE -> cost}.</p>
     */
    public static final class WeightGroup {
        public final String name;
        public final WeightType type;
        public final int max;
        public final Map<String, Integer> items; // UPPERCASE -> cost
        WeightGroup(String name, WeightType type, int max, Map<String, Integer> items) {
            this.name = name; this.type = type; this.max = max; this.items = items;
        }
    }

    public void reloadGroups() {
        groupsByMember.clear();
        allGroups.clear();

        FileConfiguration cfg = plugin.getConfig();
        ConfigurationSection groupsSec = cfg.getConfigurationSection(PATH_GROUPS);
        if (groupsSec == null) {
            Debug.warn(plugin, DebugKey.CONFIG_MANAGER, "No 'groups' section.");
            rebuildManagedWeapons(); // still rebuild from limits/weights
            return;
        }

        for (String groupName : groupsSec.getKeys(false)) {
            ConfigurationSection gSec = groupsSec.getConfigurationSection(groupName);
            if (gSec == null) continue;

            GroupDef g = loadGroupDef(groupName, gSec);
            allGroups.add(g);

            for (String m : g.members) {
                groupsByMember.computeIfAbsent(m, __ -> new LinkedHashSet<>()).add(g);
            }

            StringBuilder mb = new StringBuilder();
            for (String m : g.members) {
                Integer cap = g.memberCaps.get(m);
                mb.append(m).append("(").append(cap == null || cap <= 0 ? "∞" : cap).append(") ");
            }
            Debug.log(plugin, DebugKey.CONFIG_MANAGER,
                    "Group loaded: name=" + g.name
                            + " mode=" + g.mode
                            + " limit=" + (g.limit <= 0 ? "∞" : g.limit)
                            + " prio=" + g.priority
                            + " members=[" + mb + "]");
        }

        rebuildManagedWeapons();
    }

    public void reloadWeightGroups() {
        var cfg = plugin.getConfig();
        removeWeightPostCombat = cfg.getBoolean("remove-limit-post-combat", true);
        weightResetTimeoutSeconds = Math.max(0, cfg.getInt("weight-reset-timeout-seconds", 0));

        Map<String, WeightGroup> groups = new HashMap<>();
        Map<String, Set<WeightGroup>> byWeapon = new HashMap<>();

        var root = cfg.getConfigurationSection(PATH_WEIGHT_GROUPS);
        if (root != null) {
            for (String gName : root.getKeys(false)) {
                var sec = root.getConfigurationSection(gName);
                if (sec == null) continue;

                String rawType = sec.getString("type", "INDIVIDUAL").toUpperCase(Locale.ROOT);
                WeightType type = switch (rawType) {
                    case "INDIVIDUAL", "CUMULATIVE" -> WeightType.INDIVIDUAL; // compat
                    case "SHARED_POOL", "LIST"      -> WeightType.SHARED_POOL; // compat
                    default -> {
                        // it's a misconfiguration
                        plugin.getLogger().warning("[WMIC][weight] Unknown type '" + rawType + "' for '" + gName + "', defaulting to INDIVIDUAL.");
                        yield WeightType.INDIVIDUAL;
                    }
                };

                int max = sec.getInt("maximum_limit", 0);

                var itemsSec = sec.getConfigurationSection("items");
                Map<String, Integer> items = new HashMap<>();
                if (itemsSec != null) {
                    for (String key : itemsSec.getKeys(false)) {
                        String upper = norm(key);
                        items.put(upper, itemsSec.getInt(key, 0));
                    }
                }

                WeightGroup g = new WeightGroup(gName, type, max, Collections.unmodifiableMap(items));
                groups.put(gName, g);
                for (String w : items.keySet()) {
                    byWeapon.computeIfAbsent(w, k -> new HashSet<>()).add(g);
                }
            }
        }

        weightGroupsByName = Collections.unmodifiableMap(groups);

        Map<String, Set<WeightGroup>> frozen = new HashMap<>();
        for (var e : byWeapon.entrySet()) {
            frozen.put(e.getKey(), Collections.unmodifiableSet(e.getValue()));
        }
        weightGroupsByWeapon = Collections.unmodifiableMap(frozen);

        Debug.log(plugin, DebugKey.WEIGHT, "Loaded weight groups: " + weightGroupsByName.keySet());

        rebuildManagedWeapons();
    }

    /** Call this after saveDefaultConfig() and on /wmic reload */
    public void reloadContainerTypes() {
        // Avoid overlaps: furnaces are in NON-ACCEPTING by default here
        List<String> regularDefaults = List.of(
                "CHEST", "BARREL", "ENDER_CHEST", "SHULKER_BOX",
                "HOPPER", "DISPENSER", "DROPPER"
                // "DECORATED_POT" // handled elsewhere if needed
        );

        List<String> nonAcceptingDefaults = List.of(
                "BEACON", "ENCHANTING", "ANVIL", "GRINDSTONE",
                "CARTOGRAPHY", "LOOM", "SMITHING", "STONECUTTER",
                "MERCHANT", "BREWING", "FURNACE", "SMOKER", "BLAST_FURNACE"
        );

        this.regularContainers = loadInventoryTypeSet(PATH_CONTAINERS_REGULAR, regularDefaults);
        this.nonAcceptingContainers = loadInventoryTypeSet(PATH_CONTAINERS_BLOCK, nonAcceptingDefaults);

        Debug.log(plugin, DebugKey.CONFIG_MANAGER, "Loaded REGULAR containers: " + regularContainers);
        Debug.log(plugin, DebugKey.CONFIG_MANAGER, "Loaded NON-ACCEPTING containers: " + nonAcceptingContainers);
    }

    public Set<InventoryType> getRegularContainers() {
        return Collections.unmodifiableSet(regularContainers);
    }

    public Set<InventoryType> getNonAcceptingContainers() {
        return Collections.unmodifiableSet(nonAcceptingContainers);
    }

    public int getWeaponLimit(String weaponName) {
        FileConfiguration config = plugin.getConfig();
        String configKey = getConfigKeyForWeapon(weaponName);

        if (configKey == null) {
            Debug.log(plugin, DebugKey.CONFIG_MANAGER, "[limits] No per-weapon limit for " + weaponName + " -> unlimited.");
            return Integer.MAX_VALUE;
        }

        int v = config.getInt(PATH_WEAPON_LIMITS + "." + configKey, Integer.MAX_VALUE);
        return (v <= 0) ? Integer.MAX_VALUE : v;
    }

    /** Hot-path: true only if this title has MARK rules (limits or groups). */
    public boolean isWeaponConfigured(String weaponTitle) {
        return managedWeapons.contains(norm(weaponTitle));
    }

    /** Clear helper if you prefer explicit naming at call sites. */
    public boolean hasMarkRulesFor(String weaponTitle) {
        return isWeaponConfigured(weaponTitle);
    }

    /** Weight-only helper (optional, useful for debugging/UI). */
    public boolean hasWeightRulesFor(String weaponTitle) {
        return !getWeightGroupsForWeapon(weaponTitle).isEmpty();
    }

    public String getConfigKeyForWeapon(String weaponTitle) {
        FileConfiguration config = plugin.getConfig();
        var section = config.getConfigurationSection(PATH_WEAPON_LIMITS);
        if (section == null) {
            Debug.warn(plugin, DebugKey.CONFIG_MANAGER, "No 'weapon-limits' section found.");
            return null;
        }
        for (String k : section.getKeys(false)) {
            if (k.equalsIgnoreCase(weaponTitle)) return k;
        }
        return null;
    }

    public int effectiveMemberPoolCap(String titleKey, GroupDef g) {
        Integer raw = g.memberCaps.get(norm(titleKey));
        return (raw == null || raw <= 0) ? Integer.MAX_VALUE : raw;
    }

    /** Returns all groups a weapon belongs to (might be empty). */
    public Set<GroupDef> getGroupsForWeapon(String titleKey) {
        return groupsByMember.getOrDefault(norm(titleKey), Collections.emptySet());
    }

    /** Count total marked across all members of a group. */
    public int countMarkedInGroup(Player p, GroupDef g, InventoryManager inv) {
        int sum = 0;
        for (String m : g.members) sum += inv.countMarkedWeapons(p, m);
        return sum;
    }

    public Set<WeightGroup> getWeightGroupsForWeapon(String weaponTitle) {
        if (weaponTitle == null) return Set.of();
        var s = weightGroupsByWeapon.get(norm(weaponTitle));
        return s == null ? Set.of() : s;
    }

    public boolean isRemoveWeightPostCombat() {
        return removeWeightPostCombat;
    }

    public int getWeightResetTimeoutSeconds() {
        return weightResetTimeoutSeconds;
    }

    public boolean hasAnyWeightGroups() {
        return !weightGroupsByName.isEmpty();
    }

    // debugging
    public void dumpGroupsForDebug() {
        for (GroupDef g : allGroups) {
            Debug.log(plugin, DebugKey.CONFIG_MANAGER,
                    g.name + " -> mode=" + g.mode
                            + " limit=" + (g.limit <= 0 ? "∞" : g.limit)
                            + " prio=" + g.priority
                            + " members=" + g.members);
        }
    }

    private static String norm(String s) {
        return (s == null) ? "" : s.trim().toUpperCase(Locale.ROOT);
    }

    private GroupDef loadGroupDef(String name, ConfigurationSection sec) {
        String type = sec.getString("type", "EXCLUSIVE");
        GroupMode mode = ("POOL".equalsIgnoreCase(type) || "SHARED_POOL".equalsIgnoreCase(type))
                ? GroupMode.POOL : GroupMode.EXCLUSIVE;

        int priority = sec.getInt("priority", 0);
        int limit = sec.getInt("limit", sec.getInt("maximum_limit", 0)); // <=0 => unlimited

        Set<String> members = new LinkedHashSet<>();
        Map<String, Integer> memberCaps = new HashMap<>();

        // LIST form
        List<String> list = sec.getStringList("members");
        if (!list.isEmpty()) {
            for (String m : list) if (m != null) members.add(norm(m));
        }

        // MAP form (per-member caps)
        ConfigurationSection map = sec.getConfigurationSection("members");
        if (map != null) {
            for (String key : map.getKeys(false)) {
                String k = norm(key);
                members.add(k);
                memberCaps.put(k, map.getInt(key, 0));
            }
        }

        return new GroupDef(
                name,
                mode,
                limit,
                priority,
                Collections.unmodifiableSet(members),
                Collections.unmodifiableMap(memberCaps)
        );
    }

    private EnumSet<InventoryType> loadInventoryTypeSet(String path, List<String> defaults) {
        FileConfiguration cfg = plugin.getConfig();
        List<String> raw = cfg.getStringList(path);
        List<String> src = raw.isEmpty() ? defaults : raw;

        EnumSet<InventoryType> set = EnumSet.noneOf(InventoryType.class);
        for (String name : src) {
            if (name == null) continue;
            String key = name.trim().toUpperCase(Locale.ROOT);
            try {
                set.add(InventoryType.valueOf(key));
            } catch (IllegalArgumentException ex) {
                // keep as a real warning; this is a misconfiguration
                plugin.getLogger().warning("[WMIC] Unknown InventoryType in " + path + ": '" + name + "'");
            }
        }
        return set;
    }

    /** Called by reloaders to precompute the membership set for fast checks. */
    private void rebuildManagedWeapons() {
        managedWeapons.clear();

        // from weapon-limits
        var limits = plugin.getConfig().getConfigurationSection(PATH_WEAPON_LIMITS);
        if (limits != null) {
            for (String k : limits.getKeys(false)) managedWeapons.add(norm(k));
        }

        // from EXCLUSIVE/POOL groups
        managedWeapons.addAll(groupsByMember.keySet());

        // IMPORTANT: do NOT add weight-only titles here
        // managedWeapons.addAll(weightGroupsByWeapon.keySet()); // <-- removed

        Debug.log(plugin, DebugKey.CONFIG_MANAGER,
                "Mark-managed titles: " + managedWeapons +
                        " | weight-only titles: " + diff(weightGroupsByWeapon.keySet(), managedWeapons));
    }

    /** small local util for the debug line above */
    private static Set<String> diff(Set<String> a, Set<String> b) {
        Set<String> out = new HashSet<>(a);
        out.removeAll(b);
        return out;
    }
}