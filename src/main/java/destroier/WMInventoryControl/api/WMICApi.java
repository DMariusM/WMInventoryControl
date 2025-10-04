package destroier.WMInventoryControl.api;

import destroier.WMInventoryControl.api.types.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * Public service interface for WMInventoryControl.
 * All methods must be called from the main server thread.
 */
public interface WMICApi {

    /** Returns true if the item is a WeaponMechanics weapon managed by WMIC rules. */
    boolean isManagedWeapon(@Nullable ItemStack item);

    /** Returns true if the given item is marked by WMIC. */
    boolean isMarked(@Nullable ItemStack item);

    /**
     * Validate rules and (if allowed) mark the item in-place.
     * Never changes the item on failure.
     */
    @NotNull MarkResult tryMark(@NotNull Player player, @NotNull ItemStack item);

    /**
     * Unmark the item in-place.
     * Returns whether a mark was removed.
     */
    @NotNull UnmarkResult unmark(@NotNull ItemStack item, @NotNull UnmarkCause cause);

    @NotNull UnmarkResult unmark(@Nullable Player actor, @NotNull ItemStack item, @NotNull UnmarkCause cause);

    /** Count how many marked items of the given WM title the player holds. */
    int countMarked(@NotNull Player player, @NotNull String weaponTitle);

    /** Per-title limit (Integer.MAX_VALUE if unlimited). */
    int getWeaponLimit(@NotNull String weaponTitle);

    /** Read-only group info for this title (may be empty). */
    @NotNull Set<@NotNull GroupInfo> getGroupsFor(@NotNull String weaponTitle);

    /** Whether any weight groups are configured. */
    boolean hasWeightRules();

    /** Immutable snapshot of weight usage for this player. */
    @NotNull WeightUsage getWeightUsage(@NotNull UUID playerId);
}