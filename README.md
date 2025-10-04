# WMInventoryControl

**WMInventoryControl** is an add-on for **WeaponMechanics** that enforces per-player weapon rules and blocks edge-case exploits.<br>
It uses a namespaced **PDC mark** to tag a certain amount of weapons from the player's inventory and only those **marked** weapons are allowed to fire.

- **Mark on first use** (main or off-hand) during WeaponMechanics’ pre-shoot stage.
- **Managed titles** are marked on use; if marking is denied, **the shot is cancelled**.
- **Weight-only titles** (e.g. `GRENADE` when present only in weight rules) are **not marked** and behave normally.
- **Unmark** when items truly leave the player’s control (drops, real containers, frames, admin transfers, death, etc.).
- **Limits & Groups** (per-weapon caps, `EXCLUSIVE`, `POOL` with per-member caps).
- **Combat Weight** (with CombatLogX) to restrict heavy loadouts **only while in combat**.
- **Configurable combat restrictions** (block *marking*, *re-marking*, *dropping*, and *moving* marked weapons while in combat).
- **/invsee-safe** transfers with pre- / post-handling and temp tags.
- **Per-listener debug toggles** so you get only the logs you want.

---

## Requirements

- **Java 21**
- **Paper 1.21+** (uses Paper APIs like `PlayerArmorChangeEvent` and `DecoratedPotInventory`)
- **WeaponMechanics** (hard-depend)
- **CombatLogX** *(soft-depend; enables combat/weight features)*
- **BlueSlimeCore** *(soft-depend; only if your stack uses it)*

---

## Installation

1. Drop **WMInventoryControl** and **WeaponMechanics** into `/plugins` (add **CombatLogX** if you’ll use weights).
2. Start the server to generate `plugins/WMInventoryControl/config.yml`.
3. Tweak the config (limits, groups, containers, weight groups).
4. Run `/wmic reload` anytime to apply changes.

> **Command & Permission**  
> `/wmic reload` — reload the plugin configuration  
> Permission: `wmic.reload`

---

## How it works (Mark → Check → Unmark)

### Mark
When a **managed** title is fired for the first time, WMIC writes a namespaced **PDC mark** on the held item (supports both main-hand and off-hand). Marking stacked items is denied.

### Check
- Managed items must be (or become) **marked** to fire. If `tryMark(...)` denies (limits/groups/combat), **the shot is cancelled** with a player-facing reason.
- Titles that are **not managed** (e.g. appear only in weight rules) **are not marked** and aren’t affected by per-weapon/group caps.
- If you enable combat restrictions, marking may be denied with `MARK_BLOCKED_IN_COMBAT`, and re-marking the same title you unmarked during combat may be denied with `REMARK_BLOCKED_IN_COMBAT` until you are untagged.

### Unmark (trusted exits only)
Unmark happens **only when** an item truly leaves the player or a rule says it should be cleared:
- **Drops**
- Put into **real containers** (chest, barrel, shulker, hopper, etc.)
- **Item frames** (in/out)
- **/invsee** transfers (pre/post flow; safe for admins)
- **Death** (drops and, if `keepInventory`, items remaining in inventory)
- **Bundles** (guard + cleanup)
- **Decorated Pot** (checked next tick because of vanilla quirks)
- **Hat guard** (prevents equipping marked weapons as helmets, including `/hat`, and restores previous helmet)

> The plugin only unmarks after a successful placement or a trusted exit; attempts that bounce back (e.g. to recipe/utility UIs) are cancelled instead so items stay marked.

---

## Limits & Groups

### Per-weapon limits
A hard cap per WeaponMechanics **title**. Extras may exist but won’t be shootable (mark is denied).

### Group modes
- **EXCLUSIVE** — player may have **only one** marked item from the group.
- **POOL** — the group has a **shared limit,** and you can set **per-member caps**; the plugin enforces both the pool’s total and each member’s cap.

---

## Combat Weight (with CombatLogX)

While a player is **in combat**:

- **INDIVIDUAL**: every copy of a title **adds its cost**.
- **SHARED_POOL**: each title **counts once** (first use during the current combat).
- **Auto-reset on untag** if `remove-limit-post-combat: true`.
- If a player **unmarks** a title during combat, **re-marking that title is blocked** until they are untagged (anti-swap abuse).

### Out-of-combat reset behavior
You control how per-combat weights reset once a player leaves combat:

- `remove-limit-post-combat: true` → **reset immediately** on untag (this is the default).
- `remove-limit-post-combat: false` + `weight-reset-timeout-seconds: 0` → **persist indefinitely** across fights; only resets on **death/logout/server restart**.
- `remove-limit-post-combat: false` + `weight-reset-timeout-seconds: N` → reset **after N seconds out of combat**. The timer is canceled if the player re-enters combat before it expires.

