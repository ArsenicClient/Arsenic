import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventLiving;
import arsenic.event.impl.EventMovementInput;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import net.minecraft.network.play.client.C03PacketPlayer;

/**
 * Maintainer-requested exception to the no-freeze rule. For up to 45 ticks while you are airborne it zeroes your motion,
 * drops your movement input, and cancels the position packets, so you hang in the air. It ends after 45 ticks or on
 * landing, and then the motion you had is put back. It is off by default, never runs with a GUI open, and will be
 * flagged by movement checks: expect a flag or a lagback on most servers.
 */
@ModuleInfo(name = "Stasis", description = "Hangs in the air for up to 45 ticks. Flag-prone; opt-in", category = ModuleCategory.MOVEMENT)
public class Stasis extends Module {

    private static final int STASIS_TICKS = 45;

    private int ticks;
    private double savedX, savedY, savedZ;

    @Override
    protected void onEnable() {
        ticks = 0;
        if (mc.thePlayer != null) {
            savedX = mc.thePlayer.motionX;
            savedY = mc.thePlayer.motionY;
            savedZ = mc.thePlayer.motionZ;
        }
    }

    @Override
    protected void onDisable() {
        if (mc.thePlayer != null) {
            mc.thePlayer.motionX = savedX;
            mc.thePlayer.motionY = savedY;
            mc.thePlayer.motionZ = savedZ;
        }
        ticks = 0;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.currentScreen != null || mc.thePlayer.onGround || ++ticks >= STASIS_TICKS) {
            setEnabled(false);
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventLiving> onLiving = event -> {
        mc.thePlayer.motionX = 0.0;
        mc.thePlayer.motionY = 0.0;
        mc.thePlayer.motionZ = 0.0;
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventMovementInput> onInput = event -> {
        event.setSpeed(0f);
        event.setStrafe(0f);
        event.setJump(false);
    };

    @EventLink
    public final Listener<EventPacket.OutGoing> onOut = event -> {
        if (event.getPacket() instanceof C03PacketPlayer && !(event.getPacket() instanceof C03PacketPlayer.C05PacketPlayerLook)) {
            event.cancel();
        }
    };
}
