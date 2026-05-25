package arsenic.module.impl.movement;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMovementInput;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;

@ModuleInfo(name = "NoSlow", category = ModuleCategory.MOVEMENT)
public class NoSlow extends Module {

    public final BooleanProperty food = new BooleanProperty("Food", true);
    public final BooleanProperty blocks = new BooleanProperty("Blocks", true);
    public final DoubleProperty factor = new DoubleProperty("Factor", new DoubleValue(0, 1, 1, 0.01));

    @RequiresPlayer
    @EventLink
    public final Listener<EventMovementInput> onMove = event -> {
        if (!mc.thePlayer.isUsingItem())
            return;

        if (mc.thePlayer.getHeldItem() == null)
            return;

        boolean isBlock = mc.thePlayer.getHeldItem().getItem().getItemUseAction(mc.thePlayer.getHeldItem()) == net.minecraft.item.EnumAction.BLOCK;
        boolean isFood = mc.thePlayer.getHeldItem().getItem().getItemUseAction(mc.thePlayer.getHeldItem()) == net.minecraft.item.EnumAction.EAT;

        if (isBlock && !blocks.getValue())
            return;
        if (isFood && !food.getValue())
            return;

        float f = (float) factor.getValue().getInput();
        event.setSpeed(event.getSpeed() / 0.2f * f);
        event.setStrafe(event.getStrafe() / 0.2f * f);
    };
}
