package arsenic.module.impl.player;

import arsenic.utils.io.Keys;
import com.mojang.blaze3d.platform.InputConstants;
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
import arsenic.utils.io.Keys;

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
        mc.player.getInventory().setSelectedSlot(currentItem);
    }

    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (!mc.isWindowActive() || mc.gui.screen() != null || (DISABLE_WHILE_RIGHT_CLICK && Keys.isMouseDown(1)) || !mc.player.getAbilities().mayBuild) {
            resetVariables();
            return;
        }
        if (shiftOnly.getValue() && !Keys.isPhysicallyDown(mc.options.keyShift)) {
            resetVariables();
            return;
        }
        if (!Keys.isMouseDown(0) && REQUIRE_MOUSE_DOWN) {
            resetSlot();
            return;
        }
        HitResult over = mc.hitResult;
        if (over == null || over.getType() != HitResult.Type.BLOCK) {
            resetSlot();
            resetVariables();
            return;
        }
        currentBlock = ((net.minecraft.world.phys.BlockHitResult) over).getBlockPos();
        {
            int slot = PlayerUtils.getTool(mc.level.getBlockState(currentBlock));
            if (slot == -1) {
                return;
            }
            if (previousSlot == -1) {
                previousSlot = mc.player.getInventory().getSelectedSlot();
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
