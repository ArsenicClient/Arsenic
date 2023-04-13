package arsenic.module.impl.world;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import net.minecraft.util.MathHelper;


@ModuleInfo(name = "scaffoldTest", category = ModuleCategory.WORLD)
public class ScaffoldTest extends Module {

    @EventLink
    public final Listener<EventSilentRotation> eventSilentRotationListener = event -> {
        mc.thePlayer.setSprinting(false);
        event.setPitch(83);
        event.setYaw(MathHelper.wrapAngleTo180_float(event.getYaw() + 180f));
    };
}
