package destroier.WMInventoryControl.api;

import org.bukkit.Bukkit;

/** Convenience accessor for the WMICApi service. */
public final class WMIC {
    private static WMICApi cached;

    public static WMICApi api() {
        WMICApi svc = cached;
        if (svc == null) {
            svc = Bukkit.getServicesManager().load(WMICApi.class);
            if (svc == null) throw new IllegalStateException("WMICApi service not available");
            cached = svc;
        }
        return svc;
    }

    private WMIC() {}
}