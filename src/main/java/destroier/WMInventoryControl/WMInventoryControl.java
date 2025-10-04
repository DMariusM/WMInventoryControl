package destroier.WMInventoryControl;

import destroier.WMInventoryControl.api.WMICApi;
import destroier.WMInventoryControl.apiimpl.WMICApiImpl;
import destroier.WMInventoryControl.commands.ConfigReloadCommand;
import destroier.WMInventoryControl.debug.Debug;
import destroier.WMInventoryControl.listeners.commands.ItemAuctionCommandListener;
import destroier.WMInventoryControl.listeners.containers.ContainerUnmarkListener;
import destroier.WMInventoryControl.listeners.containers.ItemFrameListener;
import destroier.WMInventoryControl.listeners.containers.SpecialContainerListener;
import destroier.WMInventoryControl.listeners.player.PlayerQuitListener;
import destroier.WMInventoryControl.misc.HatGuardListener;
import destroier.WMInventoryControl.listeners.invsee.InvseeTransferListener;
import destroier.WMInventoryControl.listeners.player.MarkedItemDropListener;
import destroier.WMInventoryControl.listeners.combat.CombatEndListener;
import destroier.WMInventoryControl.listeners.player.PlayerDeathListener;
import destroier.WMInventoryControl.listeners.weapons.WeaponShootListener;
import destroier.WMInventoryControl.listeners.weight.WeightAccrualListener;
import destroier.WMInventoryControl.listeners.weight.WeightCheckListener;
import destroier.WMInventoryControl.managers.CombatRestrictionsManager;
import destroier.WMInventoryControl.managers.ConfigManager;
import destroier.WMInventoryControl.managers.InventoryManager;
import destroier.WMInventoryControl.managers.WeightManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/**
 * Main plugin entry point for WMInventoryControl.
 *
 * <p>Bootstraps managers, registers event listeners and commands, and
 * coordinates config reloads. Exposes accessors for managers so listeners
 * can share state through a single plugin instance.</p>
 */
public final class WMInventoryControl extends JavaPlugin {

    private static WMInventoryControl self;
    private ConfigManager configManager;
    private InventoryManager inventoryManager;
    private CombatRestrictionsManager combatRestrictionsManager;
    private WeightManager weightManager;
    private WMICApi api;

    @Override
    public void onEnable() {
        self = this;

        if (!pluginIsPresent("WeaponMechanics")) {
            getLogger().severe("WeaponMechanics is required for this plugin to run! Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        boolean missingCLX = !pluginIsPresent("CombatLogX");
        boolean missingBSC = !pluginIsPresent("BlueSlimeCore");
        if (missingCLX || missingBSC) {
            getLogger().warning("Combat weight features disabled (missing: "
                    + (missingCLX ? "CombatLogX " : "")
                    + (missingBSC ? "BlueSlimeCore" : "") + ").");
        }

        // config
        saveDefaultConfig();

        // managers
        this.configManager = new ConfigManager(this);
        this.combatRestrictionsManager = new CombatRestrictionsManager(this);
        this.inventoryManager = new InventoryManager(this);
        this.weightManager = new WeightManager(this);
        Debug.reloadFromConfig(getConfig());

        configManager.reloadGroups();
        configManager.reloadContainerTypes();
        configManager.reloadWeightGroups();

        // listeners
        getServer().getPluginManager().registerEvents(new WeaponShootListener(this), this);
        getServer().getPluginManager().registerEvents(new MarkedItemDropListener(this), this);
        getServer().getPluginManager().registerEvents(new ItemAuctionCommandListener(this), this);
        getServer().getPluginManager().registerEvents(new ContainerUnmarkListener(this), this);
        getServer().getPluginManager().registerEvents(new HatGuardListener(this), this);
        getServer().getPluginManager().registerEvents(new ItemFrameListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new InvseeTransferListener(this), this);
        getServer().getPluginManager().registerEvents(new SpecialContainerListener(this), this);
        getServer().getPluginManager().registerEvents(new WeightCheckListener(this), this);
        getServer().getPluginManager().registerEvents(new WeightAccrualListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatEndListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);

        // commands
        Objects.requireNonNull(this.getCommand("wmic")).setExecutor(new ConfigReloadCommand(this));

        // api
        this.api = new WMICApiImpl(this);
        getServer().getServicesManager().register(WMICApi.class, api, this, org.bukkit.plugin.ServicePriority.Normal);

        if (!configManager.isRemoveWeightPostCombat()
                && configManager.getWeightResetTimeoutSeconds() <= 0) {
            getLogger().warning("[WMIC] remove-limit-post-combat is FALSE and no timeout is set. " +
                    "Weight will persist across fights and only reset on death/logout/restart.");
        }

        self.getLogger().info("WMInventoryControl has been enabled!");
    }

    @Override
    public void onDisable() {
        if (weightManager != null) {
            weightManager.clearAll();
        }

        getServer().getServicesManager().unregister(WMICApi.class, api);

        self.getLogger().info("WMInventoryControl has been disabled!");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    private boolean pluginIsPresent(String name) {
        Plugin p = Bukkit.getPluginManager().getPlugin(name);
        return p != null && p.isEnabled();
    }

    public InventoryManager getInventoryManager() {
        return inventoryManager;
    }

    public WeightManager getWeightManager() {
        return weightManager;
    }

    public CombatRestrictionsManager getCombatRestrictionsManager() {
        if (combatRestrictionsManager == null) {
            getLogger().warning("[WMIC] CombatRestrictionsManager was null at access time; creating lazily.");
            try {
                combatRestrictionsManager = new CombatRestrictionsManager(this);
            } catch (Throwable t) {
                getLogger().severe("[WMIC] Failed to construct CombatRestrictionsManager: " + t);
            }
        }
        return combatRestrictionsManager;
    }

    /**
     * @param feature the key under debug-mode
     * @return whether debug is enabled for that feature
     */
    public boolean isDebug(String feature) {
        return getConfig().getBoolean("debug-mode." + feature, false);
    }
}