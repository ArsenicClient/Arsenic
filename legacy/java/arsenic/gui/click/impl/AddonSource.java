package arsenic.gui.click.impl;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import arsenic.addon.AddonManager;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleTier;
import arsenic.module.property.Property;

/**
 * An addon as a module row. Every value comes from its module, so the row behaves like any module's. Addons are only
 * loaded at start-up, on injection, by Reload addons and by .addon reload; until then (or when it fails to compile)
 * the row has no module, its switch stays off and the dropdown says so.
 */
final class AddonSource implements ModuleSource {

    private AddonManager.Info info;

    AddonSource(AddonManager.Info info) {
        this.info = info;
    }

    void update(AddonManager.Info info) {
        this.info = info;
    }

    /** The loaded module, or null while the addon is not loaded. */
    Module module() {
        return Arsenic.getArsenic().getAddonManager().findLoadedModule(info.name);
    }

    static boolean matches(AddonManager.Info info, String query) {
        return contains(info.name, query) || contains(info.description, query)
                || (info.pack != null && contains(info.pack.name, query));
    }

    private static boolean contains(String text, String query) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(query);
    }

    @Override
    public String getName() {
        return info.name;
    }

    @Override
    public String getDescription() {
        Module module = module();
        return module != null ? module.getDescription() : info.description
                + " (Not loaded: press Reload addons, or see Errors if it does not compile.)";
    }

    @Override
    public ModuleTier getTier() {
        Module module = module();
        return module != null ? module.getTier() : ModuleTier.LEGIT;
    }

    @Override
    public boolean isEnabled() {
        Module module = module();
        return module != null && module.isEnabled();
    }

    @Override
    public void setEnabled(boolean enabled) {
        Module module = module();
        if (module != null) {
            module.setEnabled(enabled);
            return;
        }
        // a bundled addon not in the folder yet: put it there so the next reload loads it
        if (enabled && info.state == AddonManager.State.AVAILABLE) {
            try {
                Arsenic.getArsenic().getAddonManager().setEnabled(info, true);
            } catch (java.io.IOException e) {
                Arsenic.getArsenic().getLogger().error("Could not install " + info.name, e);
            }
        }
    }

    @Override
    public int getKeybind() {
        return Arsenic.getArsenic().getAddonManager().getKeybind(info.name);
    }

    @Override
    public void setKeybind(int key) {
        Arsenic.getArsenic().getAddonManager().setKeybind(info.name, key);
    }

    @Override
    public boolean isHidden() {
        Module module = module();
        return module != null && module.isHidden();
    }

    @Override
    public void setHidden(boolean hidden) {
        Module module = module();
        if (module != null)
            module.setHidden(hidden);
    }

    @Override
    public List<? extends Property<?>> getProperties() {
        Module module = module();
        return module != null ? module.getProperties() : Collections.<Property<?>>emptyList();
    }
}
