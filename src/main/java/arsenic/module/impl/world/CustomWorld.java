package arsenic.module.impl.world;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.network.play.server.S03PacketTimeUpdate;


@ModuleInfo(name = "CustomWorld", category = ModuleCategory.WORLD)
public class CustomWorld extends Module {
    public BooleanProperty changeWeather = new BooleanProperty("Change weather", false);
    public final EnumProperty<wMode> mode = new EnumProperty<>("Mode: ", wMode.CLEAR);
    public BooleanProperty changeTime = new BooleanProperty("Change time", false);
    public final DoubleProperty time = new DoubleProperty("Time", new DoubleValue(0, 24000, 1000, 1));

    @EventLink
    public final Listener<EventPacket.Incoming> onPacket = event -> {

        if (event.getPacket() instanceof S03PacketTimeUpdate && changeTime.getValue()) {
            event.cancel();
        }
    };

    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.thePlayer != null && mc.theWorld != null) {
            if (changeWeather.getValue()) {
                int weather = -1;
                switch (mode.getValue()) {
                    case CLEAR:
                        weather = 0;
                        break;
                    case RAINY:
                        weather = 1;
                        break;
                    case THUNDER:
                        weather = 2;
                        break;
                }
                mc.theWorld.setRainStrength(weather);
            }
            if (changeTime.getValue()) {
                mc.theWorld.setWorldTime((long) time.getValue().getInput());
            }
        }
    };

    public enum wMode {
        CLEAR,
        RAINY,
        THUNDER
    }

}