This lets you choose between strict reset-on-untag, a grace period, or “sticky” weights for hardcore servers.

---

## Containers policy

Two lists control behavior:

- **regular** — real storage; placement **allowed**, weapon **unmarked** after it actually lands.
- **non-accepting** — utility/recipe UIs that typically reject random items; we **cancel** attempts to place **marked** weapons so you don’t get “bounced-back but unmarked”.

> Defaults treat **FURNACE/SMOKER/BLAST_FURNACE** as **non-accepting** to avoid unmarking on rejected placements. Customize as needed.

---

## Event hygiene & coverage

- Proper **priorities** (`HIGH` for checks, `MONITOR` for accrual/marking), and **ignoreCancelled** where appropriate.
- Covers **shift-click**, **drag**, and **number-key** flows.
- Handles admin manually transferring items from their inventories to regular players, frames, hats, decorated pots, bundles, death (with/without `keepInventory`), and combat transitions.

**Main listeners**
- `WeaponShootListener` (mark on pre-shoot; enforce)
- `WeightCheckListener` / `WeightAccrualListener`
- `ContainerUnmarkListener` / `SpecialContainerListener`
- `ItemFrameListener`
- `PlayerDeathListener` / `PlayerQuitListener`
- `InvseeTransferListener`
- `HatGuardListener`
- `CombatEndListener`

---

## Configuration (reference template)

```yml
# Per-listener debug toggles (ALL default to false)
debug-mode:
  weapon-shoot:        false
  container-unmark:    false
  container-block:     false
  invsee-transfer:     false
  player-drop:         false
  item-frame:          false
  player-death:        false
  special-container:   false
  hat-guard:           false
  auction:             false
  groups:              false
  combat:              false
  config-manager:      false
  inventory-manager:   false
  weight:              false
  combat-end:          false

# Containers
containers:

  # REGULAR CONTAINERS — real storage that accepts arbitrary items.
  # We allow placement and UNMARK once the item actually goes in.
  # Notes:
  # - ENDER_CHEST: persistent storage per player. Still considered a real container.
  # - SHULKER_BOX: allows putting items inside.
  # - DECORATED_POT: commented out (UI quirks); handle via a special listener if needed.
  # - PLAYER/CRAFTING/CREATIVE are NOT containers.
  regular:
    - CHEST
    - BARREL
    - ENDER_CHEST
    - SHULKER_BOX
    - HOPPER
    - DISPENSER
    - DROPPER
    # - DECORATED_POT  # special-cased in code

  # NON-ACCEPTING CONTAINERS — utility/recipe UIs that reject most items (like feathers).
  # We CANCEL any attempt to place a MARKED weapon here and DO NOT unmark.
  # This avoids the "unmarked but bounced back" problem.
  # Notes:
  # - WORKBENCH: classic crafting table. You usually don't allow marked weapons in recipes
  #   anyway; add it here if you want to hard-cancel attempts.
  # - JUKEBOX: accepts only discs; add here if you want to hard-cancel attempts for weapons.
  non-accepting:
    - BEACON
    - ENCHANTING
    - ANVIL
    - GRINDSTONE
    - CARTOGRAPHY
    - LOOM
    - SMITHING
    - STONECUTTER
    - MERCHANT
    - BREWING
    - FURNACE
    - SMOKER
    - BLAST_FURNACE

# Auction commands that we treat as selling (for your flows)
auction-commands:
  - ah
  - auction
  - auc
  - auchand

# Per-weapon hard caps (by WeaponMechanics weapon title)
weapon-limits:
  AK_47: 1
  M4A1:  1
  AUG:   2

# Groups — EXCLUSIVE or POOL (with optional per-member caps)
groups:
  Primary_Exclusive:
    type: EXCLUSIVE
    members:
      - AK_47
      - M4A1

  Pistols:
    type: POOL
    limit: 3
    members:
      Glock_17: 1
      50_GS:    2

# Weight rules (CombatLogX only) — active while tagged in combat
weight-groups:
  Individual_Explosives:
    type: INDIVIDUAL
    maximum_limit: 80
    items:
      GRENADE:   10
      FLASHBANG: 15
      MOLOTOV:   25

  Collective_Group_Explosives:
    type: SHARED_POOL
    maximum_limit: 5
    items:
      GRENADE:   2
      MOLOTOV:   2
      FLASHBANG: 3

# when combat ends, clear per-combat weights
# true  -> weights reset immediately on untag (timeout is ignored)
# false -> weights persist; see weight-reset-timeout-seconds below
remove-limit-post-combat: true

# Optional timeout reset while out of combat.
# If > 0 AND remove-limit-post-combat is FALSE, WMIC clears the weight
# after the player has been OUT OF COMBAT for this many seconds.
# Set to 0 to disable the timeout.
weight-reset-timeout-seconds: 0

# Optional: combat-time interaction restrictions (CombatLogX)
combat-restrictions:
  block-marking-in-combat: false         # deny first-time marking while in combat
  block-re-marking-in-combat: true       # deny re-marking titles unmarked during the same combat
  block-dropping-in-combat: true         # deny dropping marked weapons while in combat
  block-moving-marked-in-combat: true    # deny moving marked weapons (shift/drag/number-key) while in combat
```
---
## Debug

