package destroier.WMInventoryControl.api.types;

/**  */
public record WeightUsage(int individualUsed, int groupUsed) {
    public WeightUsage {
        if (individualUsed < 0 || groupUsed < 0)
            throw new IllegalArgumentException("Weights cannot be negative");
    }
}