package arsenic.module.impl.combat;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventMovementInput;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.entity.EntityLivingBase;

@ModuleInfo(name = "WTap",category = ModuleCategory.GHOST)
public class WTap extends Module {
    public final DoubleProperty hurtTime = new DoubleProperty("HurtTime",new DoubleValue(1,10,10,1));
    boolean stop;

    @EventLink
    public final Listener<EventAttack> eventAttackListener = event -> {
        if (event.getTarget() != null && event.getTarget() instanceof EntityLivingBase) {
            if (((EntityLivingBase) event.getTarget()).hurtTime == hurtTime.getValue().getInput()){
                stop = true;
            }
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventMovementInput> movementInputListener = event -> {
        if (mc.thePlayer.isSprinting()) {
            if (stop) {
                event.setSpeed(0);
                stop = false;
            }
        }
    };
}
