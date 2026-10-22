import arsenic.module.ModuleTier;
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
 * Sets a client-side time of day and, optionally, clear weather. The time is the dimension's day clock, stopped at the
 * chosen tick; the server sends its own time once a second, so it is set again every tick. Weather that the server
 * sends with a change packet is overwritten the same way.
 */
@ModuleInfo(name = "Ambience", description = "Sets a client-side time of day and clear weather", category = ModuleCategory.RENDER, tier = ModuleTier.QOL)
public class Ambience extends Module {

    public final DoubleProperty time = new DoubleProperty("Time (ticks)", new DoubleValue(0, 24000, 6000, 100));
    public final BooleanProperty clearWeather = new BooleanProperty("Clear Weather", true);

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        mc.level.dimensionType().defaultClock().ifPresent(clock -> mc.level.clockManager().handleUpdates(
                mc.level.getGameTime(), java.util.Map.of(clock,
                        new net.minecraft.world.clock.ClockNetworkState((long) time.getValue().getInput(), 0f, 0f))));
        if (clearWeather.getValue()) {
            mc.level.setRainLevel(0f);
            mc.level.setThunderLevel(0f);
        }
    };
}
