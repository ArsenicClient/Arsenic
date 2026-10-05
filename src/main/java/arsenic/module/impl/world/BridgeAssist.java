package arsenic.module.impl.world;

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
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.ScaffoldUtil;
import arsenic.utils.scaffoldcore.LaneNudge;
import arsenic.utils.scaffoldcore.ScaffoldCore;
import arsenic.utils.scaffoldcore.SneakPresses;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.item.BlockItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import arsenic.utils.render.QuadBatch;
import arsenic.utils.io.Keys;
import com.mojang.blaze3d.platform.InputConstants;

@ModuleInfo(name = "BridgeAssist", category = ModuleCategory.MOVEMENT)
public class BridgeAssist extends Module {

    private static final double BRIDGE_PITCH = 65.0;

    public final DoubleProperty safety = new DoubleProperty("Safety", new DoubleValue(0, 3, 1, 0.1));
    public final BooleanProperty smartMode = new BooleanProperty("Smart Mode", false);

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
    }

    private float[] nudge;
    private boolean bridging;
    private boolean placedSinceTick;

    private float arrowForward, arrowStrafe;
    private final MSTimer arrow = MSTimer.expired();
    private static final long ARROW_MS = 450;

    public void onPlace() {
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK || mc.level == null)
            return;
        BlockPos cell = hit.getBlockPos().relative(hit.getDirection());
        if (!mc.level.getBlockState(cell).canBeReplaced())
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
        bridging = false;
        nudge = null;
        boolean placed = placedSinceTick;
        placedSinceTick = false;

        Scaffold scaffold = Arsenic.getArsenic().getModuleManager().getModuleByClass(Scaffold.class);
        if (scaffold != null && scaffold.isEnabled()) {
            presses.reset();
            lane.reset();
            return;
        }

        if (mc.gui.screen() != null || !mc.player.onGround()) {
            presses.airborne();
            setSneak(false);
            return;
        }

        boolean looking = mc.options.keyDown.isDown()
                && mc.player.getXRot() >= BRIDGE_PITCH;
        if (!looking) {
            presses.reset();
            lane.reset();
            setSneak(false);
            return;
        }
        bridging = PlayerUtils.isPlayerHoldingBlocks();

        float forward = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
        float strafe = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
        nudge = lane.compute(forward, strafe, mc.player.getYRot(), mc.player.getX(), mc.player.getZ(), true, LANE_DEAD);
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
        boolean diagonal = SneakPresses.diagonal(forward, strafe, mc.player.getYRot());
        setSneak(presses.next(ticks -> ScaffoldCore.willFall(in, ScaffoldUtil.WORLD, ticks, FALL_MARGIN),
                true, safety.getValue().getInput(), diagonal, placed));
        if (!smartMode.getValue())
            return;
        float yaw = lane.steerYaw(forward, strafe, mc.player.getYRot(), mc.player.getYRot(), STEER_GAIN, STEER_MAX, 0, STEER_LIMIT);
        if (Float.isNaN(yaw))
            return;
        event.setSpeed(180);
        event.setYaw(yaw);
    };

    @RequiresPlayer
    @EventLink(Priorities.HIGH)
    public final Listener<EventMovementInput> movementInput = event -> {
        if (nudge == null || (event.getSpeed() == 0 && event.getStrafe() == 0))
            return;
        float scale = Math.max(Math.abs(event.getSpeed()), Math.abs(event.getStrafe()));
        event.setSpeed(nudge[0] * scale);
        event.setStrafe(nudge[1] * scale);
    };

    @EventLink
    public final Listener<EventRender2D> renderArrows = event -> {
        long left = ARROW_MS - arrow.getTime();
        if (left <= 0 || mc.player == null || !isEnabled())
            return;
        float alpha = Math.min(1f, left / (float) ARROW_MS);
        float cx = event.getWidth() / 2f, cy = event.getHeight() / 2f + 34f;
        int color = ((int) (alpha * 255) << 24) | 0xFFFFFF;
        QuadBatch batch = new QuadBatch();
        if (arrowStrafe > 0) drawArrow(batch, cx - 18, cy, -1, 0, color);
        if (arrowStrafe < 0) drawArrow(batch, cx + 18, cy, 1, 0, color);
        if (arrowForward > 0) drawArrow(batch, cx, cy - 14, 0, -1, color);
        if (arrowForward < 0) drawArrow(batch, cx, cy + 14, 0, 1, color);
        batch.submit();
    };

    private static void drawArrow(QuadBatch batch, float cx, float cy, float dx, float dy, int color) {
        float px = -dy, py = dx;
        float len = 9f, half = 7f, shaft = 2.5f;
        batch.triangle(cx + dx * len, cy + dy * len,
                cx + dx * 1f + px * half, cy + dy * 1f + py * half,
                cx + dx * 1f - px * half, cy + dy * 1f - py * half, color);
        batch.quad(cx + px * shaft, cy + py * shaft,
                cx - px * shaft, cy - py * shaft,
                cx - dx * len - px * shaft, cy - dy * len - py * shaft,
                cx - dx * len + px * shaft, cy - dy * len + py * shaft, color);
    }

    private void setSneak(boolean wanted) {
        mc.options.keyShift.setDown(wanted || Keys.isPhysicallyDown(mc.options.keyShift));
    }

    @Override
    public void onDisable() {
        presses.reset();
        bridging = false;
        nudge = null;
        arrow.setTime(0);
        lane.reset();
        setSneak(false);
    }
}
