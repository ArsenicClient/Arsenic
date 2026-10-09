package arsenic.gui.click.impl;

import java.util.List;

import arsenic.module.Module;
import arsenic.module.ModuleTier;
import arsenic.module.property.Property;

/** What a module row shows and changes. A loaded module is one; an addon is another, so both rows are the same. */
public interface ModuleSource {

    String getName();

    String getDescription();

    ModuleTier getTier();

    boolean isEnabled();

    void setEnabled(boolean enabled);

    int getKeybind();

    void setKeybind(int key);

    boolean isHidden();

    void setHidden(boolean hidden);

    List<? extends Property<?>> getProperties();

    static ModuleSource of(Module module) {
        return new ModuleSource() {
            @Override
            public String getName() { return module.getName(); }

            @Override
            public String getDescription() { return module.getDescription(); }

            @Override
            public ModuleTier getTier() { return module.getTier(); }

            @Override
            public boolean isEnabled() { return module.isEnabled(); }

            @Override
            public void setEnabled(boolean enabled) { module.setEnabled(enabled); }

            @Override
            public int getKeybind() { return module.getKeybind(); }

            @Override
            public void setKeybind(int key) { module.setKeybind(key); }

            @Override
            public boolean isHidden() { return module.isHidden(); }

            @Override
            public void setHidden(boolean hidden) { module.setHidden(hidden); }

            @Override
            public List<? extends Property<?>> getProperties() { return module.getProperties(); }
        };
    }
}
