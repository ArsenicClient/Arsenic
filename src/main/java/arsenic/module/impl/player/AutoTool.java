package arsenic.module.impl.player;

import arsenic.utils.io.Keys;
import com.mojang.blaze3d.platform.InputConstants;
import arsenic.module.ModuleTier;
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
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Switches to the fastest hotbar tool for the block you are mining, then swaps back. */
@ModuleInfo(name = "AutoTool", description = "Switches to the best tool while mining", category = ModuleCategory.PLAYER, hidden = true, tier = ModuleTier.LEGIT)
public class AutoTool extends Module {
    public final BooleanProperty shiftOnly = new BooleanProperty("Shift Only", false);
    public final BooleanProperty saveDurability = new BooleanProperty("Save Durability", false);
    public final DoubleProperty minDurability = new DoubleProperty("Min Durability", new DoubleValue(1, 100, 10, 1));


    private static final boolean DISABLE_WHILE_RIGHT_CLICK = true;
    private static final boolean REQUIRE_MOUSE_DOWN = true;
    private static final boolean SWAP_BACK = true;
    private int previousSlot = -1;
    private BlockPos currentBlock;

    @Override
    public void onDisable() {
        resetVariables();
    }

    public void setSlot(final int currentItem) {
        if (currentItem == -1 || mc.player == null) {
            return;
        }
        mc.player.getInventory().setSelectedSlot(currentItem);
    }

    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.player == null || mc.level == null) {
            previousSlot = -1;
            return;
        }
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
            int slot = findTool(mc.level.getBlockState(currentBlock));
            if (slot == -1) {
                return;
            }
            if (previousSlot == -1) {
                previousSlot = mc.player.getInventory().getSelectedSlot();
            }
            setSlot(slot);
        }
    };

    /** Best hotbar tool for the block; with Save Durability, nearly broken tools are skipped. */
    private int findTool(BlockState state) {
        if (!saveDurability.getValue()) {
            return PlayerUtils.getTool(state);
        }
        if (state.isAir()) {
            return -1;
        }
        float best = 1.0f;
        int bestSlot = -1;
        int min = (int) Math.round(minDurability.getValue().getInput());
        for (int i = 0; i < Inventory.getSelectionSize(); ++i) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() <= min) {
                continue;
            }
            float speed = PlayerUtils.getEfficiency(stack, state);
            if (speed > best) {
                best = speed;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

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
