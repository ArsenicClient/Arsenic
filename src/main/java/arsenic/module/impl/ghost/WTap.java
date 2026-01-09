package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventMovementInput;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.PacketUtil;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

@ModuleInfo(name = "WTap",category = ModuleCategory.GHOST)
public class WTap extends Module {
    public int hurtTime = 1;
    public EntityPlayer target;
    public final EnumProperty<wMode> mode = new EnumProperty<>("Mode", wMode.COMBO);
    public boolean hasTapped = false;

    @EventLink
    public final Listener<EventAttack> eventAttackListener = event -> {
        if (event.getTarget() != null && event.getTarget() instanceof EntityPlayer) {
            target = (EntityPlayer) event.getTarget();
            hasTapped = false;
            hurtTime = Math.max(1, PacketUtil.getPlayerPingAsTicks()) + 1;
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventMovementInput> movementInputListener = event -> {
        if(target == null || target.isDead || target.getHealth() <= 0)
            return;

        switch (mode.getValue()) {
            case COMBO:
                double distToTarget = RotationUtils.getDistanceToEntityBox(target);
                double targetDistToPlayer = RotationUtils.getDistanceToEntityBox(mc.thePlayer, target);
                if(distToTarget > 4.5 || target.hurtTime <  hurtTime) {
                    target = null;
                    return;
                }
                if(!hasTapped || (targetDistToPlayer < 3.05 && distToTarget < 2.95)) {
                    event.setSpeed(0);
                    hasTapped = true;
                    return;
                }

                break;
            case NORMAL:
                if (mc.thePlayer.isSprinting() && target.hurtTime == hurtTime) {
                    event.setSpeed(0);
                    target = null;
                }
                break;
        }
    };

    public enum wMode {
        NORMAL,
        COMBO
    }

}
