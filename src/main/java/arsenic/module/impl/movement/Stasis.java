package arsenic.module.impl.movement;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventLiving;
import arsenic.event.impl.EventMove;
import arsenic.event.impl.EventMovementInput;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventUpdate;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import net.minecraft.network.play.client.C03PacketPlayer;

/**
 * Maintainer-requested exception to the no-freeze rule, ported from the Yuri client's StasisModule.
 *
 * Stasis phase: for 45 ticks your motion is held at zero and your movement input is dropped, and position packets are
 * cancelled (look-only packets still go out). Release phase: for one tick the saved motion is put back and packets go
 * through, then the phase starts again. Packets are not cancelled while you are being hurt (hurtTime is not zero), so
 * knockback is not held. It ends as soon as you land, and the saved motion is restored when it is turned off.
 *
 * It is off by default and is flagged by movement checks: expect a flag or a lagback on most servers.
 */
@ModuleInfo(name = "Stasis", description = "Hangs in the air for up to 45 ticks. Flag-prone; opt-in", category = ModuleCategory.MOVEMENT)
public class Stasis extends Module {

    private static final int STASIS_TICKS = 45;
    private static final int RELEASE_TICKS = 1;
    private static final int PHASE_STASIS = 0;
    private static final int PHASE_RELEASE = 1;

    private double savedX, savedY, savedZ;
    private int ticks;
    private int phase;

    @Override
    protected void onEnable() {
        ticks = 0;
        phase = PHASE_STASIS;
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
        phase = PHASE_STASIS;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventUpdate.Pre> onPreUpdate = event -> {
        ticks++;

        if (phase == PHASE_STASIS && ticks >= STASIS_TICKS) {
            phase = PHASE_RELEASE;
            ticks = 0;
            mc.thePlayer.motionX = savedX;
            mc.thePlayer.motionY = savedY;
            mc.thePlayer.motionZ = savedZ;
        } else if (phase == PHASE_RELEASE && ticks >= RELEASE_TICKS) {
            phase = PHASE_STASIS;
            ticks = 0;
            savedX = mc.thePlayer.motionX;
            savedY = mc.thePlayer.motionY;
            savedZ = mc.thePlayer.motionZ;
        }

        if (phase == PHASE_STASIS) {
            mc.thePlayer.motionX = 0.0;
            mc.thePlayer.motionY = 0.0;
            mc.thePlayer.motionZ = 0.0;
        }

        if (mc.thePlayer.onGround) {
            setEnabled(false);
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventLiving> onLiving = event -> {
        if (phase == PHASE_STASIS) {
            mc.thePlayer.motionX = 0.0;
            mc.thePlayer.motionY = 0.0;
            mc.thePlayer.motionZ = 0.0;
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventMovementInput> onInput = event -> {
        if (phase != PHASE_STASIS) return;
        event.setSpeed(0f);
        event.setStrafe(0f);
        event.setJump(false);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventMove> onMove = event -> {
        if (phase != PHASE_STASIS) return;
        event.setForward(0f);
        event.setStrafe(0f);
    };

    @EventLink
    public final Listener<EventPacket.OutGoing> onOut = event -> {
        if (phase == PHASE_RELEASE || mc.thePlayer == null || mc.thePlayer.hurtTime != 0) return;
        if (event.getPacket() instanceof C03PacketPlayer && !(event.getPacket() instanceof C03PacketPlayer.C05PacketPlayerLook)) {
            event.cancel();
        }
    };
}
