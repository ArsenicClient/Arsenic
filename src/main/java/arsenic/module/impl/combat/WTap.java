package arsenic.module.impl.combat;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.client.C0BPacketEntityAction;

@ModuleInfo(name = "WTap",category = ModuleCategory.GHOST)
public class WTap extends Module {

    int ticks = 0;

    @EventLink
    public final Listener<EventAttack> eventAttackListener = eventAttack -> {
        ticks = 0;
    };

    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.thePlayer.isSprinting()) {
            ticks++;
            switch (ticks) {
                case 2:
                    mc.thePlayer.setSprinting(false);
                case 3:
                    mc.thePlayer.setSprinting(true);
            }
        }
    };
}
