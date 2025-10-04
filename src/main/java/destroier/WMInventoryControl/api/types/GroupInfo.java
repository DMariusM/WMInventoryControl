package destroier.WMInventoryControl.api.types;

/** Basic read-only description of a group a title belongs to. */
public record GroupInfo(
        String name,
        GroupMode mode,
        int poolLimit,     // Integer.MAX_VALUE if unlimited
        int memberCap      // Integer.MAX_VALUE if unlimited, or specific cap for this title
) {}