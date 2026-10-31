package arsenic.module.impl.movement;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.HotbarUse;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Throws a wind charge straight down just before you land from a long fall: the burst throws you up and the fall
 * damage is gone. The rotation is silent and the charge goes out from the hotbar, which is put back right after.
 */
@ModuleInfo(name = "AutoWindCharge", description = "Throws a wind charge at the ground to cancel a long fall", category = ModuleCategory.MOVEMENT, tier = ModuleTier.BLATANT)
public class AutoWindCharge extends Module {

    /** How far above the ground to throw, in blocks: a falling player covers one or two a tick. */
    private static final double THROW_HEIGHT = 3.5;
    private static final int COOLDOWN_TICKS = 20;

    public final DoubleProperty minFall = new DoubleProperty("Min Fall", new DoubleValue(3, 30, 8, 1));

    private final HotbarUse swap = new HotbarUse();
    private int cooldown;
    private boolean want;

    @Override
    public boolean isSwappingHotbar() {
        return swap.active();
    }

    @Override
    protected void onDisable() {
        swap.release();
        want = false;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        swap.tick();
        if (cooldown > 0)
            cooldown--;
        want = cooldown == 0 && mc.gui.screen() == null && falling() && groundBelow() <= THROW_HEIGHT
                && HotbarUse.find(s -> s.is(Items.WIND_CHARGE)) != -1;
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (!want)
            return;
        event.setPitch(90f);
        event.setSpeed(180f);
        event.setPreventDuplicateLook(true);
        event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        if (!want || event.getPitch() < 85f)
            return;
        int slot = HotbarUse.find(s -> s.is(Items.WIND_CHARGE) && !mc.player.getCooldowns().isOnCooldown(s));
        if (slot == -1)
            return;
        swap.use(slot);
        cooldown = COOLDOWN_TICKS;
        want = false;
    };

    private boolean falling() {
        return !mc.player.onGround() && !mc.player.isFallFlying() && !mc.player.isInWater() && !mc.player.isInLava()
                && mc.player.getDeltaMovement().y < -0.6
                && mc.player.fallDistance >= minFall.getValue().getInput();
    }

    /** Blocks between the feet and the ground below, or a large number over the void. */
    private double groundBelow() {
        Vec3 from = mc.player.position();
        Vec3 to = from.add(0, -16, 0);
        var hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.BLOCK ? from.y - hit.getLocation().y : 99;
    }
}