Toggle specific areas under `debug-mode.*` (all default to `false`). Common keys:

`weapon-shoot`, `container-unmark`, `container-block`, `invsee-transfer`, `player-drop`, `item-frame`, `player-death`, `special-container`, `hat-guard`, `auction`, `groups`, `combat`, `config-manager`, `inventory-manager`, `weight`, `combat-end`, `config-reload`.

---

## API for developers

WMIC exposes a **Bukkit service** you can fetch at runtime:

```java
package your.plugin.package;

import destroier.WMInventoryControl.api.WMIC;
import destroier.WMInventoryControl.api.WMICApi;
import destroier.WMInventoryControl.api.types.DenyReason;
import destroier.WMInventoryControl.api.types.MarkResult;
import destroier.WMInventoryControl.api.types.UnmarkCause;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class WMICExampleUsage {

    // You can cache the service once; WMIC.api() handles lookup + caching.
    private final WMICApi api = WMIC.api();

    public void demo(Player player, ItemStack item) {
        // Mark on demand (returns MarkResult with DenyReason on failure)
        MarkResult res = api.tryMark(player, item);
        if (!res.allowed()) {
            DenyReason r = res.reason();
            // handle: r can be NOT_MANAGED, STACKED_ITEM, EXCLUSIVE_CONFLICT,
            // POOL_TOTAL_LIMIT, POOL_MEMBER_CAP, PER_WEAPON_LIMIT,
            // MARK_BLOCKED_IN_COMBAT, REMARK_BLOCKED_IN_COMBAT, ...
            return;
        }

        // Unmark with an explicit cause (player may be null for non-player flows)
        api.unmark(player, item, UnmarkCause.PLAYER_DROP);

        // Queries
        boolean managed = api.isManagedWeapon(item);
        boolean marked  = api.isMarked(item);
        int have        = api.countMarked(player, "AK_47");
        int limit       = api.getWeaponLimit("AK_47");
    }
}
```
Call the API from the main server thread.

## Events
- **WeaponPreMarkEvent** — fired before marking; cancellable. You may also attach a DenyReason to explain why.
- **WeaponMarkedEvent** — fired after a successful mark.
- **WeaponUnmarkedEvent** - fired after an unmark (includes the UnmarkCause).

Example: veto marking under your own condition
```java
@EventHandler
package your.plugin.package;

import destroier.WMInventoryControl.api.events.WeaponPreMarkEvent;
import destroier.WMInventoryControl.api.types.DenyReason;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class WMICExampleListener implements Listener {

    @EventHandler
    public void onPreMark(WeaponPreMarkEvent e) {
        // Example: veto marking under your own condition
        if (/* your condition */ false) {
            e.setDenyReason(DenyReason.PER_WEAPON_LIMIT);
            e.setCancelled(true);
        }
    }
}
```

## Unmark causes (reference)

Some commonly-used enum values you may observe in **WeaponUnmarkedEvent**:

`PLAYER_DROP`, `PUT_IN_CONTAINER`, `INVSEE_TRANSFER`, `ITEM_FRAME`,
`DEATH_DROP`, `DEATH_KEEP_INVENTORY`, `DECORATED_POT`, `SPECIAL_CONTAINER`,
`HAT_GUARD`, `API`, `OTHER`.

## Admin notes
- **/invsee** paths use pre/post listeners + temporary PDC tags so items end up unmarked for admins and can be safely redistributed.
- Guard rails avoid dupes when shuffling items via number keys or drag operations.
- With **keepInventory**, the plugin will unmark weapons that remain in the inventory after death where appropriate.
- You can **/wmic** reload to apply config changes without a full restart.

## License & Credits
- Author: DestroierMariusM / DMariusM
- Depends on WeaponMechanics
- Soft-depends: CombatLogX, BlueSlimeCore

## Acknowledgments
- WeaponMechanics by CJCrafter & DeeCaaD — thanks for the API and plugin.
- Thanks to the Paper/Bukkit ecosystem.

## Support
If you open an issue, please include:
- Paper, WMIC, WeaponMechanics, and (if used) CombatLogX versions
- Your relevant config sections (weapon-limits, groups, weight-groups, containers)
- What you did, what you expected, and what happened (plus any WMIC debug logs if you toggled them)