package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPlayerJoinWorld;
import arsenic.event.impl.EventSilentRotation;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.world.Scaffold;
import arsenic.module.impl.world.TellyScaffold;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.SliderScale;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.BadPacketsManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

/**
 * Auto Projectile (from Yuri): while KillAura fights someone just out of reach, throws a snowball or egg (or casts
 * a rod when close enough) at where they will be. The aim is a ballistic solve with the throwable's gravity and drag
 * and the target's current velocity. The throw waits until the server has our aim (the use packet goes out before
 * this tick's movement packet), then the slot is held a few ticks and switched back. KillAura neither attacks nor
 * blocks while a throw is under way.
 */
@ModuleInfo(name = "AutoProjectile", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class AutoProjectile extends Module {

    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(4, 10, 6, 0.5));
    public final DoubleProperty rotationSpeed = new DoubleProperty("Rotation Speed", new DoubleValue(10, 180, 60, 1), SliderScale.LOG);
    public final BooleanProperty rods = new BooleanProperty("Rods", true);
    public final BooleanProperty lineOfSight = new BooleanProperty("Line Of Sight", true);

    private static final double MIN_RANGE = 3.0;
    private static final double ROD_MAX_RANGE = 6.0;
    private static final int MAX_AIM_TICKS = 10;
    private static final int MAX_FLIGHT_TICKS = 60;
    private static final double LAUNCH_SPEED = 1.5;
    private static final double THROWN_GRAVITY = 0.03, THROWN_DRAG = 0.99;
    private static final double HOOK_GRAVITY = 0.03, HOOK_DRAG = 0.92;
    private static final float AIM_THRESHOLD = 1.5f;

    private enum State { IDLE, AIMING, THROWING }

    private State state = State.IDLE;
    private EntityLivingBase target;
    private float[] rots;
    private int slot = -1;
    private int oldSlot = -1;
    private int aimTicks;
    private int swapBackTicks;
    private int cooldown;
    private long lastThrow;

    /** True while a throw is being aimed or held, and for the tick the slot switches back. */
    public boolean isBusy() {
        return isEnabled() && (state != State.IDLE || cooldown > 0);
    }

    public boolean ownsRotation() {
        return isEnabled() && state != State.IDLE && rots != null;
    }

    @Override
    protected void onDisable() {
        reset();
    }

    @EventLink
    public final Listener<EventPlayerJoinWorld> onJoin = event -> {
        if (event.getEntity() != mc.thePlayer)
            return;
        state = State.IDLE;
        target = null;
        rots = null;
        cooldown = 0;
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (cooldown > 0)
            cooldown--;
        if (moduleEnabled(Scaffold.class) || moduleEnabled(TellyScaffold.class)) {
            reset();
            return;
        }
        switch (state) {
            case IDLE:
                tryBegin();
                break;
            case AIMING:
                if (target == null || target.isDead || mc.thePlayer.inventory.getStackInSlot(slot) == null
                        || ++aimTicks > MAX_AIM_TICKS) {
                    reset();
                    return;
                }
                float[] solved = solveRotations(target, mc.thePlayer.inventory.getStackInSlot(slot).getItem());
                if (solved != null)
                    rots = solved;
                break;
            case THROWING:
                if (--swapBackTicks <= 0) {
                    finish();
                    return;
                }
                break;
        }
        if (state != State.IDLE && rots != null) {
            event.setYaw(rots[0]);
            event.setPitch(rots[1]);
            event.setSpeed((float) rotationSpeed.getValue().getInput());
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onRotationPost = event -> {
        if (state != State.AIMING || rots == null)
            return;
        // the use packet is handled with the rotation of the last movement packet, so that one has to be on aim
        if (Math.abs(MathHelper.wrapAngleTo180_float(event.getPrevYaw() - rots[0])) > AIM_THRESHOLD
                || Math.abs(event.getPrevPitch() - rots[1]) > AIM_THRESHOLD)
            return;
        if (mc.thePlayer.isBlocking())
            return;
        if (BadPacketsManager.bad(false, true, false, true, true))
            return;
        throwNow();
    };

    private void tryBegin() {
        KillAura killAura = killAura();
        if (killAura == null || !killAura.isEnabled() || killAura.target == null)
            return;
        EntityLivingBase candidate = killAura.target;
        float dist = mc.thePlayer.getDistanceToEntity(candidate);
        if (candidate.isDead || dist <= MIN_RANGE || dist > range.getValue().getInput() || candidate.hurtTime >= 3)
            return;
        if (System.currentTimeMillis() - lastThrow < delayFor(dist))
            return;
        int found = projectileSlot(dist);
        if (found == -1 || BadPacketsManager.bad(false, true, false, false, true))
            return;
        float[] solved = solveRotations(candidate, mc.thePlayer.inventory.getStackInSlot(found).getItem());
        if (solved == null)
            return;
        target = candidate;
        slot = found;
        rots = solved;
        aimTicks = 0;
        lastThrow = System.currentTimeMillis();
        state = State.AIMING;
    }

    private void throwNow() {
        oldSlot = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = slot;
        mc.playerController.updateController();
        ItemStack stack = mc.thePlayer.inventory.getCurrentItem();
        if (stack != null) {
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, stack);
            mc.entityRenderer.itemRenderer.resetEquippedProgress2();
        }
        // a rod is held a little longer so the line has time to land before it is put away
        swapBackTicks = stack != null && stack.getItem() instanceof ItemFishingRod ? 4 : 2;
        lastThrow = System.currentTimeMillis();
        state = State.THROWING;
    }

    private void finish() {
        if (oldSlot >= 0 && mc.thePlayer.inventory.currentItem != oldSlot) {
            mc.thePlayer.inventory.currentItem = oldSlot;
            mc.playerController.updateController();
        }
        oldSlot = -1;
        state = State.IDLE;
        target = null;
        rots = null;
        // the slot switch went out this tick, so KillAura waits one more tick before attacking
        cooldown = 1;
    }

    private void reset() {
        if (state == State.THROWING && mc.thePlayer != null)
            finish();
        state = State.IDLE;
        target = null;
        rots = null;
        slot = -1;
    }

    private long delayFor(float dist) {
        if (dist <= 5.0f) return 550L;
        if (dist <= 6.0f) return 450L;
        if (dist <= 7.0f) return 350L;
        return 250L;
    }

    /** A snowball or egg first, a rod only up close. */
    private int projectileSlot(float dist) {
        int rod = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack == null)
                continue;
            Item item = stack.getItem();
            if (item instanceof ItemSnowball || item instanceof ItemEgg)
                return i;
            if (item instanceof ItemFishingRod && rods.getValue() && rod == -1 && dist <= ROD_MAX_RANGE)
                rod = i;
        }
        return rod;
    }

    private float[] solveRotations(EntityLivingBase entity, Item item) {
        boolean rod = item instanceof ItemFishingRod;
        double gravity = rod ? HOOK_GRAVITY : THROWN_GRAVITY;
        double drag = rod ? HOOK_DRAG : THROWN_DRAG;

        double eyeX = mc.thePlayer.posX;
        double eyeY = mc.thePlayer.posY + mc.thePlayer.getEyeHeight() - 0.1;
        double eyeZ = mc.thePlayer.posZ;
        double velX = entity.posX - entity.lastTickPosX;
        double velZ = entity.posZ - entity.lastTickPosZ;
        double aimY = entity.posY + entity.height * 0.7;

        double ticks = Math.max(1.0, mc.thePlayer.getDistanceToEntity(entity) / LAUNCH_SPEED);
        double tx = entity.posX, tz = entity.posZ, pitch = 0;
        // the flight time depends on where they will be, so solve a few times
        for (int i = 0; i < 4; i++) {
            tx = entity.posX + velX * ticks;
            tz = entity.posZ + velZ * ticks;
            double dx = tx - eyeX, dz = tz - eyeZ;
            double[] sol = solvePitch(Math.sqrt(dx * dx + dz * dz), aimY - eyeY, drag, gravity);
            if (sol == null)
                return null;
            pitch = sol[0];
            ticks = Math.min(sol[1], 30.0);
        }

        if (lineOfSight.getValue()
                && mc.theWorld.rayTraceBlocks(new Vec3(eyeX, eyeY + 0.1, eyeZ), new Vec3(tx, aimY, tz)) != null)
            return null;

        float yaw = (float) Math.toDegrees(Math.atan2(tz - eyeZ, tx - eyeX)) - 90f;
        float base = Arsenic.getArsenic().getSilentRotationManager().yaw;
        return new float[]{base + MathHelper.wrapAngleTo180_float(yaw - base), (float) pitch};
    }

    /** The pitch whose flight crosses the target's distance at its height, and the ticks it takes. */
    private double[] solvePitch(double horiz, double dy, double drag, double gravity) {
        double prevErr = Double.NaN, prevPitch = 0, prevTicks = 0;
        for (double pitch = 70.0; pitch >= -80.0; pitch -= 0.5) {
            double[] r = simulate(pitch, horiz, drag, gravity);
            if (r == null) {
                prevErr = Double.NaN;
                continue;
            }
            double err = r[0] - dy;
            if (!Double.isNaN(prevErr) && prevErr < 0.0 && err >= 0.0) {
                double f = -prevErr / (err - prevErr);
                return new double[]{prevPitch + (pitch - prevPitch) * f, prevTicks + (r[1] - prevTicks) * f};
            }
            prevErr = err;
            prevPitch = pitch;
            prevTicks = r[1];
        }
        return null;
    }

    private double[] simulate(double pitch, double horiz, double drag, double gravity) {
        double rad = Math.toRadians(pitch);
        double mh = Math.cos(rad) * LAUNCH_SPEED;
        double my = -Math.sin(rad) * LAUNCH_SPEED;
        if (mh < 0.02)
            return null;
        double h = 0, y = 0;
        for (int t = 1; t <= MAX_FLIGHT_TICKS; t++) {
            double nh = h + mh, ny = y + my;
            if (nh >= horiz) {
                double f = (horiz - h) / (nh - h);
                return new double[]{y + (ny - y) * f, (t - 1) + f};
            }
            h = nh;
            y = ny;
            mh *= drag;
            my *= drag;
            my -= gravity;
            if (mh < 0.02)
                return null;
        }
        return null;
    }

    private KillAura killAura() {
        return Arsenic.getArsenic().getModuleManager().getModuleByClass(KillAura.class);
    }

    private boolean moduleEnabled(Class<? extends Module> type) {
        Module module = Arsenic.getArsenic().getModuleManager().getModuleByClass(type);
        return module != null && module.isEnabled();
    }
}
