package arsenic.module.impl.ghost;

import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMovementInput;
import arsenic.event.impl.EventPacket;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.TargetManager;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;

@ModuleInfo(name = "JumpReset", category = ModuleCategory.COMBAT)
public class JumpReset extends Module {
    /** Chance to reset on a hit. 1 is every time, which is a pattern. */
    public final DoubleProperty chance = new DoubleProperty("Chance", new DoubleValue(0.0, 1, 1, 0.01));


    public boolean shouldJump;

    @EventLink
    public final Listener<EventPacket.Incoming.Pre> eventPacketListener = event -> {
        if (event.getPacket() instanceof ClientboundSetEntityMotionPacket) {
            if (((ClientboundSetEntityMotionPacket) event.getPacket()).id() == mc.player.getId()) {
                if(Math.random() <= chance.getValue().getInput()) {
                    shouldJump = true;
                }
            }
        }
    };

    @EventLink
    public final Listener<EventMovementInput> eventMotionListener = event -> {
        if (shouldJump && PlayerUtils.getClosestPlayerWithin(4.0) != null) {
            event.setJump(true);
            shouldJump = false;
        }
    };
}
