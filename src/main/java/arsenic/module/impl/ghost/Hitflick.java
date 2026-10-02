package arsenic.module.impl.ghost;

import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.EnumProperty;
import arsenic.utils.lag.LagManager;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;
import arsenic.module.property.impl.BooleanProperty;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(name = "Hitflick", category = ModuleCategory.COMBAT)
public class Hitflick extends Module {

    public final EnumProperty<FlickDirection> direction = new EnumProperty<>("Direction", FlickDirection.Right);

    /**
     * Hold outgoing packets for the duration of the flick, so the server never sees the rotation -
     * only the position that existed before it. Lands more of the flick, at the cost of a short
     * deliberate desync every time you swing.
     */
    public final BooleanProperty blinkDuringFlick = new BooleanProperty("Blink", false);

    /** Minimum time between flicks, so every hit doesn't flick - a real person doesn't either. */
    public final DoubleProperty cooldown = new DoubleProperty("Cooldown", new DoubleValue(0, 1000, 250, 50));

    /** Flick angle in degrees. Bigger is a stronger flick and a bigger rotation to explain. Custom direction only. */
    @PropertyInfo(reliesOn = "Direction", value = "Custom")
    public final DoubleProperty customAngle = new DoubleProperty("Angle", new DoubleValue(1, 180, 90, 1));

    private long sinceLastFlick = 0;
    private long lastFlickTime = 0;

    /** Base horizontal knockback impulse, approximating a bare-hand hit with no Knockback enchant. */
    private static final float KNOCKBACK_STRENGTH = 0.4f;
    /** How many ticks to simulate the target's flight before giving up on an angle. */
    private static final int MAX_SIM_TICKS = 30;
    /** How far the target has to drop with no ground found underneath before we call it void. */
    private static final double VOID_DROP = 14.0;
    /** Degrees between each candidate angle tried while searching for a void push. */
    private static final int ANGLE_STEP = 3;

    private static final double ARROW_LENGTH = 4.0;
    private static final double ARROW_HEAD_SIZE = 1.0;
    private static final double SHAFT_HALF_WIDTH = 0.18;
    private static final double HEAD_HALF_WIDTH = 0.55;
    private static final double OUTLINE_THICKNESS = 0.07;
    private static final long ARROW_LIFETIME_MS = 4000;

    private final List<VoidArrow> voidArrows = new ArrayList<>();

    public enum FlickState {
        IDLE,
        FLICKING_AWAY,
        RESTORING
    }

    private FlickState state = FlickState.IDLE;
    private float flickYaw;
    private float originalYaw;
    private Entity pendingTarget; // store target to attack on restore
    private boolean pendingVoidHit;
    private int flickAppliedTick = -1;
    private int attackTick = -1;

    /** Whether the flick's own attack went out this tick - callers shouldn't stack a second one. */
    public boolean attackedThisTick() {
        return mc.thePlayer != null && attackTick == mc.thePlayer.ticksExisted;
    }

    /**
     * Whether the flick owns this tick's rotation - armed and waiting to apply, or applied this
     * tick. Other rotation modules must leave the rotation alone while this is true. Checking the
     * applied tick as well as the state keeps the answer right whichever order the listeners run
     * in, since applying the flick moves the state on to RESTORING.
     */
    public boolean ownsRotation() {
        return isEnabled() && (state == FlickState.FLICKING_AWAY
                || (mc.thePlayer != null && flickAppliedTick == mc.thePlayer.ticksExisted));
    }

    /**
     * Arms the flick for an incoming attack. Returns whether it actually armed: for every
     * direction but {@link FlickDirection#Void} this always succeeds, but Void only arms when a
     * push angle that empties into the void was actually found - callers must let the attack go
     * through normally when this returns false instead of forcing a flick that goes nowhere.
     */
    public boolean armFlick(Entity target) {
        return armFlick(target, mc.thePlayer.rotationYaw);
    }

