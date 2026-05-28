package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventRunTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemSword;

/**
 * @author Kv
 * @since 1/4/26 (Aus)
 */

@ModuleInfo(name = "BlockHit", category = ModuleCategory.GHOST)
public class BlockHit extends Module {
    public final EnumProperty<mode> blockType = new EnumProperty<>("Mode", mode.Legit);
    public final BooleanProperty rmb = new BooleanProperty("RightClick Only",false);
    public final DoubleProperty hurtime = new DoubleProperty("Hurtime", new DoubleValue(0.0, 10.0, 2.0, 1.0));
    public EntityLivingBase target;
    public boolean down;

    @EventLink
    public final Listener<EventAttack> eventAttackListener = event -> {
        if (event.getTarget() != null && event.getTarget() instanceof EntityLivingBase) {
            target = (EntityLivingBase) event.getTarget();
        }
    };


    @RequiresPlayer
    @EventLink
    public final Listener<EventRunTick> eventTickListener = event -> {
        if(!isPlayerHoldingSword())
            return;
        if(target == null) {
            if(down) release();
            return;
        }
        if(!down && target.hurtTime > hurtime.getValue().getInput()) {
            press();
            return;
        }
        if(down && target.hurtTime <= hurtime.getValue().getInput()) {
            release();
        }
    };

    public boolean isPlayerHoldingSword() {
        return (mc.thePlayer.getCurrentEquippedItem() != null)
                && (mc.thePlayer.getCurrentEquippedItem().getItem() instanceof ItemSword);
    }

    private void release() {
        int key = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(key, false);
        down = false;
        target = null;
    }

    private void press() {
        down = true;
        int key = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(key, true);
        //KeyBinding.onTick(key);
    }



    public enum mode {
        Legit,
    }
}