package arsenic.module;

import arsenic.main.Arsenic;
import arsenic.module.property.Property;
import arsenic.utils.interfaces.IConfigurable;
import arsenic.utils.interfaces.IContainable;
import arsenic.utils.interfaces.IContainer;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Module implements IConfigurable, IContainable, IContainer {

    protected static final Minecraft mc = Minecraft.getMinecraft();
    protected static final Arsenic client = Arsenic.getInstance();

    private final String name, description;
    private final ModuleCategory category;
    private boolean enabled, hidden;
    private String displayName;
    private int keybind;

    private boolean registered;

    private final List<Property<?>> properties = new ArrayList<>();

    public Module() {
        if(!this.getClass().isAnnotationPresent(ModuleInfo.class))
            throw new IllegalArgumentException("No @ModuleInfo on class " + this.getClass().getCanonicalName());

        ModuleInfo info = this.getClass().getDeclaredAnnotation(ModuleInfo.class);

        this.name = info.name();
        this.displayName = this.name;
        this.description = info.description();
        this.category = info.category();
        this.enabled = info.enabled();
        this.hidden = info.hidden();
        this.keybind = info.keybind();

        if(this.enabled) {
            setEnabledSilently(true);
        }
    }

    protected void onEnable() {}
    protected void onDisable() {}

    public final String getName() {
        return name;
    }

    public final String getDescription() {
        return description;
    }

    public final ModuleCategory getCategory() {
        return category;
    }

    public final boolean isEnabled() {
        return enabled;
    }

    public final void setEnabled(boolean enabled) {
        if(this.enabled != enabled) {
            this.enabled = enabled;

            if(enabled) {
                onEnable();

                // prevents registering when it gets disabled on enable
                if(this.enabled) {
                    Arsenic.getInstance().getEventManager().subscribe(this);
                    this.registered = true;
                }
            } else {
                if(this.registered) {
                    this.registered = false;
                    Arsenic.getInstance().getEventManager().unsubscribe(this);
                }

                onDisable();
            }
        }
    }

    public final void setEnabledSilently(boolean enabled) {
        if(this.enabled != enabled) {
            this.enabled = enabled;

            if(enabled) {
                Arsenic.getInstance().getEventManager().subscribe(this);
                this.registered = true;
            } else if(this.registered) {
                this.registered = false;
                Arsenic.getInstance().getEventManager().unsubscribe(this);
            }
        }
    }

    @Override
    public Collection<IContainable> getContents() {
        return new ArrayList<>(properties);
    }

    public final boolean isHidden() {
        return hidden;
    }

    public final void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public final String getDisplayName() {
        return displayName;
    }

    public final void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public final int getKeybind() {
        return keybind;
    }

    public final void setKeybind(int keybind) {
        this.keybind = keybind;
    }

    public final List<Property<?>> getProperties() {
        return properties;
    }

}
