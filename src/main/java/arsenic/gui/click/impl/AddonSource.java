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
 * An addon as a module row. Every value comes from its module, so the row behaves like any module's. An addon that is
 * not loaded yet is loaded as soon as the row needs its module: when it is switched on, opened to show its settings,
 * bound or hidden.
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

    /** The module, loading the addon first if needed. Null when it does not load. */
    private Module load() {
        return Arsenic.getArsenic().getAddonManager().loadAddon(info);
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
        return module != null ? module.getDescription() : info.description;
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
        Module module = enabled ? load() : module();
        if (module != null)
            module.setEnabled(enabled);
    }

    @Override
    public int getKeybind() {
        return Arsenic.getArsenic().getAddonManager().getKeybind(info.name);
    }

    @Override
    public void setKeybind(int key) {
        if (key != 0)
            load();
        Arsenic.getArsenic().getAddonManager().setKeybind(info.name, key);
    }

    @Override
    public boolean isHidden() {
        Module module = module();
        return module != null && module.isHidden();
    }

    @Override
    public void setHidden(boolean hidden) {
        Module module = load();
        if (module != null)
            module.setHidden(hidden);
    }

    @Override
    public void prepare() {
        load();
    }

    @Override
    public List<? extends Property<?>> getProperties() {
        Module module = module();
        return module != null ? module.getProperties() : Collections.<Property<?>>emptyList();
    }
}
