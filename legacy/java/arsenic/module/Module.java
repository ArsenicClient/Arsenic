package arsenic.module;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.gson.JsonObject;

import arsenic.command.Command;
import arsenic.gui.hud.HudElement;
import arsenic.main.Arsenic;
import arsenic.module.property.Property;
import arsenic.module.property.PropertyGroup;
import arsenic.module.property.Visibility;
import arsenic.module.property.SerializableProperty;
import arsenic.notifications.Notification;
import arsenic.notifications.NotificationManager;
import arsenic.notifications.NotificationType;
import arsenic.utils.interfaces.IContainer;
import arsenic.utils.interfaces.ISerializable;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

public class Module implements IContainer<Property<?>>, ISerializable {

    protected static final Minecraft mc = Minecraft.getMinecraft();
    protected static final Arsenic client = Arsenic.getInstance();

    private final String name;
    private final String description;
    private final ModuleCategory category;
    private final ModuleTier tier;
    private boolean enabled;
    private boolean hidden;
    private String displayName;
    private int keybind;
    /** Set for modules that came from an addon. They stay out of the category lists and appear in search and the Addon Manager. */
    private boolean addon;

    private boolean registered;

    private final List<Property<?>> properties = new ArrayList<>();
    protected final List<SerializableProperty<?>> serializableProperties = new ArrayList<>();
    private final List<HudElement> hudElements = new ArrayList<>();
    private final List<Command> commands = new ArrayList<>();
    private final List<String> propertyProblems = new ArrayList<>();

    public Module() {
        if (!this.getClass().isAnnotationPresent(ModuleInfo.class))
            throw new IllegalArgumentException("No @ModuleInfo on class " + this.getClass().getCanonicalName());

        final ModuleInfo info = this.getClass().getDeclaredAnnotation(ModuleInfo.class);

        name = info.name();
        displayName = name;
        description = info.description();
        category = info.category();
        tier = info.tier();
        hidden = info.hidden();
        keybind = info.keybind();

        if (info.enabled()) {
            enabled = true;
        }
    }


    protected void registerProperty(Property<?> p) {
        properties.add(p);
    }

    /** Registers a draggable HUD spot for this module; it appears in the HUD editor and is saved with the module. */
    protected final HudElement hudElement(String label, int x, int y, int width, int height) {
        return hudElement(label, x, y, width, height, false);
    }

    protected final HudElement hudElement(String label, int x, int y, int width, int height, boolean rightAnchored) {
        HudElement element = new HudElement(label, x, y, width, height, rightAnchored);
        hudElements.add(element);
        return element;
    }

    /** Registers a chat command that exists while this module is loaded (used by addons). */
    protected final void registerCommand(Command command) {
        commands.add(command);
    }

    public final List<Command> getCommands() {
        return commands;
    }

    /** While this module is enabled, TargetManager only accepts targets this returns true for. */
    public boolean allowsTarget(EntityPlayer player) {
        return true;
    }

    /** True while the module is moving items around the hotbar, so AutoWeapon keeps its hands off. */
    public boolean isSwappingHotbar() {
        return false;
    }

    public final List<HudElement> getHudElements() {
        return hudElements;
    }

    public void registerProperties() throws IllegalAccessException {
        if (enabled && !registered) {
            Arsenic.getInstance().getEventManager().subscribe(this);
            registered = true;
        }
        for (final Field field : getClass().getFields()) {
            Object fieldObject = field.get(this);
            if (!(fieldObject instanceof Property<?>) || fieldObject == null)
                continue;

            Property<?> property = (Property<?>) fieldObject;
            PropertyGroup group = field.getAnnotation(PropertyGroup.class);
            if (group != null)
                property.setGroup(group.value());
            properties.add(property);
            if (property instanceof SerializableProperty<?>)
                serializableProperties.add((SerializableProperty<?>) property);
        }
        propertyProblems.clear();
        propertyProblems.addAll(Visibility.wire(this));
        for (String problem : propertyProblems)
            Arsenic.getArsenic().getLogger().error("{}: {}", name, problem);
    }

