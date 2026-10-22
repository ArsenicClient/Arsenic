package arsenic.utils.minecraft;

import arsenic.utils.lag.LagManager;
import arsenic.utils.io.Keys;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The block KillAura uses while it fights: the held item that blocks (a shield, or a sword where the server makes
 * swords block), main hand first.
 *
 * The packet blocks (Hypixel and Hypixel Hold) keep to one rule that Grim's PacketOrderI checks: an attack and a sword
 * release never go out in the same tick (the movement packet ends a tick), in either order. So the release is only
 * ever sent here, never by vanilla: the client's own item use is cleared once the block's tick has gone out, or
 * Minecraft would send a second release itself at the start of the next tick, before anything else. An attack from
 * anywhere (Hitflick, a clicker) in a tick that already released is dropped, and a release waits a tick if an attack
 * already went out in this one.
 *
 * Hypixel only attacks when the hit can do damage: our look ray meets the target within 3 blocks (such a hit is taken
 * to land), and the target is out of its hurt time when the attack reaches the server. It only blocks while we can
 * take damage ourselves. Hypixel Hold keeps the sword up between attacks and lowers it the tick before the next one.
 */
public final class AutoBlocker {

    public enum Mode {
        None,
        Legit,
        /** From OpenB4: attack, interact and block in one tick, as a left then right click does; release the next. */
        Hypixel,
        /**
         * Hypixel, but the sword stays up (and slows us, as a held right click does) until the tick before the next
         * attack, when it comes down so the attack can go out on its own tick.
         */
        HypixelHold
    }

    private static final Minecraft mc = Minecraft.getInstance();
    private static final double HYPIXEL_RANGE = 3.0;
    /** Ticks after a hit before the server lets the same entity take damage again (hurt resistance 20, over 10). */
    private static final int HURT_TICKS = 10;

    /** The server sees the sword up. */
    private boolean blocking;
    private boolean canAttack = true;
    /** What already went out since the last movement packet. */
    private boolean attackedThisTick, releasedThisTick;
    /** Our last attack that is taken to have landed, so the target is not hit again inside its hurt time. */
    private int ticks, lastHitTick = Integer.MIN_VALUE / 2, lastHitId = -1;

    /** Whether the first-person sword is drawn blocking for a packet block that is fighting (the real one lasts a tick). */
    private static volatile boolean renderBlocking;

    public static boolean isPacketMode(Mode mode) {
        return mode == Mode.Hypixel || mode == Mode.HypixelHold;
    }

    public static boolean isRenderBlocking() {
        return renderBlocking;
    }

    public static void setRenderBlocking(boolean blocking) {
        renderBlocking = blocking;
    }

    /**
     * Legit: really raises the sword for a moment after we are hit (hurt time 10 to 6) while the target is
     * close, otherwise the key just follows the mouse. KillAura already waits while the client is using an item.
     */
    public void tickLegit(Player target, boolean active) {
        boolean userRightClick = mc.gui.screen() == null && Keys.isMouseDown(1);
        boolean block = active && target != null && mc.player.distanceTo(target) <= 3.0f
                && mc.player.hurtTime >= 6 && mc.player.hurtTime <= 10;
        mc.options.keyUse.setDown(block || userRightClick);
    }

    /**
     * Before KillAura attacks this tick. Nothing attacks in a tick that starts blocking. Hypixel's block from the last
     * tick comes down now; Hypixel Hold keeps it up while the target is close and we can take damage.
     */
    public void tickPacket(Mode mode, Player target, boolean active) {
        ticks++;
        canAttack = true;
        if (!blocking)
            return;
        canAttack = false;
        if (mode == Mode.HypixelHold && active && target != null && canTakeDamage()
                && mc.player.distanceTo(target) <= HYPIXEL_RANGE) {
            hold();
            return;
        }
        if (!attackedThisTick)
            release();
    }

    /**
     * Hypixel Hold, once this tick's look ray is known: lowers the sword when the next tick is going to attack, since a
     * release and an attack cannot share a tick.
     */
    public void releaseBeforeAttack(Mode mode, boolean attackNextTick) {
        if (mode == Mode.HypixelHold && blocking && attackNextTick && !attackedThisTick)
            release();
    }

    /**
     * Whether an attack on this entity would hurt it: our look ray meets it within reach, and it is out of its hurt
     * time by the time the attack reaches the server.
     */
    public boolean shouldHit(Mode mode, Entity hit, Vec3 hitVec) {
        return shouldHit(mode, hit, hitVec, 0);
    }

