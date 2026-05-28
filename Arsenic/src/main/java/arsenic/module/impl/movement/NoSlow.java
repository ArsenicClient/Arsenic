package arsenic.module.impl.movement;

import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;

@ModuleInfo(name = "NoSlow", category = ModuleCategory.MOVEMENT)
public class NoSlow extends Module {

    public final BooleanProperty food = new BooleanProperty("Food", true);
    public final BooleanProperty blocks = new BooleanProperty("Blocks", true);

    public boolean isUsingItem() {
        boolean isBlock = mc.thePlayer.getHeldItem().getItem().getItemUseAction(mc.thePlayer.getHeldItem()) == net.minecraft.item.EnumAction.BLOCK;
        boolean isFood = mc.thePlayer.getHeldItem().getItem().getItemUseAction(mc.thePlayer.getHeldItem()) == net.minecraft.item.EnumAction.EAT;
        return !(isBlock && blocks.getValue() || isFood && food.getValue());
    }
}