    /** What was wrong with this module's @ShowWhen rules when it was registered (see {@link Visibility}). */
    public final List<String> getPropertyProblems() {
        return propertyProblems;
    }

    public String getHudInfo() {
        return null;
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

    public final ModuleTier getTier() {
        return tier;
    }

    public final ModuleCategory getCategory() {
        return category;
    }

    public final boolean isEnabled() {
        return enabled;
    }

    public final void toggle() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;

            if (enabled) {
                onEnable();

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

            if (!hidden) {
                String s = enabled ? "enabled" : "disabled";
                NotificationManager.show(new Notification(NotificationType.INFO, "Module " + s, getName() + " has been " + s, 1));
            }
        }
    }

    public void setEnabledSilently(boolean enabled) {
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

    public boolean isHidden() {
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

    public final boolean isAddon() {
        return addon;
    }

    final void markAddon() {
        addon = true;
    }

    public final int getKeybind() {
        return keybind;
    }

    public final void setKeybind(int keybind) {
        this.keybind = keybind;
    }

    @Override
    public final Collection<Property<?>> getContents() {
        return properties;
    }

    public final List<? extends Property<?>> getProperties() {
        return properties;
    }

    /** Each value loads on its own: a missing or broken one keeps its current value and the rest still load. */
    @Override
    public void loadFromJson(JsonObject obj) {
        if (obj.has("hud") && obj.get("hud").isJsonObject())
            hudElements.forEach(element -> element.load(obj.getAsJsonObject("hud")));
        try {
            if (obj.has("bind"))
                keybind = obj.get("bind").getAsInt();
            if (obj.has("hidden"))
                hidden = obj.get("hidden").getAsBoolean();
        } catch (RuntimeException e) {
            Arsenic.getArsenic().getLogger().info("Bad bind/hidden value in {}'s config", getName());
        }

        for (SerializableProperty<?> property : serializableProperties) {
            if (!obj.has(property.getJsonKey()) || !obj.get(property.getJsonKey()).isJsonObject())
                continue;
            try {
                property.loadFromJson(obj.getAsJsonObject(property.getJsonKey()));
            } catch (RuntimeException e) {
                Arsenic.getArsenic().getLogger().info("Could not load {} > {} from the config: {}", getName(), property.getJsonKey(), e);
            }
        }

        // after the settings, so onEnable sees the loaded values
        try {
            if (obj.has("enabled"))
                setEnabledFromConfig(obj.get("enabled").getAsBoolean());
        } catch (RuntimeException e) {
            Arsenic.getArsenic().getLogger().info("Could not apply {}'s enabled state: {}", getName(), e);
        }
        postApplyConfig();
    }

    /** In a world the module is switched like a normal toggle (onEnable/onDisable run) but without a notification. */
    private void setEnabledFromConfig(boolean enabled) {
        if (mc.thePlayer == null || mc.theWorld == null) {
            setEnabledSilently(enabled);
            return;
        }
        boolean wasHidden = hidden;
        hidden = true;
        try {
            setEnabled(enabled);
        } finally {
            hidden = wasHidden;
        }
    }

    protected void postApplyConfig() {
    }

    @Override
    public JsonObject saveInfoToJson(JsonObject obj) {

        obj.addProperty("bind", keybind);
        obj.addProperty("enabled", enabled);
        obj.addProperty("hidden", hidden);

        if (!hudElements.isEmpty()) {
            JsonObject hud = new JsonObject();
            hudElements.forEach(element -> element.save(hud));
            obj.add("hud", hud);
        }

        serializableProperties.forEach(property -> property.addToJson(obj));
        return obj;
    }

    @Override
    public final String getJsonKey() {
        return name;
    }
}
