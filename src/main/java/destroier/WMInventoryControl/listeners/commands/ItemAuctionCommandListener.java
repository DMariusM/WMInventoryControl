package destroier.WMInventoryControl.listeners.commands;

import destroier.WMInventoryControl.WMInventoryControl;
import destroier.WMInventoryControl.debug.Debug;
import destroier.WMInventoryControl.debug.Debug.DebugKey;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Prevents auction/listing commands when the player is holding a marked weapon.
 *
 * <p>Command roots are read from {@code auction-commands} and take effect after a config reload.</p>
 */
public final class ItemAuctionCommandListener implements Listener {
    private final WMInventoryControl plugin;

    public ItemAuctionCommandListener(WMInventoryControl plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAuctionCommand(PlayerCommandPreprocessEvent e) {
        final String msg = e.getMessage();
        if (msg.isBlank()) return;

        // Normalize first token: strip leading '/', lower-case, and optional namespace 'plugin:cmd'
        String first = msg.trim().split("\\s+")[0];
        if (first.startsWith("/")) first = first.substring(1);
        first = first.toLowerCase(Locale.ROOT);
        final int colon = first.indexOf(':');
        if (colon != -1) first = first.substring(colon + 1);

        // Load auction roots from config (auto-updates after /wmic reload)
        final Set<String> roots = getAuctionRootsFromConfig();
        if (!roots.contains(first)) return;

        // Trace which root matched (rate-limited to avoid spam)
        Debug.logRate(plugin, DebugKey.AUCTION, "Matched auction root: '" + first + "'", 3000);

        final Player p = e.getPlayer();

        // Block if any of these are a marked weapon
        if (isMarked(p.getInventory().getItemInMainHand())
                || isMarked(p.getInventory().getItemInOffHand())
                || isMarked(p.getItemOnCursor())) {

            e.setCancelled(true);
            p.sendMessage("§c(!) You cannot list or auction marked weapons.");

            Debug.log(plugin, DebugKey.AUCTION,
                    "Blocked auction command '" + msg + "' for " + p.getName());
        }
    }

    private boolean isMarked(ItemStack item) {
        return item != null
                && item.getType() != Material.AIR
                && plugin.getInventoryManager().isWeaponMarked(item);
    }

    private Set<String> getAuctionRootsFromConfig() {
        final Set<String> roots = new HashSet<>();
        for (String s : plugin.getConfig().getStringList("auction-commands")) {
            if (s == null) continue;
            String x = s.trim().toLowerCase(Locale.ROOT);
            if (x.startsWith("/")) x = x.substring(1); // we store roots without leading '/'
            if (!x.isEmpty()) roots.add(x);
        }
        return roots;
    }
}