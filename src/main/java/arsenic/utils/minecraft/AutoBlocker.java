package arsenic.utils.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import org.lwjgl.input.Mouse;

/** The sword block KillAura uses while it fights. */
public final class AutoBlocker {

    public enum Mode { None, Legit, Hypixel }

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double HYPIXEL_RANGE = 3.0;

    private boolean hypixelBlocking;
    private boolean blockAfterAttack;
    private boolean hypixelCanAttack = true;

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
     * Hypixel (from Yuri): a one-tick block after every attack. The tick after an attack sends the block, the next
     * tick releases it, and the attack after that waits for the release, so a block, a release and an attack never
     * share a tick. Whether this tick may attack is decided before the release goes out, as Yuri does.
     */
    public void tickHypixel(EntityPlayer target, boolean active) {
        boolean inRange = active && target != null && mc.thePlayer.getDistanceToEntity(target) <= HYPIXEL_RANGE;
        hypixelCanAttack = !BadPacketsManager.bad(false, false, false, true, false);
        if (hypixelBlocking) {
            releaseHypixel();
            hypixelCanAttack = false;
            return;
        }
        if (!inRange) {
            blockAfterAttack = false;
            return;
        }
        if (blockAfterAttack) {
            blockAfterAttack = false;
            sendHypixelBlock();
            hypixelCanAttack = false;
        }
    }

    /** Called by KillAura once its attack for this tick went out. */
    public void onAttack() {
        blockAfterAttack = true;
    }

    public boolean hypixelCanAttack() {
        return hypixelCanAttack && !hypixelBlocking;
    }

    /** Whether the sword is up because of the Hypixel block, so KillAura does not wait on its own use of the item. */
    public boolean isHypixelBlocking() {
        return hypixelBlocking;
    }

    /** Keeps the client's item-use state in step with the block the server sees (its slowdown included). */
    public void keepHypixelUse() {
        if (!hypixelBlocking || mc.thePlayer.isBlocking())
            return;
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held != null)
            mc.thePlayer.setItemInUse(held, held.getMaxItemUseDuration());
    }

    public void stopHypixel() {
        if (hypixelBlocking && mc.thePlayer != null)
            releaseHypixel();
        hypixelBlocking = false;
        blockAfterAttack = false;
        hypixelCanAttack = true;
    }

    private void sendHypixelBlock() {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null)
            return;
        mc.getNetHandler().addToSendQueue(new C08PacketPlayerBlockPlacement(held));
        mc.thePlayer.setItemInUse(held, held.getMaxItemUseDuration());
        hypixelBlocking = true;
    }

    private void releaseHypixel() {
        hypixelBlocking = false;
        mc.getNetHandler().addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                BlockPos.ORIGIN, EnumFacing.DOWN));
        mc.thePlayer.stopUsingItem();
    }
}
