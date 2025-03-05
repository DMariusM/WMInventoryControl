package destroier.WMInventoryControl.managers;

import destroier.WMInventoryControl.WMInventoryControl;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.Set;

public class ConfigManager {
    private final WMInventoryControl plugin;

    public ConfigManager(WMInventoryControl plugin) {
        this.plugin = plugin;
    }

    public int getWeaponLimit(String weaponName) {
        FileConfiguration config = plugin.getConfig();
        String configKey = getConfigKeyForWeapon(weaponName);
        if (configKey == null) {
            if (config.getBoolean("debug-mode")) {
                plugin.getLogger().info("Weapon not found in config, defaulting to 1: " + weaponName);
            }
            return 1; // Default limit if not found
        }
        return config.getInt("weapon-limits." + configKey, 1);
    }

    public boolean isWeaponConfigured(String weaponTitle) {
        FileConfiguration config = plugin.getConfig();
        if (config.getConfigurationSection("weapon-limits") == null) {
            if (config.getBoolean("debug-mode")) {
                plugin.getLogger().warning("No 'weapon-limits' section found in config.yml.");
            }
            return false;
        }
        Set<String> weaponKeys = config.getConfigurationSection("weapon-limits").getKeys(false);
        if (config.getBoolean("debug-mode")) {
            plugin.getLogger().info("Loaded weapon keys: " + weaponKeys);
        }
        for (String key : weaponKeys) {
            if (key.equalsIgnoreCase(weaponTitle)) {
                return true;
            }
        }
        return false;
    }

    public String getConfigKeyForWeapon(String weaponTitle) {
        FileConfiguration config = plugin.getConfig();
        if (config.getConfigurationSection("weapon-limits") == null) {
            if (config.getBoolean("debug-mode")) {
                plugin.getLogger().warning("No 'weapon-limits' section found in config.yml.");
            }
            return null;
        }
        Set<String> weaponKeys = config.getConfigurationSection("weapon-limits").getKeys(false);
        for (String key : weaponKeys) {
            if (key.equalsIgnoreCase(weaponTitle)) {
                return key; // Return the exact config key
            }
        }
        return null;
    }
}
