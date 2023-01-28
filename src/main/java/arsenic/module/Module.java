package arsenic.module;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import arsenic.module.property.SerializableProperty;
import com.google.gson.JsonObject;

import arsenic.main.Arsenic;
import arsenic.module.property.Property;
import arsenic.utils.interfaces.IContainable;
import arsenic.utils.interfaces.IContainer;
import arsenic.utils.interfaces.ISerializable;
import net.minecraft.client.Minecraft;

public class Module implements IContainable, IContainer, ISerializable {

    protected static final Minecraft mc = Minecraft.getMinecraft();
    protected static final Arsenic client = Arsenic.getInstance();

    private final String name, description;
    private final ModuleCategory category;
    private boolean enabled, hidden;
    private String displayName;
    private int keybind;

    private boolean registered;

    private final List<Property<?>> properties = new ArrayList<>();
    private final List<ISerializable> serializableProperties = new ArrayList<>();

    public Module() {
        if (!this.getClass().isAnnotationPresent(ModuleInfo.class))
            throw new IllegalArgumentException("No @ModuleInfo on class " + this.getClass().getCanonicalName());

        final ModuleInfo info = this.getClass().getDeclaredAnnotation(ModuleInfo.class);

        name = info.name();
        displayName = name;
        description = info.description();
        category = info.category();
        enabled = info.enabled();
        hidden = info.hidden();
        keybind = info.keybind();

        if (info.enabled()) {
            setEnabledSilently(true);
        }
    }

    public void registerProperties() {
        for (final Field field : getClass().getFields()) {
            try {
                Object p = field.get(this);
                properties.add((Property<?>) p);
                serializableProperties.add((SerializableProperty<?>) p);
            } catch (final IllegalAccessException | ClassCastException e) {
                //ignored
            }
        }
    }


    protected void onEnable() {
    }

    protected void onDisable() {
    }

    @Override
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
        if (this.enabled != enabled) {
            this.enabled = enabled;

            if (enabled) {
                onEnable();

                // prevents registering when it gets disabled on enable
                if (this.enabled) {
                    Arsenic.getInstance().getEventManager().subscribe(this);
                    registered = true;
                }
            } else {
                if (registered) {
                    registered = false;
                    Arsenic.getInstance().getEventManager().unsubscribe(this);
                }

                onDisable();
            }
        }
    }

    public final void setEnabledSilently(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;

            if (enabled) {
                Arsenic.getInstance().getEventManager().subscribe(this);
                registered = true;
            } else if (registered) {
                registered = false;
                Arsenic.getInstance().getEventManager().unsubscribe(this);
            }
        }
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

    @Override
    public Collection<IContainable> getContents() {
        return new ArrayList<>(properties);
    }

    public final List<Property<?>> getProperties() {
        return properties;
    }

    @Override
    public void loadFromJson(JsonObject obj) {
        serializableProperties.forEach(property -> {
            property.loadFromJson(obj.getAsJsonObject(property.getJsonKey()));
        });
    }

    @Override
    public JsonObject saveInfoToJson(JsonObject obj) {
        serializableProperties.forEach(property -> {
            property.addToJson(obj);
        });
        return obj;
    }

    @Override
    public JsonObject addToJson(JsonObject obj) {
        final JsonObject config = new JsonObject();
        saveInfoToJson(config);
        obj.add(name, config);
        return obj;
    }

    @Override
    public String getJsonKey() {
        return name;
    }
}
