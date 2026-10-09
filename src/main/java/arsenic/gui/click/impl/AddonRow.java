package arsenic.gui.click.impl;

import arsenic.addon.AddonManager;
import arsenic.module.Module;

/** One addon's module row, kept across layouts. It is rebuilt when the addon's loaded module changes (a reload). */
final class AddonRow {

    private final AddonSource source;
    private ModuleComponent component;
    private Module built;

    AddonRow(AddonManager.Info info) {
        source = new AddonSource(info);
    }

    void update(AddonManager.Info info) {
        source.update(info);
    }

    ModuleComponent component() {
        Module now = source.module();
        if (component == null || now != built) {
            component = new ModuleComponent(source);
            built = now;
        }
        return component;
    }
}
