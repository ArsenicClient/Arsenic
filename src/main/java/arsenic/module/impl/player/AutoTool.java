package arsenic.module.impl.player;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Mouse;

@ModuleInfo(name = "Auto Tool",category = ModuleCategory.PLAYER)
public class AutoTool extends Module {
    public final DoubleProperty hoverDelay = new DoubleProperty("Hover Delay", new DoubleValue(0, 20, 0, 1));
    public final BooleanProperty diableRight = new BooleanProperty("Disable while right click", true);
    public final BooleanProperty requireClicking = new BooleanProperty("Require Mouse down", true);
    public final BooleanProperty swapBack = new BooleanProperty("Swap to previous slot", true);
    private int previousSlot = -1;
    private int ticksHovered;
    private BlockPos currentBlock;
    public void onDisable() {
        resetVariables();
    }

    public void setSlot(final int currentItem) {
        if (currentItem == -1) {
            return;
        }
        mc.thePlayer.inventory.currentItem = currentItem;
    }

    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (!mc.inGameHasFocus || mc.currentScreen != null || (diableRight.getValue() && Mouse.isButtonDown(1)) || !mc.thePlayer.capabilities.allowEdit) {
            resetVariables();
            return;
        }
        if (!Mouse.isButtonDown(0) && requireClicking.getValue()) {
            resetSlot();
            return;
        }
        MovingObjectPosition over = mc.objectMouseOver;
        if (over == null || over.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            resetSlot();
            resetVariables();
            return;
        }
        if (over.getBlockPos().equals(currentBlock)) {
            ticksHovered++;
        }
        else {
            ticksHovered = 0;
        }
        currentBlock = over.getBlockPos();
        if (hoverDelay.getValue().getInput() == 0 || ticksHovered > hoverDelay.getValue().getInput()) {
            int slot = PlayerUtils.getTool(mc.theWorld.getBlockState(currentBlock).getBlock());
            if (slot == -1) {
                return;
            }
            if (previousSlot == -1) {
                previousSlot = mc.thePlayer.inventory.currentItem;
            }
            setSlot(slot);
        }
    };

    private void resetVariables() {
        ticksHovered = 0;
        resetSlot();
        previousSlot = -1;
    }

    private void resetSlot() {
        if (previousSlot == -1 || !swapBack.getValue()) {
            return;
        }
        setSlot(previousSlot);
        previousSlot = -1;
    }
}
