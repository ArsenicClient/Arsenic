package arsenic.module.impl.world;

import arsenic.module.ModuleTier;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.timer.MSTimer;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.bus.Priorities;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventMovementInput;
import arsenic.event.impl.EventRender2D;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.Group;
import arsenic.module.property.PropertyGroup;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.ScaffoldUtil;
import arsenic.utils.scaffoldcore.LaneNudge;
import arsenic.utils.scaffoldcore.ScaffoldCore;
import arsenic.utils.scaffoldcore.SneakPresses;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

@ModuleInfo(name = "BridgeAssist", category = ModuleCategory.MOVEMENT, tier = ModuleTier.LEGIT)
public class BridgeAssist extends Module {

    private static final double BRIDGE_PITCH = 65.0;

    public final DoubleProperty safety = new DoubleProperty("Safety", new DoubleValue(0, 3, 1, 0.1));
    public final BooleanProperty smartMode = new BooleanProperty("Smart Mode", false);
    /** Rewrites WASD to keep you on the bridge's lane (and shows the arrows). */
    public final BooleanProperty correctKeys = new BooleanProperty("Auto Correct Keys", true);
    /** Only bridges while you hold your sneak key; then it decides when you actually sneak. */
    @PropertyGroup(Group.ADVANCED)
    public final BooleanProperty onlyOnShift = new BooleanProperty("Only On Shift", false);

    private static final double FALL_MARGIN = 0.03;
    private static final double LANE_DEAD = 0.12;
    private static final int PLACE_DELAY = 1;
    private static final double STEER_GAIN = 15, STEER_MAX = 5, STEER_LIMIT = 25;

    private final SneakPresses presses = new SneakPresses();
    private final LaneNudge lane = new LaneNudge();

    {
        lane.diagonals = true;
        lane.window = 15;
        lane.snap = true;
        // Safety is the lead in ticks, so no minimum lead is forced above 1.0 (the shared default is 3)
        presses.needLead = 0;
    }

    private float[] nudge;
    private boolean bridging;
    private boolean placedSinceTick;
    private boolean holdingSneak;
    /** Only On Shift: the physical sneak key is down while bridging, so the sneak key's state is ours to set. */
    private boolean overriding;
    /** Smart Mode turned the body this tick. */
    private boolean steering;

    private float arrowForward, arrowStrafe;
    private final MSTimer arrow = MSTimer.expired();
    private static final long ARROW_MS = 450;

