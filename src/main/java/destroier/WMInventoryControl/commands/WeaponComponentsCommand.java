package destroier.WMInventoryControl.commands;

import destroier.WMInventoryControl.WMInventoryControl;
import me.deecaad.weaponmechanics.utils.CustomTag;
import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

public class WeaponComponentsCommand implements CommandExecutor {

    private final WMInventoryControl plugin;

    public WeaponComponentsCommand(WMInventoryControl plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        FileConfiguration config = plugin.getConfig();
        ItemStack heldItem = player.getInventory().getItemInMainHand();

        if (config.getBoolean("debug-mode")) {
            plugin.getLogger().info("[WMIC] CheckNBT command triggered");
        }

        if (!heldItem.hasItemMeta()) {
            player.sendMessage("§cYou are not holding a valid item.");
            return true;
        }

        // Get weapon title
        String weaponTitle = WeaponMechanicsAPI.getWeaponTitle(heldItem);
        if (weaponTitle == null) {
            player.sendMessage("§cThis is not a WeaponMechanics weapon.");
            return true;
        }

        player.sendMessage("§6Weapon Title: " + weaponTitle);
        player.sendMessage("§7Available Components:");

        // Retrieve data from both NBT and CustomTag
        Map<String, Object> weaponComponents = getAllWeaponComponents(heldItem);

        // Print each component on a new line
        for (Map.Entry<String, Object> entry : weaponComponents.entrySet()) {
            player.sendMessage("§e- " + entry.getKey() + ": " + (entry.getValue() != null ? entry.getValue() : "N/A"));
        }

        return true;
    }

    private Map<String, Object> getAllWeaponComponents(ItemStack weaponStack) {
        Map<String, Object> components = new LinkedHashMap<>();

        // Get Persistent Data Container (Bukkit NBT)
        PersistentDataContainer pdc = weaponStack.getItemMeta().getPersistentDataContainer();
        for (NamespacedKey key : pdc.getKeys()) {
            components.put("NBT: " + key.getKey(), getNBTValue(pdc, key));
        }

        // Fetch WeaponMechanics-specific data using CustomTag
        components.put("Weapon Title", CustomTag.WEAPON_TITLE.getString(weaponStack));
        components.put("Ammo Left", CustomTag.AMMO_LEFT.getInteger(weaponStack));
        components.put("Fire Mode", CustomTag.SELECTIVE_FIRE.getInteger(weaponStack));
        components.put("Durability", CustomTag.DURABILITY.getInteger(weaponStack));
        components.put("Max Durability", CustomTag.MAX_DURABILITY.getInteger(weaponStack));
        components.put("Current Skin", CustomTag.WEAPON_SKIN.getString(weaponStack));
        components.put("Magazine Size", CustomTag.AMMO_MAGAZINE.getInteger(weaponStack));

        return components;
    }

    // Function to determine the correct NBT type
    private Object getNBTValue(PersistentDataContainer pdc, NamespacedKey key) {
        if (pdc.has(key, PersistentDataType.STRING)) {
            return pdc.get(key, PersistentDataType.STRING);
        } else if (pdc.has(key, PersistentDataType.INTEGER)) {
            return pdc.get(key, PersistentDataType.INTEGER);
        } else if (pdc.has(key, PersistentDataType.DOUBLE)) {
            return pdc.get(key, PersistentDataType.DOUBLE);
        } else if (pdc.has(key, PersistentDataType.LONG)) {
            return pdc.get(key, PersistentDataType.LONG);
        } else if (pdc.has(key, PersistentDataType.BYTE)) {
            return pdc.get(key, PersistentDataType.BYTE);
        } else if (pdc.has(key, PersistentDataType.FLOAT)) {
            return pdc.get(key, PersistentDataType.FLOAT);
        } else if (pdc.has(key, PersistentDataType.BOOLEAN)) {
            return pdc.get(key, PersistentDataType.BOOLEAN);
        } else {
            return "Unknown Type";
        }
    }
}