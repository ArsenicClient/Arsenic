package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
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
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Bind it to a key: turning it on throws an ender pearl where you look, and the moment the pearl teleports you it
 * throws a wind charge straight down (silently) for a launch, optionally with a jump, then turns itself off.
 */
@ModuleInfo(name = "PearlWindCharge", description = "Throws a pearl and a wind charge the moment the pearl lands", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class PearlWindCharge extends Module {

    /** Give up if the pearl has not teleported us after this long (it fell in the void or hit nothing). */
    private static final int TIMEOUT_TICKS = 200;
    /** Ticks the jump key is held, as a tap. */
    private static final int JUMP_TICKS = 2;

    public final BooleanProperty autoSwap = new BooleanProperty("Auto Swap", true);
    public final BooleanProperty jump = new BooleanProperty("Jump", true);

    private final HotbarUse swap = new HotbarUse();
    private boolean thrown;
    private volatile boolean teleported;
    private boolean charged;
    private int waitTicks;
    private int jumpTicks;

    @Override
    public boolean isSwappingHotbar() {
        return swap.active();
    }

    @Override
    protected void onEnable() {
        thrown = false;
        teleported = false;
        charged = false;
        waitTicks = 0;
        jumpTicks = 0;
    }

    @Override
    protected void onDisable() {
        swap.release();
        if (jumpTicks > 0 && mc.player != null)
            Keys.setDown(mc.options.keyJump, mc.gui.screen() == null && Keys.isPhysicallyDown(mc.options.keyJump));
        jumpTicks = 0;
        thrown = false;
        teleported = false;
        charged = false;
    }

    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onIncoming = event -> {
        if (!thrown || charged)
            return;
        Packet<?> packet = event.getPacket();
        if (packet instanceof ClientboundPlayerPositionPacket) {
            teleported = true;
        } else if (packet instanceof ClientboundBundlePacket bundle) {
            for (Packet<?> sub : bundle.subPackets()) {
                if (sub instanceof ClientboundPlayerPositionPacket) {
                    teleported = true;
                    break;
                }
            }
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.gameMode == null)
            return;

        // the combo is done: let the jump tap and the slot restore play out, then turn off
        if (charged) {
            swap.tick();
            if (jumpTicks > 0 && --jumpTicks == 0)
                Keys.setDown(mc.options.keyJump, mc.gui.screen() == null && Keys.isPhysicallyDown(mc.options.keyJump));
            if (jumpTicks == 0)
                setEnabled(false);
            return;
        }

        if (thrown) {
            if (!teleported && ++waitTicks > TIMEOUT_TICKS)
                setEnabled(false);
            return;
        }

        if (mc.player.isUsingItem())
            return;
        ItemStack held = mc.player.getMainHandItem();
        if (!held.is(Items.ENDER_PEARL)) {
            if (!autoSwap.getValue())
                return;
            int slot = HotbarUse.find(s -> s.is(Items.ENDER_PEARL));
            if (slot == -1) {
                setEnabled(false);
                return;
            }
            // swap this tick, throw the next (no slot change in the same tick as the use)
            swap.select(slot);
            return;
        }
        if (mc.player.getCooldowns().isOnCooldown(held))
            return;
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        thrown = true;
        waitTicks = 0;
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (!teleported || charged)
            return;
        event.setPitch(90f);
        event.setSpeed(180f);
        event.setPreventDuplicateLook(true);
        event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        if (!teleported || charged || mc.gameMode == null || event.getPitch() < 85f)
            return;
        int slot = HotbarUse.find(s -> s.is(Items.WIND_CHARGE) && !mc.player.getCooldowns().isOnCooldown(s));
        if (slot == -1) {
            setEnabled(false);
            return;
        }
        swap.use(slot);
        if (jump.getValue() && mc.player.onGround()) {
            Keys.setDown(mc.options.keyJump, true);
            jumpTicks = JUMP_TICKS;
        }
        charged = true;
    };
}
