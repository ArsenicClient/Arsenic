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
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
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

    /** Furthest the void search will turn away from your aim. Smaller flicks are less obvious. Void direction only. */
    @PropertyInfo(reliesOn = "Direction", value = "Void")
    public final DoubleProperty maxVoidAngle = new DoubleProperty("Max angle", new DoubleValue(15, 180, 180, 15));

    private long sinceLastFlick = 0;
    private long lastFlickTime = 0;

    /** Vanilla's base knockback, applied away from the attacker's position - the flick can't steer it. */
    private static final double BASE_KNOCKBACK = 0.4;
    /** Extra knockback per level (sprint counts as one), applied along the attacker's yaw - the part a flick steers. */
    private static final double EXTRA_KNOCKBACK = 0.5;
    /** Upward kick the extra knockback adds on top of the clamped base one. */
    private static final double EXTRA_KNOCKBACK_Y = 0.1;
    /** Air acceleration a sprinting player gets from movement input - what they fight the knockback with. */
    private static final double AIR_STRAFE = 0.026;
    /** How many ticks to simulate the target's flight before giving up on an angle. */
    private static final int MAX_SIM_TICKS = 40;
    /** How far the target has to drop with no ground found before we check the column below for void. */
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
        return mc.player != null && attackTick == mc.player.tickCount;
    }

    /**
     * Whether the flick owns this tick's rotation - armed and waiting to apply, or applied this
     * tick. Other rotation modules must leave the rotation alone while this is true. Checking the
     * applied tick as well as the state keeps the answer right whichever order the listeners run
     * in, since applying the flick moves the state on to RESTORING.
     */
    public boolean ownsRotation() {
        return isEnabled() && (state == FlickState.FLICKING_AWAY
                || (mc.player != null && flickAppliedTick == mc.player.tickCount));
    }

    /**
     * Arms the flick for an incoming attack. Returns whether it actually armed: for every
     * direction but {@link FlickDirection#Void} this always succeeds, but Void only arms when a
     * push angle that empties into the void was actually found - callers must let the attack go
     * through normally when this returns false instead of forcing a flick that goes nowhere.
     */
    public boolean armFlick(Entity target) {
        return armFlick(target, mc.player.getYRot());
    }

    /**
     * {@link #armFlick(Entity)} measured from {@code baseYaw} instead of the camera - for callers
     * like KillAura whose aim lives in a silent rotation the camera never shows.
     */
    public boolean armFlick(Entity target, float baseYaw) {
        originalYaw = baseYaw;

        if (direction.getValue() == FlickDirection.Void) {
            if (!(target instanceof LivingEntity))
                return false;
            LivingEntity living = (LivingEntity) target;
            // Still in hurt frames: the server ignores the hit's knockback entirely, so there's
            // nothing to steer. Let it go through normally and save the flick for the next one.
            if (living.hurtTime > 0)
                return false;
            Float voidYaw = findVoidYaw(living);
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
                flickAppliedTick = mc.player.tickCount;
                event.setYaw(flickYaw);
                event.setSpeed(360f);
                event.setMovementFix(SilentRotationManager.MovementFix.STRICT);
                state = FlickState.RESTORING;
                break;

            case RESTORING:
                if (pendingTarget != null) {
                    attackTick = mc.player.tickCount;
                    mc.player.swingItem();
                    mc.gameMode.attackEntity(mc.player, pendingTarget);
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
        // Point along where they actually go - base push plus the steered part - not just the yaw.
        double[] push = knockbackVelocity(target, yaw, knockbackLevel());
        double len = Math.sqrt(push[0] * push[0] + push[2] * push[2]);
        if (len < 1.0E-4)
            return;
        voidArrows.add(new VoidArrow(target.getX(), target.getY(), target.getZ(),
                push[0] / len, push[2] / len, System.currentTimeMillis()));
    }

    /** Knockback level the server will use for our next hit: the Knockback enchant, plus one for sprinting. */
    private int knockbackLevel() {
        int level = EnchantmentHelper.getKnockbackModifier(mc.player);
        if (mc.player.isSprinting())
            level++;
        return level;
    }

    /**
     * Searches outward from the current yaw, up to {@link #maxVoidAngle}, for an angle whose
     * knockback would drop the target into the void. Returns null when nothing does - or when
     * there's no steerable knockback at all (not sprinting, no Knockback enchant), since then
     * the push goes straight away from us whatever way we face and a flick changes nothing.
     */
    private Float findVoidYaw(LivingEntity target) {
        int level = knockbackLevel();
        if (level <= 0)
            return null;
        int maxDelta = (int) maxVoidAngle.getValue().getInput();
        for (int delta = 0; delta <= maxDelta; delta += ANGLE_STEP) {
            if (wouldKnockIntoVoid(target, originalYaw + delta, level))
                return originalYaw + delta;
            if (delta != 0 && delta != 180 && wouldKnockIntoVoid(target, originalYaw - delta, level))
                return originalYaw - delta;
        }
        return null;
    }

    /**
     * The velocity the server hands the target for a hit thrown at {@code yaw}, per vanilla 1.8:
     * {@code LivingEntity#knockBack} pushes 0.4 away from the attacker's <i>position</i>
     * (clamping Y to 0.4), then {@code attackTargetEntityWithCurrentItem} adds 0.5 per knockback
     * level along the attacker's <i>yaw</i> - only that second part is what a flick steers. The
     * target's prior motion is dropped: the server barely tracks player motion, and the velocity
     * packet overwrites whatever the client had.
     */
    private double[] knockbackVelocity(Entity target, float yaw, int level) {
        double motionX = 0, motionZ = 0;
        double offX = mc.player.getX() - target.getX();
        double offZ = mc.player.getZ() - target.getZ();
        double offLen = Math.sqrt(offX * offX + offZ * offZ);
        if (offLen > 1.0E-4) {
            motionX -= offX / offLen * BASE_KNOCKBACK;
            motionZ -= offZ / offLen * BASE_KNOCKBACK;
        }
        double motionY = BASE_KNOCKBACK;

        if (level > 0) {
            float rad = yaw * (float) Math.PI / 180f;
            motionX += -Mth.sin(rad) * level * EXTRA_KNOCKBACK;
            motionZ += Mth.cos(rad) * level * EXTRA_KNOCKBACK;
            motionY += EXTRA_KNOCKBACK_Y;
        }
        return new double[]{motionX, motionY, motionZ};
    }

    /**
     * Simulates the target's flight after a hit thrown at {@code yaw}: gravity, air drag, and the
     * target holding straight back against the push every tick at sprint strength - the worst
     * case, so a flick is never wasted on someone who just air-strafes back onto the edge. It
     * fails the moment their body touches anything; once they've dropped {@link #VOID_DROP} it
     * checks the whole column below down to the bottom of the world, so a lower island or floor
     * doesn't count as void.
     */
    private boolean wouldKnockIntoVoid(LivingEntity target, float yaw, int level) {
        double[] push = knockbackVelocity(target, yaw, level);
        double motionX = push[0], motionY = push[1], motionZ = push[2];

        double[] input = {0, 0};
        double pushLen = Math.sqrt(motionX * motionX + motionZ * motionZ);
        if (pushLen > 1.0E-4) {
            input[0] = -motionX / pushLen * AIR_STRAFE;
            input[1] = -motionZ / pushLen * AIR_STRAFE;
        }

        double posX = target.getX(), posY = target.getY(), posZ = target.getZ();
        double startY = posY;

        for (int tick = 0; tick < MAX_SIM_TICKS; tick++) {
            motionX += input[0];
            motionZ += input[1];

            double nextX = posX + motionX;
            double nextY = posY + motionY;
            double nextZ = posZ + motionZ;

            if (collides(target, nextX, posY, nextY, nextZ, target.height))
                return false;

            posX = nextX;
            posY = nextY;
            posZ = nextZ;

            motionY -= 0.08;
            motionY *= 0.98;
            motionX *= 0.91;
            motionZ *= 0.91;

            if (posY < 0)
                return true;
            if (startY - posY > VOID_DROP)
                return !collides(target, posX, 0, posY, posZ, 0);
        }
        return false;
    }

    /**
     * Whether a player-sized body at {@code (x, z)} touches anything while sweeping from
     * {@code fromY} to {@code toY}. Covers the full body height, so walls stop the sim too -
     * someone shoved into a wall slides down it rather than sailing past.
     */
    private boolean collides(LivingEntity target, double x, double fromY, double toY, double z, double height) {
        double minY = Math.min(fromY, toY) - 0.1;
        double maxY = Math.max(fromY, toY) + height + 0.1;
        AABB probe = new AABB(x - 0.3, minY, z - 0.3, x + 0.3, maxY, z + 0.3);
        return !mc.level.getCollidingBoundingBoxes(target, probe).isEmpty();
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