    /** {@link #shouldHit(Mode, Entity, Vec3)} for an attack this many ticks from now, the look ray kept as it is. */
    public boolean shouldHit(Mode mode, Entity hit, Vec3 hitVec, int ticksAhead) {
        if (!isPacketMode(mode) || !(hit instanceof Player target))
            return true;
        if (!inReach(hitVec))
            return false;
        if (target.getId() == lastHitId && ticks + ticksAhead - lastHitTick < HURT_TICKS)
            return false;
        // the hurt time we see is half a ping old and the attack lands half a ping from now
        return target.hurtTime - ticksAhead <= LagManager.getPingAsTicks();
    }

    /** Called by KillAura once its attack for this tick went out, with the entity hit and where the look ray met it. */
    public void onAttack(Mode mode, Entity hit, Vec3 hitVec, boolean active) {
        if (!isPacketMode(mode) || hit == null)
            return;
        // only attacks whose look ray met the target within reach go out, so take this one as landed
        lastHitId = hit.getId();
        lastHitTick = ticks;
        InteractionHand hand = blockingHand();
        if (active && hand != null && !releasedThisTick && canTakeDamage() && inReach(hitVec)) {
            // a right click on the entity: interact at the point looked at, then use the blocking item
            mc.gameMode.interact(mc.player, hit, new EntityHitResult(hit, hitVec), hand);
            block(hand);
        }
    }

    public boolean canAttack() {
        return canAttack && !blocking && !releasedThisTick;
    }

    /** Whether the sword is up because of a packet block, so KillAura does not wait on its own use of the item. */
    public boolean isBlocking() {
        return blocking;
    }

    /**
     * Every outgoing packet, before it is sent. Returns true to drop it: an attack in a tick that already released.
     * A movement packet starts a new tick.
     */
    public boolean onOutgoing(Packet<?> packet) {
        if (packet instanceof ServerboundMovePlayerPacket || packet instanceof ServerboundClientTickEndPacket) {
            attackedThisTick = releasedThisTick = false;
        } else if (packet instanceof ServerboundAttackPacket) {
            if (releasedThisTick)
                return true;
            attackedThisTick = true;
        } else if (packet instanceof ServerboundPlayerActionPacket action
                && action.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
            releasedThisTick = true;
        }
        return false;
    }

    /**
     * After the tick's movement packet went out. The client's item use from a block is cleared here: it already slowed
     * this tick's movement as the server expects, and left on, vanilla would send its own release next tick.
     */
    public void afterMovement() {
        if (blocking && mc.player.isUsingItem())
            mc.player.stopUsingItem();
    }

    public void stopPacket() {
        if (blocking && mc.player != null && mc.getConnection() != null)
            release();
        blocking = false;
        canAttack = true;
        lastHitId = -1;
    }

    /** Whether a hit on us would do damage when a block sent now reaches the server, the same lead as for the target. */
    private boolean canTakeDamage() {
        return mc.player.hurtTime <= LagManager.getPingAsTicks();
    }

    /** Whether the look ray met the entity within reach of our eyes. */
    private static boolean inReach(Vec3 hitVec) {
        return hitVec != null && mc.player.getEyePosition(1f).distanceTo(hitVec) <= HYPIXEL_RANGE;
    }

    /** The hand holding an item that blocks, main hand first, or null. */
    private static InteractionHand blockingHand() {
        if (mc.player.getMainHandItem().getUseAnimation() == ItemUseAnimation.BLOCK)
            return InteractionHand.MAIN_HAND;
        if (mc.player.getOffhandItem().getUseAnimation() == ItemUseAnimation.BLOCK)
            return InteractionHand.OFF_HAND;
        return null;
    }

    /** Keeps the client using the blocking item for this tick's movement, as a held right click does; afterMovement clears it. */
    private void hold() {
        InteractionHand hand = blockingHand();
        if (hand == null) {
            release();
            return;
        }
        if (!mc.player.isUsingItem())
            mc.player.startUsingItem(hand);
    }

    private void block(InteractionHand hand) {
        // sends the use packet and starts using the item, so the same tick's movement is slowed as for a real right click
        mc.gameMode.useItem(mc.player, hand);
        blocking = true;
    }

    private void release() {
        blocking = false;
        mc.gameMode.releaseUsingItem(mc.player);
        if (mc.player.isUsingItem())
            mc.player.stopUsingItem();
    }
}