    public void onPlace() {
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || mc.theWorld == null)
            return;
        BlockPos cell = hit.getBlockPos().offset(hit.sideHit);
        if (!mc.theWorld.getBlockState(cell).getBlock().getMaterial().isReplaceable())
            placedSinceTick = true;
    }

    public boolean isBridging() {
        return bridging;
    }

    public int getPlaceDelay() {
        return PLACE_DELAY;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> tickEvent = event -> {
        boolean wasSteering = steering;
        steering = false;
        tick(event);
        // after a turn the rotation manager would keep re-mapping WASD to the turned body while it catches up,
        // which is felt in the air right after leaving the block; snap it back to the camera at once instead
        if (wasSteering && !steering && !mc.thePlayer.onGround) {
            event.setSpeed(180);
            event.setYaw(mc.thePlayer.rotationYaw);
        }
    };

    private void tick(EventSilentRotation event) {
        bridging = false;
        nudge = null;
        boolean placed = placedSinceTick;
        placedSinceTick = false;

        Scaffold scaffold = Arsenic.getArsenic().getModuleManager().getModuleByClass(Scaffold.class);
        if (scaffold != null && scaffold.isEnabled()) {
            presses.reset();
            lane.reset();
            endOverride();
            return;
        }

        if (onlyOnShift.getValue() && !sneakKeyHeld()) {
            presses.reset();
            lane.reset();
            endOverride();
            setSneak(false);
            return;
        }

        if (mc.currentScreen != null || !mc.thePlayer.onGround) {
            presses.airborne();
            endOverride();
            setSneak(false);
            return;
        }

        boolean looking = mc.gameSettings.keyBindBack.isKeyDown()
                && mc.thePlayer.rotationPitch >= BRIDGE_PITCH;
        if (!looking) {
            presses.reset();
            lane.reset();
            endOverride();
            setSneak(false);
            return;
        }
        bridging = PlayerUtils.isPlayerHoldingBlocks();
        overriding = onlyOnShift.getValue();

        float forward = (mc.gameSettings.keyBindForward.isKeyDown() ? 1 : 0) - (mc.gameSettings.keyBindBack.isKeyDown() ? 1 : 0);
        float strafe = (mc.gameSettings.keyBindLeft.isKeyDown() ? 1 : 0) - (mc.gameSettings.keyBindRight.isKeyDown() ? 1 : 0);
        nudge = correctKeys.getValue()
                ? lane.compute(forward, strafe, mc.thePlayer.rotationYaw, mc.thePlayer.posX, mc.thePlayer.posZ, true, LANE_DEAD)
                : null;
        if (nudge != null) {
            arrowForward = Math.signum(nudge[0] - forward);
            arrowStrafe = Math.signum(nudge[1] - strafe);
            arrow.reset();
        }

        ScaffoldCore.Input in = ScaffoldUtil.coreInput(true);
        if (in.moveScale != 1f) {
            in.moveForward /= in.moveScale;
            in.moveStrafe /= in.moveScale;
        }
        boolean diagonal = SneakPresses.diagonal(forward, strafe, mc.thePlayer.rotationYaw);
        // Safety is how many ticks ahead to shift, fractions included (0.5 checks half a tick of motion)
        presses.hardLead = safety.getValue().getInput();
        setSneak(presses.next(ticks -> ScaffoldCore.willFall(in, ScaffoldUtil.WORLD, ticks, FALL_MARGIN),
                true, safety.getValue().getInput(), diagonal, placed));
        if (!smartMode.getValue())
            return;
        float yaw = lane.steerYaw(forward, strafe, mc.thePlayer.rotationYaw, mc.thePlayer.rotationYaw, STEER_GAIN, STEER_MAX, 0, STEER_LIMIT);
        if (Float.isNaN(yaw))
            return;
        event.setSpeed(180);
        event.setYaw(yaw);
        steering = true;
    }

    @RequiresPlayer
    @EventLink(Priorities.HIGH)
    public final Listener<EventMovementInput> movementInput = event -> {
        if (nudge == null || (event.getSpeed() == 0 && event.getStrafe() == 0))
            return;
        // the nudge is worked out before this tick's movement: never apply it to a jump or once off the ground
        if (!mc.thePlayer.onGround || event.isJumping())
            return;
        float scale = Math.max(Math.abs(event.getSpeed()), Math.abs(event.getStrafe()));
        event.setSpeed(nudge[0] * scale);
        event.setStrafe(nudge[1] * scale);
    };

    @EventLink
    public final Listener<EventRender2D> renderArrows = event -> {
        long left = ARROW_MS - arrow.getTime();
        if (left <= 0 || mc.thePlayer == null || !isEnabled())
            return;
        float alpha = Math.min(1f, left / (float) ARROW_MS);
        ScaledResolution sr = event.getSr() != null ? event.getSr() : new ScaledResolution(mc);
        float cx = sr.getScaledWidth() / 2f, cy = sr.getScaledHeight() / 2f + 34f;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1f, 1f, 1f, alpha);
        if (arrowStrafe > 0) drawArrow(cx - 18, cy, -1, 0);
        if (arrowStrafe < 0) drawArrow(cx + 18, cy, 1, 0);
        if (arrowForward > 0) drawArrow(cx, cy - 14, 0, -1);
        if (arrowForward < 0) drawArrow(cx, cy + 14, 0, 1);
        GL11.glPopAttrib();
    };

    private static void drawArrow(float cx, float cy, float dx, float dy) {
        float px = -dy, py = dx;
        float len = 9f, half = 7f, shaft = 2.5f;
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(cx + dx * len, cy + dy * len);
        GL11.glVertex2f(cx + dx * 1f + px * half, cy + dy * 1f + py * half);
        GL11.glVertex2f(cx + dx * 1f - px * half, cy + dy * 1f - py * half);
        GL11.glEnd();
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(cx + px * shaft, cy + py * shaft);
        GL11.glVertex2f(cx - px * shaft, cy - py * shaft);
        GL11.glVertex2f(cx - dx * len - px * shaft, cy - dy * len - py * shaft);
        GL11.glVertex2f(cx - dx * len + px * shaft, cy - dy * len + py * shaft);
        GL11.glEnd();
    }

    private void setSneak(boolean wanted) {
        int key = mc.gameSettings.keyBindSneak.getKeyCode();
        if (overriding) {
            // Only On Shift: the held key turns bridging on, so it must not also keep us sneaking
            holdingSneak = wanted;
            KeyBinding.setKeyBindState(key, wanted);
            return;
        }
        // only let go of a press we made, so other modules (BedDefender) can hold sneak while we idle
        if (!wanted && !holdingSneak)
            return;
        holdingSneak = wanted;
        // with a screen open the physical shift key must not turn into sneaking (shift-clicking in inventories)
        KeyBinding.setKeyBindState(key, wanted || (mc.currentScreen == null && sneakKeyHeld()));
    }

    /** Hands the sneak key back to the player after Only On Shift held it at our value. */
    private void endOverride() {
        if (!overriding)
            return;
        overriding = false;
        holdingSneak = false;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), mc.currentScreen == null && sneakKeyHeld());
    }

    /** Whether the player is physically holding the sneak key (keyboard or mouse button). */
    private boolean sneakKeyHeld() {
        int key = mc.gameSettings.keyBindSneak.getKeyCode();
        if (key == 0)
            return false;
        return key < 0 ? Mouse.isButtonDown(key + 100) : Keyboard.isKeyDown(key);
    }

    @Override
    public void onDisable() {
        presses.reset();
        bridging = false;
        nudge = null;
        steering = false;
        arrow.setTime(0);
        lane.reset();
        endOverride();
        setSneak(false);
    }
}
