package arsenic.utils.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.input.Mouse;

/** The sword block KillAura uses while it fights. */
public final class AutoBlocker {

    public enum Mode { None, Legit }

    private static final Minecraft mc = Minecraft.getMinecraft();

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
}
