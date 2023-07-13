package arsenic.module.impl.combat;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import net.minecraft.client.settings.KeyBinding;

@ModuleInfo(name = "WTap",category = ModuleCategory.GHOST)
public class WTap extends Module { //really dogshit atm will be improved later

    //idk if this even works (not tested)
    @EventLink
    public final Listener<EventAttack> eventAttackListener = eventAttack -> {
        mc.thePlayer.setSprinting(false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(),false);
    };
}
