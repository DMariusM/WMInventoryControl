package destroier.WMInventoryControl.apiimpl;

import destroier.WMInventoryControl.WMInventoryControl;
import destroier.WMInventoryControl.managers.ConfigManager;
import destroier.WMInventoryControl.managers.InventoryManager;
import destroier.WMInventoryControl.api.WMICApi;
import destroier.WMInventoryControl.api.events.WeaponMarkedEvent;
import destroier.WMInventoryControl.api.events.WeaponPreMarkEvent;
import destroier.WMInventoryControl.api.events.WeaponUnmarkedEvent;
import destroier.WMInventoryControl.api.types.*;
import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class WMICApiImpl implements WMICApi {
    private final WMInventoryControl plugin;
    private final ConfigManager cfg;
    private final InventoryManager inv;

    public WMICApiImpl(WMInventoryControl plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
        this.inv = plugin.getInventoryManager();
    }

    @Override
    public boolean isManagedWeapon(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        String title = WeaponMechanicsAPI.getWeaponTitle(item);
        return title != null && cfg.hasMarkRulesFor(title);
    }

    @Override
    public boolean isMarked(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        return inv.isWeaponMarked(item); // inv method is null-safe too
    }

    @Override
    public @NotNull MarkResult tryMark(@NotNull Player p, @NotNull ItemStack item) {
        String title = WeaponMechanicsAPI.getWeaponTitle(item);
        if (title == null || !cfg.hasMarkRulesFor(title)) {
            return MarkResult.deny(DenyReason.NOT_MANAGED);
        }

        if (item.getAmount() > 1) {
            return MarkResult.deny(DenyReason.STACKED_ITEM);
        }

        var cr = plugin.getCombatRestrictionsManager();
        if (cr != null && cr.isEnabled() && cr.isInCombat(p)) {
            // 1) Optional global block for any marking in combat
            if (cfg.isBlockMarkingInCombat()) {
                return MarkResult.deny(DenyReason.MARK_BLOCKED_IN_COMBAT);
            }
            // 2) Optional block for re-marking titles unmarked during combat
            if (cfg.isBlockRemarkingInCombat() && cr.isMarkingBlocked(p, title)) {
                return MarkResult.deny(DenyReason.REMARK_BLOCKED_IN_COMBAT);
            }
        }

        // Pre-event (external plugins can veto)
        WeaponPreMarkEvent pre = new WeaponPreMarkEvent(p, item);
        Bukkit.getPluginManager().callEvent(pre);
        if (pre.isCancelled()) {
            return MarkResult.deny(pre.getDenyReason() != null ? pre.getDenyReason() : DenyReason.UNKNOWN_ERROR);
        }

        // Group checks
        var groups = cfg.getGroupsForWeapon(title);
        boolean alreadyMarked = inv.isWeaponMarked(item);

        for (var g : groups) {
            if (g.mode == ConfigManager.GroupMode.EXCLUSIVE) {
                for (String member : g.members) {
                    if (member.equalsIgnoreCase(title)) continue;
                    if (inv.countMarkedWeapons(p, member) > 0) {
                        return MarkResult.deny(DenyReason.EXCLUSIVE_CONFLICT);
                    }
                }
            } else { // POOL
                int total = cfg.countMarkedInGroup(p, g, inv);
                int nextTotal = total + (alreadyMarked ? 0 : 1);
                int groupLimit = g.limit <= 0 ? Integer.MAX_VALUE : g.limit;
                if (nextTotal > groupLimit) return MarkResult.deny(DenyReason.POOL_TOTAL_LIMIT);

                int currentOfThis = inv.countMarkedWeapons(p, title);
                int nextOfThis = currentOfThis + (alreadyMarked ? 0 : 1);
                int cap = cfg.effectiveMemberPoolCap(title, g);
                if (nextOfThis > cap) return MarkResult.deny(DenyReason.POOL_MEMBER_CAP);
            }
        }

        // Per-weapon limit
        int have = inv.countMarkedWeapons(p, title);
        int limit = cfg.getWeaponLimit(title);
        if (!alreadyMarked && have >= limit) return MarkResult.deny(DenyReason.PER_WEAPON_LIMIT);

        // OK -> mark (fire event only when we actually changed state)
        if (!alreadyMarked) {
            inv.markWeapon(item);
            Bukkit.getPluginManager().callEvent(new WeaponMarkedEvent(p, item, title));
        }
        return MarkResult.ok();
    }

    @Override
    public @NotNull UnmarkResult unmark(@Nullable Player actor, @NotNull ItemStack item, @NotNull UnmarkCause cause) {
        boolean before = inv.isWeaponMarked(item);
        if (before) {
            String title = WeaponMechanicsAPI.getWeaponTitle(item);
            inv.unmarkWeapon(item);

            var cr = plugin.getCombatRestrictionsManager();
            if (actor != null && cr != null && cr.isEnabled() && cr.isInCombat(actor)
                    && title != null && cfg.isBlockRemarkingInCombat()) {
                cr.flagUnmarkDuringCombat(actor, title);
            }

            Bukkit.getPluginManager().callEvent(new WeaponUnmarkedEvent(actor, item, title, cause));
        }
        return new UnmarkResult(before);
    }

    @Override
    public @NotNull UnmarkResult unmark(@NotNull ItemStack item, @NotNull UnmarkCause cause) {
        return unmark(null, item, cause);
    }

    @Override
    public int countMarked(@NotNull Player player, @NotNull String weaponTitle) {
        return inv.countMarkedWeapons(player, weaponTitle);
    }

    @Override
    public int getWeaponLimit(@NotNull String weaponTitle) {
        return cfg.getWeaponLimit(weaponTitle);
    }

    @Override
    public @NotNull Set<@NotNull GroupInfo> getGroupsFor(@NotNull String weaponTitle) {
        return cfg.getGroupsForWeapon(weaponTitle).stream()
                .map(g -> new GroupInfo(
                        g.name,
                        g.mode == ConfigManager.GroupMode.POOL ? GroupMode.POOL : GroupMode.EXCLUSIVE,
                        g.limit <= 0 ? Integer.MAX_VALUE : g.limit,
                        cfg.effectiveMemberPoolCap(weaponTitle, g)
                ))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean hasWeightRules() {
        return cfg.hasAnyWeightGroups();
    }

    @Override
    public @NotNull WeightUsage getWeightUsage(@NotNull UUID playerId) {
        int indiv = plugin.getWeightManager().getTotalUsedForWeapons(playerId);
        int group = plugin.getWeightManager().getTotalUsedForGroups(playerId);
        return new WeightUsage(indiv, group);
    }
}