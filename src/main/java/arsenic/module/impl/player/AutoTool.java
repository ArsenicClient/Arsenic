package arsenic.module.impl.player;

import org.lwjgl.input.Keyboard;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.input.Mouse;

@ModuleInfo(name = "AutoTool",category = ModuleCategory.PLAYER, hidden = true)
public class AutoTool extends Module {
    /**
     * Only swap while sneaking. Off, the module is always working; on, it only acts when you ask
     * it to, which makes it invisible to anyone watching your hotbar the rest of the time.
     */
    public final BooleanProperty shiftOnly = new BooleanProperty("Shift Only", false);

    // The rest were already fixed on the only values that behave correctly: swap on mouse-down,
    // not on hover; never fight a right-click; always swap back.

    /** Don't swap while the right button is held - that is eating, blocking or placing. */
    private static final boolean DISABLE_WHILE_RIGHT_CLICK = true;
    /** Swap when actually mining, not merely from looking at a block. */
    private static final boolean REQUIRE_MOUSE_DOWN = true;
    /** Return to the slot the player chose once the swap is no longer needed. */
    private static final boolean SWAP_BACK = true;
    private int previousSlot = -1;
    private BlockPos currentBlock;

    @Override
    public void onDisable() {
        resetVariables();
    }

    public void setSlot(final int currentItem) {
        if (currentItem == -1) {
            return;
        }
        mc.player.inventory.currentItem = currentItem;
    }

    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (!mc.inGameHasFocus || mc.gui.screen() != null || (DISABLE_WHILE_RIGHT_CLICK && Mouse.isButtonDown(1)) || !mc.player.capabilities.allowEdit) {
            resetVariables();
            return;
        }
        if (shiftOnly.getValue() && !Keyboard.isKeyDown(mc.options.keyBindSneak.getKeyCode())) {
            resetVariables();
            return;
        }
        if (!Mouse.isButtonDown(0) && REQUIRE_MOUSE_DOWN) {
            resetSlot();
            return;
        }
        MovingObjectPosition over = mc.hitResult;
        if (over == null || over.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            resetSlot();
            resetVariables();
            return;
        }
        currentBlock = over.getBlockPos();
        {
            int slot = PlayerUtils.getTool(mc.level.getBlockState(currentBlock).getBlock());
            if (slot == -1) {
                return;
            }
            if (previousSlot == -1) {
                previousSlot = mc.player.inventory.currentItem;
            }
            setSlot(slot);
        }
    };

    private void resetVariables() {
        resetSlot();
        previousSlot = -1;
    }

    private void resetSlot() {
        if (previousSlot == -1 || !SWAP_BACK) {
            return;
        }
        setSlot(previousSlot);
        previousSlot = -1;
    }
}
