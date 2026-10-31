package arsenic.module.impl.ghost;

import arsenic.utils.minecraft.PlayerUtils;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.Priorities;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMovementInput;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventSilentRotation;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.blatant.KillAura;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.BadPacketsManager;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.HitResult;

/**
 * Legit velocity (from Yuri): no packet is changed, the knockback is cut the way a player does it. On the tick the
 * knockback arrives we jump (a jump reset), and for the next few ticks we hit the closest enemy, because a sprinting
 * hit multiplies our own horizontal speed by 0.6. Hits only go out when the look ray really lands on the enemy.
 */
@ModuleInfo(name = "Velocity", category = ModuleCategory.COMBAT, tier = ModuleTier.LEGIT)
public class Velocity extends Module {

    public enum Mode { Legit }

    public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.Legit);
    public final DoubleProperty reduceTicks = new DoubleProperty("Reduce Ticks", new DoubleValue(1, 3, 2, 1));
    public final BooleanProperty jumpReset = new BooleanProperty("Jump Reset", true);

    private static final double RANGE = 3.0;
    private static final float ROTATION_SPEED = 18f;

    private volatile int reduceCounter = -1;
    private volatile boolean shouldJump;
    private int lastAttackTick = -1;
    private Player reduceTarget;

    @Override
    public String getHudInfo() {
        return mode.getValue().name().toLowerCase();
    }

    @Override
    protected void onDisable() {
        reduceCounter = -1;
        shouldJump = false;
        lastAttackTick = -1;
        reduceTarget = null;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onIncoming = event -> {
        if (!(event.getPacket() instanceof ClientboundSetEntityMotionPacket))
            return;
        ClientboundSetEntityMotionPacket packet = (ClientboundSetEntityMotionPacket) event.getPacket();
        if (packet.id() != mc.player.getId() || packet.movement().y <= 0)
            return;
        reduceCounter = 0;
        if (jumpReset.getValue() && mc.player.onGround() && mc.player.isSprinting())
            shouldJump = true;
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventPacket.OutGoing> onOutgoing = event -> {
        if (event.getPacket() instanceof ServerboundAttackPacket)
            lastAttackTick = mc.player.tickCount;
    };

    /** The jump goes in as movement input, so the knockback's upward speed is replaced the way a real jump would. */
    @RequiresPlayer
    @EventLink(Priorities.HIGH)
    public final Listener<EventMovementInput> onMovementInput = event -> {
        if (!shouldJump)
            return;
        shouldJump = false;
        if (mc.player.onGround() && mc.player.isSprinting() && !mc.options.keyJump.isDown())
            event.setJump(true);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        reduceTarget = null;
        if (mc.gui.screen() != null || reduceCounter < 0 || reduceCounter >= (int) reduceTicks.getValue().getInput())
            return;
        reduceCounter++;
        if (lastAttackTick == mc.player.tickCount)
            return;
        reduceTarget = furthestInRange();
        if (reduceTarget == null || killAuraAiming())
            return;
        float[] rots = RotationUtils.getRotationsToEntity(reduceTarget);
        event.setYaw(rots[0]);
        event.setPitch(rots[1]);
        event.setSpeed(ROTATION_SPEED);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onRotationPost = event -> {
        if (reduceTarget == null || mc.player.isUsingItem() || BadPacketsManager.serverUsingItem()
                || lastAttackTick == mc.player.tickCount
                || BadPacketsManager.bad(false, true, true, true, false))
            return;
        HitResult ray = event.getRayTraceEntity();
        Entity hit = ray instanceof net.minecraft.world.phys.EntityHitResult rayHit ? rayHit.getEntity() : null;
        if (!(hit instanceof Player) || !TargetManager.isValidTarget((Player) hit)
                || mc.player.getEyePosition(1f).distanceTo(ray.getLocation()) > RANGE)
            return;
        mc.gameMode.attack(mc.player, hit);
        PlayerUtils.swingItem();
    };

    private boolean killAuraAiming() {
        KillAura killAura = Arsenic.getArsenic().getModuleManager().getModuleByClass(KillAura.class);
        return killAura != null && killAura.isEnabled() && killAura.target != null;
    }

    /** Yuri pushes off the furthest enemy still in reach. */
    private Player furthestInRange() {
        Player best = null;
        double bestDist = -1;
        for (Player player : mc.level.players()) {
            if (player == mc.player || !player.isAlive() || player.getHealth() <= 0 || !TargetManager.isValidTarget(player))
                continue;
            if (!mc.player.hasLineOfSight(player))
                continue;
            double dist = RotationUtils.getDistanceToEntityBox(player);
            if (dist <= RANGE && dist > bestDist) {
                best = player;
                bestDist = dist;
            }
        }
        return best;
    }
}
