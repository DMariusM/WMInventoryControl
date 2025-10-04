package destroier.WMInventoryControl.api.types;

/** Business reasons a mark attempt may be denied. */
public enum DenyReason {
    NOT_MANAGED,
    STACKED_ITEM,
    EXCLUSIVE_CONFLICT,
    POOL_TOTAL_LIMIT,
    POOL_MEMBER_CAP,
    PER_WEAPON_LIMIT,
    REMARK_BLOCKED_IN_COMBAT,
    MARK_BLOCKED_IN_COMBAT,
    UNKNOWN_ERROR
}