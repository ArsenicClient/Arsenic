package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.io.Keys;
import arsenic.utils.minecraft.HotbarUse;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Puts the elytra on, jumps into a glide and throws a wind charge at the ground just before touching it, so the burst
 * throws you back up and you keep bouncing for mace hits. Hold a chestplate to stop.
 */
@ModuleInfo(name = "ElytraWindCharge", description = "Glides on an elytra and bounces off the ground with wind charges", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class ElytraWindCharge extends Module {

    /** 400 ms between glide starts and between charges, as ticks. */
    private static final int COOLDOWN_TICKS = 8;
    /** Throw height in blocks at a standstill and at full forward speed: the faster you go, the later it is thrown. */
    private static final double SLOW_HEIGHT = 1.2, FAST_HEIGHT = 0.4;

    public final BooleanProperty equipElytra = new BooleanProperty("Equip Elytra", true);
    public final BooleanProperty autoJump = new BooleanProperty("Auto Jump", true);
    public final BooleanProperty disableOnChestplate = new BooleanProperty("Disable On Chestplate", true);

    private final HotbarUse swap = new HotbarUse();
    private int glideCooldown, chargeCooldown;
    private boolean want, jumpHeld;
    private float throwPitch = 88f;

    @Override
    public boolean isSwappingHotbar() {
        return swap.active();
    }

    @Override
    protected void onEnable() {
        glideCooldown = 0;
        chargeCooldown = 0;
        want = false;
    }

    @Override
    protected void onDisable() {
        swap.release();
        want = false;
        releaseJump();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        swap.tick();
        if (glideCooldown > 0)
            glideCooldown--;
        if (chargeCooldown > 0)
            chargeCooldown--;
        want = false;

        if (mc.gui.screen() != null || !mc.isWindowActive()) {
            releaseJump();
            return;
        }

        // the player picked their chestplate back up: the run is over
        if (disableOnChestplate.getValue() && !swap.active() && isChestplate(mc.player.getMainHandItem())) {
            setEnabled(false);
            return;
        }

        if (!mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
            releaseJump();
            if (!equipElytra.getValue() || swap.active())
                return;
            // a right click with the elytra in hand puts it on and leaves the chestplate in that slot
            int elytra = HotbarUse.find(s -> s.is(Items.ELYTRA));
            if (elytra != -1)
                swap.use(elytra);
            return;
        }

        if (mc.player.onGround()) {
            if (autoJump.getValue() && !jumpHeld) {
                Keys.setDown(mc.options.keyJump, true);
                jumpHeld = true;
            }
            return;
        }
        releaseJump();

        if (!mc.player.isFallFlying()) {
            if (glideCooldown == 0 && !mc.player.isInWater() && !mc.player.isInLava() && mc.player.tryToStartFallFlying()) {
                mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
                glideCooldown = COOLDOWN_TICKS;
            }
            return;
        }

        if (chargeCooldown > 0 || swap.active() || groundBelow() > throwHeight())
            return;
        if (HotbarUse.find(s -> s.is(Items.WIND_CHARGE) && !mc.player.getCooldowns().isOnCooldown(s)) == -1)
            return;
        throwPitch = 87f + ThreadLocalRandom.current().nextFloat() * 3f;
        want = true;
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (!want)
            return;
        event.setPitch(throwPitch);
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
        chargeCooldown = COOLDOWN_TICKS;
        want = false;
    };

    /** Lower when moving fast forward, since the player covers more ground before the charge lands. */
    private double throwHeight() {
        Vec3 motion = mc.player.getDeltaMovement();
        double yaw = Math.toRadians(mc.player.getYRot());
        double forward = motion.x * -Math.sin(yaw) + motion.z * Math.cos(yaw);
        double speed = Math.max(0, Math.min(1, forward));
        return SLOW_HEIGHT - (SLOW_HEIGHT - FAST_HEIGHT) * speed;
    }

    /** Blocks between the feet and the ground below, or a large number over the void. */
    private double groundBelow() {
        Vec3 from = mc.player.position();
        Vec3 to = from.add(0, -256, 0);
        var hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.BLOCK ? from.y - hit.getLocation().y : 256;
    }

    private void releaseJump() {
        if (!jumpHeld)
            return;
        jumpHeld = false;
        Keys.setDown(mc.options.keyJump, Keys.isPhysicallyDown(mc.options.keyJump));
    }

    private static boolean isChestplate(ItemStack stack) {
        return stack.is(Items.NETHERITE_CHESTPLATE) || stack.is(Items.DIAMOND_CHESTPLATE);
    }
}
