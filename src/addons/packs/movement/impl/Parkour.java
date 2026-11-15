import arsenic.module.ModuleTier;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMovementInput;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;

/**
 * Presses jump on the last tick you can still jump before sprinting off a block edge. Vanilla moves an entity on Y
 * before X/Z, so the tick where nothing is left under your hitbox is still an on-ground tick: the last one a jump works.
 * "Ticks Early" jumps that many ticks sooner, predicting your position from last tick's movement. It only presses the
 * jump key: it never changes your speed or direction. Nothing happens while sneaking, in the air, or with a GUI open.
 */
@ModuleInfo(name = "Parkour", description = "Jumps on the last tick before you sprint off a block edge", category = ModuleCategory.MOVEMENT, tier = ModuleTier.LEGIT)
public class Parkour extends Module {

    public final DoubleProperty ticksEarly = new DoubleProperty("Ticks Early", new DoubleValue(0, 5, 0, 1));

    // on the ground, vanilla motionY is -0.0784 every tick
    private static final double GROUND_CHECK = 0.08;
    // a bigger step is a teleport or a respawn, not walking
    private static final double MAX_STEP = 1.0;

    private boolean jumpNow;

    @Override
    protected void onDisable() {
        jumpNow = false;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        jumpNow = false;
        EntityPlayerSP p = mc.thePlayer;
        if (mc.currentScreen != null || !p.onGround || !p.isSprinting() || p.isSneaking() || p.moveForward <= 0) return;

        // EventTick fires before prevPos is updated: this is how far we moved last tick
        double stepX = p.posX - p.prevPosX, stepZ = p.posZ - p.prevPosZ;
        if (Math.hypot(stepX, stepZ) > MAX_STEP) return;

        int early = (int) ticksEarly.getValue().getInput();
        for (int i = 0; i <= early; i++) {
            double ox = stepX * i, oz = stepZ * i;
            if (!mc.theWorld.isBlockLoaded(new BlockPos(p.posX + ox, p.posY - 1, p.posZ + oz))) return;
            if (!groundUnder(p, ox, oz)) {
                jumpNow = true;
                return;
            }
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventMovementInput> onInput = event -> {
        if (jumpNow) {
            event.setJump(true);
            jumpNow = false;
        }
    };

    /** True if anything solid is right under the whole hitbox, shifted by (ox, oz). */
    private static boolean groundUnder(EntityPlayerSP p, double ox, double oz) {
        AxisAlignedBB bb = p.getEntityBoundingBox();
        AxisAlignedBB feet = new AxisAlignedBB(bb.minX + ox, bb.minY - GROUND_CHECK, bb.minZ + oz,
                bb.maxX + ox, bb.minY, bb.maxZ + oz);
        return !mc.theWorld.getCollidingBoundingBoxes(p, feet).isEmpty();
    }
}
