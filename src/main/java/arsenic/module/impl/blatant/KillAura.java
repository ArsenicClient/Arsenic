package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.security.SecureRandom;

import static arsenic.utils.lag.LagManager.acquire;
import static arsenic.utils.lag.LagManager.isHolding;
import static arsenic.utils.lag.LagManager.release;
import static arsenic.utils.lag.LagManager.sendPacket;

@ModuleInfo(name = "KillAura", category = ModuleCategory.BLATANT)
public class KillAura extends Module {

    public RangeProperty speed = new RangeProperty("speed", new RangeValue(1, 360, 20, 50,1));
    public RangeProperty aps = new RangeProperty("APS", new RangeValue(1, 20, 10, 1, 1));
    public final EnumProperty<AutoBlockMode> autoBlock = new EnumProperty<>("AutoBlock", AutoBlockMode.Off);

    public EntityPlayer target = null;
    private boolean wasUsingItem;

    private boolean blocking = false; // true while the sword is "in use" (packet block)
    private int blockTick = 0;        // tick-state machine: 0 = release-only tick, 1 = attack + re-block tick

    private final MSTimer attackTimer = new MSTimer();


    @Override
    protected void onEnable() {
        resetState();
    }

    @Override
    protected void onDisable() {
        if (autoBlock.getValue() == AutoBlockMode.Legit) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        } else {
            releaseBlockPacket();
            disarmBlink();
        }
        resetState();
    }

    private void resetState() {
        target = null;
        blocking = false;
        blockTick = 0;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> eventSilentRotationListener = event -> {
        target = TargetManager.getTarget();
        if (target == null)
            return;
        float[] rots = RotationUtils.getRotationsToEntity(target); //smoothing is already done in rotation manager.
        event.setYaw(rots[0]);
        event.setPitch(rots[1]);
        event.setSpeed((float) speed.getValue().getRandomInRange());
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> eventTickListener = event -> {
        boolean usingItem = mc.thePlayer.isUsingItem();
        AutoBlockMode mode = autoBlock.getValue();
        boolean auto = mode != AutoBlockMode.Off && holdingSword();
        MovingObjectPosition raytrace = event.getRayTraceEntity();
        boolean inRange = target != null
                && raytrace != null
                && raytrace.entityHit != null
                && RotationUtils.getDistanceToEntityBox(raytrace.entityHit) <= 3;

        if (!inRange) {
            stopBlocking();
            wasUsingItem = usingItem;
            return;
        }

        boolean ready = attackTimer.hasTimeElapsed(getAttackDelay());

        if (!auto) {
            // AutoBlock off (or no sword) -> original vanilla KillAura behaviour
            if (ready && !usingItem && !wasUsingItem) {
                attack(raytrace);
                attackTimer.reset();
            }
            wasUsingItem = usingItem;
            return;
        }

        if (mode == AutoBlockMode.Legit) {
            handleLegit(raytrace, ready);
        } else {
            handleHypixel(raytrace, ready);
        }
        wasUsingItem = usingItem;
    };


    /**
     * Legit autoblock: drives the real use-item keybind, so the server sees a
     * genuine player blocking. Blockhit style — hold block between hits, drop it
     * for the swing, then re-block.
     */
    private void handleLegit(MovingObjectPosition raytrace, boolean ready) {
        if (ready) {
            if (blocking) setBlockKey(false);
            attack(raytrace);
            attackTimer.reset();
        } else if (!blocking) {
            setBlockKey(true);
        }
    }

    private void setBlockKey(boolean state) {
        if (blocking == state)
            return;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), state);
        blocking = state;
    }

    private void handleHypixel(MovingObjectPosition raytrace, boolean ready) {
        armBlink();

        if (!ready) {
            // idle between hits: keep the block up (no attack this tick, so a plain place is safe)
            if (!blocking && blockTick == 0)
                placeBlockPacket();
            return;
        }

        switch (blockTick) {
            case 0:
                // release-only tick (no swing)
                if (blocking)
                    releaseBlockPacket();
                blockTick = 1;
                break;
            default:
                // attack + re-block tick (interact combo first so the place isn't flagged)
                attack(raytrace);
                attackTimer.reset();
                interactBlockPacket(raytrace);
                blockTick = 0;
                break;
        }

        flushBlink();
    }


    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> renderWorldLast = event -> {
        if(target == null)
            return;
        RenderUtils.drawCircle(target, event.partialTicks, 0.7, Arsenic.getInstance().getThemeManager().getCurrentTheme().getMainColor(), 255);
        RenderUtils.resetColor();
    };


    private void attack(MovingObjectPosition raytrace) {
        mc.thePlayer.swingItem();
        mc.playerController.attackEntity(mc.thePlayer, raytrace.entityHit);
    }

    private boolean holdingSword() {
        return mc.thePlayer.getCurrentEquippedItem() != null
                && mc.thePlayer.getCurrentEquippedItem().getItem() instanceof ItemSword;
    }

    // ── block start/stop (packet) ───────────────────────────────────────

    private void placeBlockPacket() {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null)
            return;
        sendPacket(new C08PacketPlayerBlockPlacement(held));
        mc.thePlayer.setItemInUse(held, 72000);
        blocking = true;
    }

    private void interactBlockPacket(MovingObjectPosition raytrace) {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null)
            return;
        if (raytrace.entityHit != null) {
            Entity hit = raytrace.entityHit;
            sendPacket(new C02PacketUseEntity(hit, new Vec3(
                    raytrace.hitVec.xCoord - hit.posX,
                    raytrace.hitVec.yCoord - hit.posY,
                    raytrace.hitVec.zCoord - hit.posZ)));
            sendPacket(new C02PacketUseEntity(hit, C02PacketUseEntity.Action.INTERACT));
        }
        sendPacket(new C08PacketPlayerBlockPlacement(held));
        mc.thePlayer.setItemInUse(held, 72000);
        blocking = true;
    }

    private void releaseBlockPacket() {
        if (!blocking)
            return;
        sendPacket(new C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                BlockPos.ORIGIN,
                EnumFacing.DOWN));
        mc.thePlayer.stopUsingItem();
        blocking = false;
    }

    private void stopBlocking() {
        if (autoBlock.getValue() == AutoBlockMode.Legit) {
            if (blocking)
                setBlockKey(false);
        } else {
            releaseBlockPacket();
            disarmBlink();
        }
        blockTick = 0;
    }

    // ── blink batching (Arsenic's LagManager is the blink buffer) ────────

    private void armBlink() {
        if (!isHolding(KillAura.class))
            acquire(KillAura.class);
    }

    private void disarmBlink() {
        if (isHolding(KillAura.class))
            release(KillAura.class);
    }

    private void flushBlink() {
        release(KillAura.class);
        acquire(KillAura.class);
    }

    private long getAttackDelay() {
        double x = aps.getValue().getMax();
        double y = aps.getValue().getMin();
        float finalValue = getRandom((float) x, (float) y) + 6;
        return (long) (1000L / finalValue);
    }

    public static float getRandom(float min, float max) {
        SecureRandom random = new SecureRandom();
        return random.nextFloat() * (max - min) + min;
    }

    public enum AutoBlockMode {
        Off,
        Legit,
        Hypixel
    }
}
