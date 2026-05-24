package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;

import java.util.Collection;

@ModuleInfo(name = "AutoWeapon", category = ModuleCategory.GHOST)
public class AutoWeapon extends Module {

    private int prevSlot;
    private boolean onWeapon;

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        MovingObjectPosition mouseOver = mc.objectMouseOver;
        if (mouseOver != null && mouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            if(!onWeapon) {
                prevSlot = mc.thePlayer.inventory.currentItem;
                mc.thePlayer.inventory.currentItem = getMaxDamageSlot();
                onWeapon = true;
            }
        } else if(onWeapon) {
            onWeapon = false;
            mc.thePlayer.inventory.currentItem = prevSlot;
        }
    };

    private int getMaxDamageSlot() {
        int index = -1;
        double damage = -1;
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack == null) continue;
            Collection<AttributeModifier> mods = stack.getAttributeModifiers().values();
            for (AttributeModifier mod : mods) {
                if (mod.getAmount() > damage) {
                    damage = mod.getAmount();
                    index = slot;
                }
            }
        }
        return index;
    }

    private double getSlotDamage(int slot) {
        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
        if (stack == null) return -1;
        Collection<AttributeModifier> mods = stack.getAttributeModifiers().values();
        for (AttributeModifier mod : mods) {
            return mod.getAmount();
        }
        return -1;
    }
}
