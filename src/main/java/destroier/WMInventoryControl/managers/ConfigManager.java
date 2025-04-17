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
                plugin.getLogger().info("[WMIC] Weapon not found in config, defaulting to 1: " + weaponName);
            }
            return 1; // Default limit if not found
        }
        return config.getInt("weapon-limits." + configKey, 1);
    }

    public boolean isWeaponConfigured(String weaponTitle) {
        FileConfiguration config = plugin.getConfig();

        var section = config.getConfigurationSection("weapon-limits");

        if (section == null) {
            if (config.getBoolean("debug-mode")) {
                plugin.getLogger().warning("[WMIC] No 'weapon-limits' section found in config.yml.");
            }
            return false;
        }

        Set<String> weaponKeys = section.getKeys(false);

        if (config.getBoolean("debug-mode")) {
            plugin.getLogger().info("Loaded weapon keys: " + weaponKeys);
        }

        return weaponKeys.stream().anyMatch(key -> key.equalsIgnoreCase(weaponTitle));
    }

    public String getConfigKeyForWeapon(String weaponTitle) {
        FileConfiguration config = plugin.getConfig();

        var section = config.getConfigurationSection("weapon-limits");

        if (section == null) {
            if (config.getBoolean("debug-mode")) {
                plugin.getLogger().warning("No 'weapon-limits' section found in config.yml.");
            }
            return null;
        }

        // No more warnings here!
        Set<String> weaponKeys = section.getKeys(false);

        return weaponKeys.stream()
                .filter(key -> key.equalsIgnoreCase(weaponTitle))
                .findFirst()
                .orElse(null);
    }
}