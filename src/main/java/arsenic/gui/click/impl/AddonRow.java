package arsenic.gui.click.impl;

import java.util.HashMap;
import java.util.Map;

import arsenic.addon.AddonManager;
import arsenic.module.Module;

/**
 * One addon's module row. There is one per addon, shared by the Addon Manager and search and kept across reloads, so
 * a row stays open while its addon loads. Its settings are rebuilt when the addon's loaded module changes.
 */
final class AddonRow {

    private static final Map<String, AddonRow> ROWS = new HashMap<>();

    private final AddonSource source;
    private ModuleComponent component;
    private Module built;

    private AddonRow(AddonManager.Info info) {
        source = new AddonSource(info);
    }

    /** The row for an addon, pointed at the latest info read from disk. */
    static AddonRow of(AddonManager.Info info) {
        String key = (info.pack == null ? "" : info.pack.id) + "/" + info.name;
        AddonRow row = ROWS.get(key);
        if (row == null)
            ROWS.put(key, row = new AddonRow(info));
        else
            row.source.update(info);
        return row;
    }

    ModuleComponent component() {
        Module now = source.module();
        if (component == null) {
            component = new ModuleComponent(source);
            built = now;
        } else if (now != built) {
            component.refreshContents();
            built = now;
        }
        return component;
    }
}
