package arsenic.utils.minecraft;

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
 * The packet blocks (Hypixel and Hypixel 2) keep to one rule that Grim's PacketOrderI checks: an attack and a sword
 * release never go out in the same tick (the movement packet ends a tick), in either order. So the release is only
 * ever sent here, never by vanilla: the client's own item use is cleared once the block's tick has gone out, or
 * Minecraft would send a second release itself at the start of the next tick, before anything else. An attack from
 * anywhere (Hitflick, a clicker) in a tick that already released is dropped, and a release waits a tick if an attack
 * already went out in this one.
 */
public final class AutoBlocker {

    public enum Mode {
        None,
        Legit,
        /** From Yuri: attack, block the next tick, release the tick after, attack again. */
        Hypixel,
        /** From OpenB4: attack, interact and block in one tick, as a left then right click does; release the next. */
        Hypixel2
    }

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double HYPIXEL_RANGE = 3.0;

    /** The server sees the sword up. */
    private boolean blocking;
    private boolean blockAfterAttack;
    private boolean canAttack = true;
    /** What already went out since the last movement packet. */
    private boolean attackedThisTick, releasedThisTick;

    /** Whether the first-person sword is drawn blocking for a packet block that is fighting (the real one lasts a tick). */
    private static volatile boolean renderBlocking;

    public static boolean isPacketMode(Mode mode) {
        return mode == Mode.Hypixel || mode == Mode.Hypixel2;
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
     * Before KillAura attacks this tick. A block from the last tick comes down now, and nothing attacks in this tick.
     * Hypixel then raises the sword again the tick after an attack.
     */
    public void tickPacket(Mode mode, EntityPlayer target, boolean active) {
        canAttack = true;
        if (blocking) {
            canAttack = false;
            if (!attackedThisTick)
                release();
            return;
        }
        boolean inRange = active && target != null && mc.thePlayer.getDistanceToEntity(target) <= HYPIXEL_RANGE;
        if (mode == Mode.Hypixel && blockAfterAttack && inRange) {
            block();
            canAttack = false;
        }
        blockAfterAttack = false;
    }

    /** Called by KillAura once its attack for this tick went out, with the entity hit and where the look ray met it. */
    public void onAttack(Mode mode, Entity hit, Vec3 hitVec, boolean active) {
        if (mode == Mode.Hypixel) {
            blockAfterAttack = true;
        } else if (mode == Mode.Hypixel2 && active && hit != null && hitVec != null && !releasedThisTick
                && mc.thePlayer.getDistanceToEntity(hit) <= HYPIXEL_RANGE) {
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

    public void stopPacket() {
        if (blocking && mc.thePlayer != null && mc.getNetHandler() != null)
            release();
        blocking = false;
        blockAfterAttack = false;
        canAttack = true;
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
