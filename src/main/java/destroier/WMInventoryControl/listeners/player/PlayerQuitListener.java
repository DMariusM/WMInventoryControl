package destroier.WMInventoryControl.listeners.player;

import destroier.WMInventoryControl.WMInventoryControl;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Cleans up per-player state when a player disconnects.
 *
 * <p>Releases any cached data (e.g., weight usage or combat flags) to avoid stale state.</p>
 */
public class PlayerQuitListener implements Listener {

    private final WMInventoryControl plugin;

    public PlayerQuitListener(WMInventoryControl plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.getWeightManager().clear(e.getPlayer().getUniqueId());
    }
}