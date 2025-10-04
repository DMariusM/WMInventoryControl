package destroier.WMInventoryControl.debug;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized, opt-in debug logger with per-topic flags.
 *
 * <p>Reads {@code debug-mode} flags from config and gates logs accordingly.
 * Offers simple static helpers for info/warn/error and optional rate-limited
 * logging.</p>
 */
public final class Debug {
    private static final String PREFIX = "[WMIC]";
    private static volatile Map<String, Boolean> flags = Collections.emptyMap();
    private static volatile boolean all = false;

    // signature -> last time ms
    private static final Map<String, Long> lastLog = new ConcurrentHashMap<>();

    public static void reloadFromConfig(FileConfiguration cfg) {
        Map<String, Boolean> map = new HashMap<>();
        boolean allFlag = false;

        ConfigurationSection sec = cfg.getConfigurationSection("debug-mode");
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                boolean v = sec.getBoolean(k, false);
                map.put(k.toLowerCase(Locale.ROOT), v);
            }
            allFlag = sec.getBoolean("all", false) || sec.getBoolean("*", false);
        }

        flags = map;
        all = allFlag;
        lastLog.clear(); // resetting rate-limits on reload
    }

    /**
     * Keys for enabling/disabling individual debug topics via configuration.
     *
     * <p>Each key corresponds to a boolean under {@code debug-mode:}.</p>
     */
    public static boolean enabled(DebugKey key) {
        return enabled(key.key());
    }

    public static boolean enabled(String key) {
        return all || flags.getOrDefault(key.toLowerCase(Locale.ROOT), false);
    }

    public static void log(JavaPlugin plugin, DebugKey key, String msg) {
        if (enabled(key)) plugin.getLogger().info(PREFIX + tag(key) + " " + msg);
    }

    public static void warn(JavaPlugin plugin, DebugKey key, String msg) {
        if (enabled(key)) plugin.getLogger().warning(PREFIX + tag(key) + " " + msg);
    }

    public static void error(JavaPlugin plugin, DebugKey key, String msg, Throwable t) {
        if (enabled(key)) plugin.getLogger().severe(PREFIX + tag(key) + " " + msg + " :: " + t);
    }

    /** Rate-limited info: logs at most once per `minIntervalMs` for same (key+msg). */
    public static void logRate(JavaPlugin plugin, DebugKey key, String msg, long minIntervalMs) {
        if (!enabled(key)) return;

        final String sig = key.key() + "|" + msg;
        final long now = System.currentTimeMillis();

        lastLog.compute(sig, (k, prev) -> {
            if (prev == null || now - prev >= minIntervalMs) {
                plugin.getLogger().info(PREFIX + tag(key) + " " + msg);
                return now;          // update timestamp
            }
            return prev;             // keep previous timestamp, skip log
        });
    }

    private static String tag(DebugKey key) {
        String pretty = key.name().toLowerCase(Locale.ROOT).replace('_', '-');
        return "[" + pretty.substring(0,1).toUpperCase(Locale.ROOT) + pretty.substring(1) + "]";
    }

    public enum DebugKey {
        WEAPON_SHOOT("weapon-shoot"),
        CONTAINER_UNMARK("container-unmark"),
        CONTAINER_BLOCK("container-block"),
        INVSEE_TRANSFER("invsee-transfer"),
        PLAYER_DROP("player-drop"),
        ITEM_FRAME("item-frame"),
        PLAYER_DEATH("player-death"),
        SPECIAL_CONTAINER("special-container"),
        HAT_GUARD("hat-guard"),
        AUCTION("auction"),
        GROUPS("groups"),
        COMBAT("combat"),
        COMBAT_END("combat-end"),
        CONFIG_MANAGER("config-manager"),
        INVENTORY_MANAGER("inventory-manager"),
        WEIGHT("weight"),
        CONFIG_RELOAD("config-reload");

        private final String key;

        DebugKey(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }
}