    /**
     * {@link #armFlick(Entity)} measured from {@code baseYaw} instead of the camera - for callers
     * like KillAura whose aim lives in a silent rotation the camera never shows.
     */
    public boolean armFlick(Entity target, float baseYaw) {
        originalYaw = baseYaw;

        if (direction.getValue() == FlickDirection.Void) {
            if (!(target instanceof EntityLivingBase))
                return false;
            Float voidYaw = findVoidYaw((EntityLivingBase) target);
            if (voidYaw == null)
                return false;
            flickYaw = voidYaw;
            pendingVoidHit = true;
        } else {
            flickYaw = originalYaw + getFlickAngle();
            pendingVoidHit = false;
        }

        this.pendingTarget = target;
        state = FlickState.FLICKING_AWAY;
        lastFlickTime = System.currentTimeMillis();
        // Hold from the moment the flick is armed, so the rotation away never reaches the server.
        if (blinkDuringFlick.getValue())
            LagManager.acquire(getClass());
        return true;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onSilentRotation = event -> {
        switch (state) {
            case FLICKING_AWAY:
                flickAppliedTick = mc.thePlayer.ticksExisted;
                event.setYaw(flickYaw);
                event.setSpeed(360f);
                event.setMovementFix(SilentRotationManager.MovementFix.STRICT);
                state = FlickState.RESTORING;
                break;

            case RESTORING:
                if (pendingTarget != null) {
                    attackTick = mc.thePlayer.ticksExisted;
                    mc.thePlayer.swingItem();
                    mc.playerController.attackEntity(mc.thePlayer, pendingTarget);
                    if (pendingVoidHit)
                        spawnVoidArrow(pendingTarget, flickYaw);
                    pendingTarget = null;
                    pendingVoidHit = false;
                }
                state = FlickState.IDLE;
                sinceLastFlick = 0;
                // Flush once the view is back where it started: the server sees the swing, never
                // the round trip out and back.
                if (blinkDuringFlick.getValue())
                    LagManager.release(getClass());
                break;

            case IDLE:
                sinceLastFlick++;
                break;
        }
    };

    /**
     * Draws the "pushed this way" arrow next to anyone we've just knocked towards the void.
     * <p>
     * Drawn as two filled triangle passes rather than lines: a black silhouette slightly larger
     * than the arrow, then a white fill on top of it - the standard "draw it twice" outline trick,
     * since there is no line-width thick enough to read as a solid arrow from a distance.
     */
    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRenderVoidArrows = event -> {
        if (voidArrows.isEmpty())
            return;

        long now = System.currentTimeMillis();
        voidArrows.removeIf(arrow -> now - arrow.spawnTime > ARROW_LIFETIME_MS);
        if (voidArrows.isEmpty())
            return;

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDepthMask(false);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        double vx = mc.getRenderManager().viewerPosX;
        double vy = mc.getRenderManager().viewerPosY;
        double vz = mc.getRenderManager().viewerPosZ;

        for (VoidArrow arrow : voidArrows) {
            float alpha = 1f - (now - arrow.spawnTime) / (float) ARROW_LIFETIME_MS;
            double baseX = arrow.x, baseY = arrow.y + 1.2, baseZ = arrow.z;

            // Black outline, drawn oversized so it peeks out from behind the white fill.
            wr.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
            addArrowTriangles(wr, baseX, baseY, baseZ, arrow.dirX, arrow.dirZ, OUTLINE_THICKNESS,
                    0f, 0f, 0f, alpha, vx, vy, vz);
            tessellator.draw();

            // White fill on top, true size.
            wr.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
            addArrowTriangles(wr, baseX, baseY, baseZ, arrow.dirX, arrow.dirZ, 0,
                    1f, 1f, 1f, alpha, vx, vy, vz);
            tessellator.draw();
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
    };

    /**
     * Emits the six triangles (shaft quad + head) of a flat arrow lying in the XZ plane, pointing
     * from {@code (baseX, baseZ)} along {@code (dirX, dirZ)}. {@code expand} grows every edge
     * outward by that much - 0 for the true-size fill, {@link #OUTLINE_THICKNESS} for the outline
     * pass drawn behind it.
     */
    private static void addArrowTriangles(WorldRenderer wr, double baseX, double baseY, double baseZ,
                                           double dirX, double dirZ, double expand,
                                           float r, float g, float b, float a,
                                           double vx, double vy, double vz) {
        double perpX = -dirZ, perpZ = dirX;

        double tailX = baseX - dirX * expand;
        double tailZ = baseZ - dirZ * expand;
        double tipX = baseX + dirX * (ARROW_LENGTH + expand);
        double tipZ = baseZ + dirZ * (ARROW_LENGTH + expand);
        double shaftEndX = baseX + dirX * (ARROW_LENGTH - ARROW_HEAD_SIZE);
        double shaftEndZ = baseZ + dirZ * (ARROW_LENGTH - ARROW_HEAD_SIZE);

        double shaftHalf = SHAFT_HALF_WIDTH + expand;
        double headHalf = HEAD_HALF_WIDTH + expand;

        double tailLeftX = tailX + perpX * shaftHalf, tailLeftZ = tailZ + perpZ * shaftHalf;
        double tailRightX = tailX - perpX * shaftHalf, tailRightZ = tailZ - perpZ * shaftHalf;
        double shaftLeftX = shaftEndX + perpX * shaftHalf, shaftLeftZ = shaftEndZ + perpZ * shaftHalf;
        double shaftRightX = shaftEndX - perpX * shaftHalf, shaftRightZ = shaftEndZ - perpZ * shaftHalf;
        double headLeftX = shaftEndX + perpX * headHalf, headLeftZ = shaftEndZ + perpZ * headHalf;
        double headRightX = shaftEndX - perpX * headHalf, headRightZ = shaftEndZ - perpZ * headHalf;

        // Shaft (two triangles forming a rectangle).
        vertex(wr, tailLeftX, baseY, tailLeftZ, vx, vy, vz, r, g, b, a);
        vertex(wr, shaftLeftX, baseY, shaftLeftZ, vx, vy, vz, r, g, b, a);
        vertex(wr, shaftRightX, baseY, shaftRightZ, vx, vy, vz, r, g, b, a);

        vertex(wr, tailLeftX, baseY, tailLeftZ, vx, vy, vz, r, g, b, a);
        vertex(wr, shaftRightX, baseY, shaftRightZ, vx, vy, vz, r, g, b, a);
        vertex(wr, tailRightX, baseY, tailRightZ, vx, vy, vz, r, g, b, a);

        // Head (one triangle, wider than the shaft).
        vertex(wr, headLeftX, baseY, headLeftZ, vx, vy, vz, r, g, b, a);
        vertex(wr, tipX, baseY, tipZ, vx, vy, vz, r, g, b, a);
        vertex(wr, headRightX, baseY, headRightZ, vx, vy, vz, r, g, b, a);
    }

    private static void vertex(WorldRenderer wr, double x, double y, double z,
                                double vx, double vy, double vz, float r, float g, float b, float a) {
        wr.pos(x - vx, y - vy, z - vz).color(r, g, b, a).endVertex();
    }

    private void spawnVoidArrow(Entity target, float yaw) {
        float rad = yaw * (float) Math.PI / 180f;
        double dirX = -MathHelper.sin(rad);
        double dirZ = MathHelper.cos(rad);
        voidArrows.add(new VoidArrow(target.posX, target.posY, target.posZ, dirX, dirZ, System.currentTimeMillis()));
    }

    /**
     * Searches outward from the current yaw for an angle whose knockback push would drop the
     * target into the void, within {@link #MAX_SIM_TICKS}. Returns null when nothing in the full
     * sweep lands in the void, so the caller knows not to flick at all.
     */
    private Float findVoidYaw(EntityLivingBase target) {
        for (int delta = 0; delta <= 180; delta += ANGLE_STEP) {
            if (wouldKnockIntoVoid(target, originalYaw + delta))
                return originalYaw + delta;
            if (delta != 0 && wouldKnockIntoVoid(target, originalYaw - delta))
                return originalYaw - delta;
        }
        return null;
    }

    /**
     * Simulates the target's flight after a hit thrown at {@code yaw}, mirroring vanilla's
     * {@code EntityLivingBase#knockBack}: existing motion is halved, then the push is added in the
     * direction the attacker is facing - the exact lever a flick pulls. From there it's just
     * gravity, drag, and a ground probe every tick until it either lands or has fallen further
     * than {@link #VOID_DROP} with nothing underneath it.
     */
    private boolean wouldKnockIntoVoid(EntityLivingBase target, float yaw) {
        float rad = yaw * (float) Math.PI / 180f;
        double dirX = -MathHelper.sin(rad);
        double dirZ = MathHelper.cos(rad);

        double motionX = target.motionX / 2.0 + dirX * KNOCKBACK_STRENGTH;
        double motionY = Math.min(target.motionY / 2.0 + KNOCKBACK_STRENGTH, 0.4);
        double motionZ = target.motionZ / 2.0 + dirZ * KNOCKBACK_STRENGTH;

        double posX = target.posX, posY = target.posY, posZ = target.posZ;
        double startY = posY;

        for (int tick = 0; tick < MAX_SIM_TICKS; tick++) {
            double nextX = posX + motionX;
            double nextY = posY + motionY;
            double nextZ = posZ + motionZ;

            if (landsOnGround(target, nextX, posY, nextY, nextZ))
                return false;

            posX = nextX;
            posY = nextY;
            posZ = nextZ;

            motionY -= 0.08;
            motionY *= 0.98;
            motionX *= 0.91;
            motionZ *= 0.91;

            if (startY - posY > VOID_DROP)
                return true;
        }
        return false;
    }

    private boolean landsOnGround(EntityLivingBase target, double x, double fromY, double toY, double z) {
        double minY = Math.min(fromY, toY) - 0.1;
        double maxY = Math.max(fromY, toY) + 0.1;
        AxisAlignedBB probe = new AxisAlignedBB(x - 0.3, minY, z - 0.3, x + 0.3, maxY, z + 0.3);
        return !mc.theWorld.getCollidingBoundingBoxes(target, probe).isEmpty();
    }

    private float getFlickAngle() {
        switch (direction.getValue()) {
            case Left:   return -90f;
            case Right:  return  90f;
            case Back:   return 180f;
            case Custom: return (float) customAngle.getValue().getInput();
            default:     return  90f;
        }
    }

    public boolean shouldFlick() {
        return state == FlickState.IDLE && sinceLastFlick >= 1
                && System.currentTimeMillis() - lastFlickTime >= cooldown.getValue().getInput();
    }

    @Override
    protected void onDisable() {
        // Never leave the buffer held if the module is toggled off mid-flick.
        LagManager.release(getClass());
        state = FlickState.IDLE;
        flickYaw = 0;
        originalYaw = 0;
        pendingTarget = null;
        pendingVoidHit = false;
        flickAppliedTick = -1;
        attackTick = -1;
        voidArrows.clear();
    }

    public enum FlickDirection {
        Left, Right, Back, Custom, Void;
    }

    private static final class VoidArrow {
        final double x, y, z;
        final double dirX, dirZ;
        final long spawnTime;

        VoidArrow(double x, double y, double z, double dirX, double dirZ, long spawnTime) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dirX = dirX;
            this.dirZ = dirZ;
            this.spawnTime = spawnTime;
        }
    }
}
