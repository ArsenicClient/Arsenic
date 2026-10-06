package arsenic.utils.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import org.lwjgl.input.Mouse;

/** The two ways KillAura blocks with a sword while it fights. */
public final class AutoBlocker {

    public enum Mode { None, Legit, Hypixel }

    private static final Minecraft mc = Minecraft.getMinecraft();

    private int stage;
    private boolean cycling;

    /** True while the Hypixel cycle is running, so attacks must wait for its window. */
    public boolean isCycling() {
        return cycling;
    }

    /**
     * Advances the Hypixel cycle by one tick. Three ticks: block, switch the server slot away (which drops the
     * block), switch back. Returns whether an attack is allowed this tick: always true when the cycle is not
     * running, only on the last tick of it otherwise, and then only if nothing else sent a conflicting packet.
     */
    public boolean tickHypixel(boolean active) {
        if (!active) {
            abort();
            cycling = false;
            return true;
        }
        cycling = true;
        int slot = mc.thePlayer.inventory.currentItem;
        switch (stage) {
            case 0:
                BadPacketsManager.sendSilently(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
                if (!mc.thePlayer.isUsingItem())
                    mc.thePlayer.setItemInUse(mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
                stage = 1;
                return false;
            case 1:
                BadPacketsManager.sendSilently(new C09PacketHeldItemChange(slot % 7 + (int) (Math.random() * 2) + 1));
                stage = 2;
                return false;
            default:
                BadPacketsManager.sendSilently(new C09PacketHeldItemChange(slot));
                stage = 0;
                return !BadPacketsManager.bad(true, false, false, true, false);
        }
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

    /** Puts the server back to normal if the cycle is switched off or interrupted part-way. */
    public void abort() {
        if (stage == 1)
            BadPacketsManager.sendSilently(new C07PacketPlayerDigging(
                    C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
        else if (stage == 2)
            BadPacketsManager.sendSilently(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
        stage = 0;
    }

    public void reset() {
        if (mc.thePlayer != null && mc.getNetHandler() != null)
            abort();
        stage = 0;
        cycling = false;
    }
}
