package destroier.WMInventoryControl.api.types;

/** Result of a mark attempt. If allowed=true, the item was marked. */
public record MarkResult(boolean allowed, DenyReason reason) {
    public static MarkResult ok() {
        return new MarkResult(true, null);
    }

    public static MarkResult deny(DenyReason r) {
        return new MarkResult(false, r);
    }
}