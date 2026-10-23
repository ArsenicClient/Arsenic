package arsenic.utils.minecraft;

import arsenic.utils.lag.LagManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Mouse;

/**
 * The sword block KillAura uses while it fights.
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

    private static final Minecraft mc = Minecraft.getMinecraft();
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
    /** Whether the sword was up as the server saw it, for each of the last ticks, by ticksExisted. */
    private final boolean[] sentBlocking = new boolean[64];

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
    public void tickLegit(EntityPlayer target, boolean active) {
        boolean userRightClick = mc.currentScreen == null && Mouse.isButtonDown(1);
        boolean block = active && target != null && mc.thePlayer.getDistanceToEntity(target) <= 3.0f
                && mc.thePlayer.hurtTime >= 6 && mc.thePlayer.hurtTime <= 10;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), block || userRightClick);
    }

    /**
     * Before KillAura attacks this tick. Nothing attacks in a tick that starts blocking. Hypixel's block from the last
     * tick comes down now; Hypixel Hold keeps it up while the target is close and we can take damage.
     */
    public void tickPacket(Mode mode, EntityPlayer target, boolean active) {
        ticks++;
        canAttack = true;
        if (!blocking)
            return;
        canAttack = false;
        if (mode == Mode.HypixelHold && active && target != null && canTakeDamage()
                && mc.thePlayer.getDistanceToEntity(target) <= HYPIXEL_RANGE) {
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
        if (!isPacketMode(mode) || !(hit instanceof EntityPlayer))
            return true;
        if (!inReach(hitVec))
            return false;
        EntityPlayer target = (EntityPlayer) hit;
        if (target.getEntityId() == lastHitId && ticks + ticksAhead - lastHitTick < HURT_TICKS)
            return false;
        // the hurt time we see is half a ping old and the attack lands half a ping from now
        return target.hurtTime - ticksAhead <= LagManager.getPingAsTicks();
    }

    /** Called by KillAura once its attack for this tick went out, with the entity hit and where the look ray met it. */
    public void onAttack(Mode mode, Entity hit, Vec3 hitVec, boolean active) {
        if (!isPacketMode(mode) || hit == null)
            return;
        // only attacks whose look ray met the target within reach go out, so take this one as landed
        lastHitId = hit.getEntityId();
        lastHitTick = ticks;
        if (active && !releasedThisTick && canTakeDamage() && inReach(hitVec)) {
            // a right click on the entity: interact at the point looked at, interact, then use the sword
            send(new C02PacketUseEntity(hit, new Vec3(hitVec.xCoord - hit.posX, hitVec.yCoord - hit.posY,
                    hitVec.zCoord - hit.posZ)));
            send(new C02PacketUseEntity(hit, C02PacketUseEntity.Action.INTERACT));
            block();
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
        if (packet instanceof C03PacketPlayer) {
            attackedThisTick = releasedThisTick = false;
        } else if (packet instanceof C02PacketUseEntity
                && ((C02PacketUseEntity) packet).getAction() == C02PacketUseEntity.Action.ATTACK) {
            if (releasedThisTick)
                return true;
            attackedThisTick = true;
        } else if (packet instanceof C07PacketPlayerDigging
                && ((C07PacketPlayerDigging) packet).getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
            releasedThisTick = true;
        }
        return false;
    }

    /**
     * After the tick's movement packet went out. The client's item use from a block is cleared here: it already slowed
     * this tick's movement as the server expects, and left on, vanilla would send its own release next tick.
     */
    public void afterMovement() {
        if (blocking && mc.thePlayer.isUsingItem())
            mc.thePlayer.clearItemInUse();
    }

    /** After the tick's movement packet went out: whether the server now sees the sword up for this tick. */
    public void recordSent(boolean up) {
        sentBlocking[mc.thePlayer.ticksExisted & (sentBlocking.length - 1)] = up;
    }

    /** Whether the sword was up as the server saw it this many ticks ago. */
    public boolean wasBlocking(int ticksAgo) {
        ticksAgo = Math.max(0, Math.min(sentBlocking.length - 1, ticksAgo));
        return sentBlocking[(mc.thePlayer.ticksExisted - ticksAgo) & (sentBlocking.length - 1)];
    }

    public void stopPacket() {
        if (blocking && mc.thePlayer != null && mc.getNetHandler() != null)
            release();
        blocking = false;
        canAttack = true;
        lastHitId = -1;
    }

    /** Whether a hit on us would do damage when a block sent now reaches the server, the same lead as for the target. */
    private boolean canTakeDamage() {
        return mc.thePlayer.hurtTime <= LagManager.getPingAsTicks();
    }

    /** Whether the look ray met the entity within reach of our eyes. */
    private static boolean inReach(Vec3 hitVec) {
        return hitVec != null && mc.thePlayer.getPositionEyes(1f).distanceTo(hitVec) <= HYPIXEL_RANGE;
    }

    /** Keeps the client using the sword for this tick's movement, as a held right click does; afterMovement clears it. */
    private void hold() {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null) {
            release();
            return;
        }
        if (!mc.thePlayer.isUsingItem())
            mc.thePlayer.setItemInUse(held, held.getMaxItemUseDuration());
    }

    private void block() {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null)
            return;
        send(new C08PacketPlayerBlockPlacement(held));
        // the same tick's movement is slowed, as for a real right click
        mc.thePlayer.setItemInUse(held, held.getMaxItemUseDuration());
        blocking = true;
    }

    private void release() {
        blocking = false;
        send(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
        if (mc.thePlayer.isUsingItem())
            mc.thePlayer.clearItemInUse();
    }

    private static void send(Packet<?> packet) {
        mc.getNetHandler().addToSendQueue(packet);
    }
}
