package destroier.WMInventoryControl;

import destroier.WMInventoryControl.commands.ConfigReloadCommand;
import destroier.WMInventoryControl.commands.WeaponComponentsCommand;
import destroier.WMInventoryControl.events.MarkedItemDropEvent;
import destroier.WMInventoryControl.listeners.*;
import destroier.WMInventoryControl.managers.ConfigManager;
import destroier.WMInventoryControl.managers.InventoryManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class WMInventoryControl extends JavaPlugin {

    private static WMInventoryControl self;
    private ConfigManager configManager;
    private InventoryManager inventoryManager;

    @Override
    public void onEnable() {
        self = this;

        if (!isPluginInstalled("WeaponMechanics")) {
            getLogger().severe("WeaponMechanics is required for this plugin to run! Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        //managers
        this.configManager = new ConfigManager(this);
        this.inventoryManager = new InventoryManager(this);

        //listeners
        getServer().getPluginManager().registerEvents(new WeaponShootListener(this), this);
        getServer().getPluginManager().registerEvents(new MarkedItemDropEvent(this), this);
        getServer().getPluginManager().registerEvents(new ContainerUnmarkListener(this), this);
        getServer().getPluginManager().registerEvents(new ItemFrameListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);

        //commands
        Objects.requireNonNull(this.getCommand("wmic")).setExecutor(new ConfigReloadCommand(this));
        Objects.requireNonNull(this.getCommand("checknbt")).setExecutor(new WeaponComponentsCommand(this));

        //config
        saveDefaultConfig();

        self.getLogger().info("WMInventoryControl has been enabled!");
    }

    @Override
    public void onDisable() {
        self.getLogger().info("WMInventoryControl has been disabled!");
    }

    public boolean isPluginInstalled(String pluginName) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
        return plugin != null && plugin.isEnabled();
    }

    public InventoryManager getInventoryManager() {
        return inventoryManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

}