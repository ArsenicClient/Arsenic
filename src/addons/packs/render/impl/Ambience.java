import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;

/**
 * Sets a client-side time of day and, optionally, clear weather. The server sends its own time once a second, so the
 * value is set again every tick: a short flicker can show when the server's time arrives. Weather that the server sends
 * with a change packet can also be overwritten the same way.
 */
@ModuleInfo(name = "Ambience", description = "Sets a client-side time of day and clear weather", category = ModuleCategory.RENDER)
public class Ambience extends Module {

    public final DoubleProperty time = new DoubleProperty("Time (ticks)", new DoubleValue(0, 24000, 6000, 100));
    public final BooleanProperty clearWeather = new BooleanProperty("Clear Weather", true);

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        mc.theWorld.setWorldTime((long) time.getValue().getInput());
        if (clearWeather.getValue()) {
            mc.theWorld.setRainStrength(0f);
            mc.theWorld.setThunderStrength(0f);
        }
    };
}
