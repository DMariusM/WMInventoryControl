package destroier.WMInventoryControl.listeners.combat;

import com.github.sirblobman.combatlogx.api.event.PlayerUntagEvent;
import destroier.WMInventoryControl.WMInventoryControl;
import destroier.WMInventoryControl.debug.Debug;
import destroier.WMInventoryControl.managers.ConfigManager;
import destroier.WMInventoryControl.managers.WeightManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatEndListener implements Listener {

    private final WMInventoryControl plugin;
    private final ConfigManager configManager;
    private final WeightManager weightManager;

    private final Map<UUID, Integer> pendingTasks = new ConcurrentHashMap<>();

    public CombatEndListener(WMInventoryControl plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.weightManager = plugin.getWeightManager();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCombatEnd(PlayerUntagEvent e) {
        final Player player = e.getPlayer();
        final UUID id = player.getUniqueId();

        if (!configManager.isRemoveWeightPostCombat() && configManager.getWeightResetTimeoutSeconds() <= 0) {
            Debug.log(plugin, Debug.DebugKey.WEIGHT,
                    "[WMIC] remove-limit-post-combat is FALSE and no timeout is set. " +
                            "Weight will persist across fights and only reset on death/logout/restart.");
        }

        if (configManager.isRemoveWeightPostCombat()) {
            weightManager.clear(id);
            Debug.log(plugin, Debug.DebugKey.WEIGHT,
                    "[WMIC][weight] Combat ended -> clearing weight for " + player.getName());
            return;
        }

        final int time = configManager.getWeightResetTimeoutSeconds();
        if (time > 0) {
            final int ticks = time * 20;
            Integer prev = pendingTasks.remove(id);
            if (prev != null) {
                Bukkit.getScheduler().cancelTask(prev);
            }
            int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                pendingTasks.remove(id);
                Player now = Bukkit.getPlayer(id);
                var cr = plugin.getCombatRestrictionsManager();
                boolean inCombat = (now != null && cr != null && cr.isEnabled() && cr.isInCombat(now));
                if (!inCombat) {
                    weightManager.clear(id);
                    Debug.log(plugin, Debug.DebugKey.WEIGHT,
                            "[WMIC][weight] Cleared by timeout (" + time + "s) for " + (now != null ? now.getName() : id));
                }
            }, ticks).getTaskId();
            pendingTasks.put(id, taskId);
        }
    }
}