package arsenic.module.impl.ghost;

import arsenic.utils.minecraft.PlayerUtils;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventRunTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;



@ModuleInfo(name = "BlockHit", category = ModuleCategory.COMBAT)
public class BlockHit extends Module {
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

    @Override
    protected void onDisable() {
        if (down) release();
        super.onDisable();
    }

    public boolean isPlayerHoldingSword() {
        return PlayerUtils.isPlayerHoldingSword();
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
    }
}
