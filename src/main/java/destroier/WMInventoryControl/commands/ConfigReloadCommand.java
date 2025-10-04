package destroier.WMInventoryControl.commands;

import destroier.WMInventoryControl.WMInventoryControl;
import destroier.WMInventoryControl.debug.Debug;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Handles the admin command that reloads WMInventoryControl configuration.
 *
 * <p>Invokes the plugin reload routine and reports success or failure to the sender.</p>
 */
public class ConfigReloadCommand implements CommandExecutor {

    private final WMInventoryControl plugin;

    public ConfigReloadCommand(WMInventoryControl plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (sender instanceof Player && !sender.hasPermission("wmic.reload")) {
                sender.sendMessage("§c(!) You do not have permission to use this command.");
                return true;
            }

            plugin.reloadConfig();
            Debug.reloadFromConfig(plugin.getConfig());
            plugin.getConfigManager().reloadCombatOptions();
            plugin.getConfigManager().reloadGroups();
            plugin.getConfigManager().reloadContainerTypes();
            plugin.getConfigManager().reloadWeightGroups();
            Debug.log(plugin, Debug.DebugKey.CONFIG_RELOAD, "Config reloaded.");
            sender.sendMessage("§a(✔) WMInventoryControl configuration has been reloaded.");
            return true;
        }

        sender.sendMessage("§c(!) Correct usage is /wmic reload");
        return true;
    }
}