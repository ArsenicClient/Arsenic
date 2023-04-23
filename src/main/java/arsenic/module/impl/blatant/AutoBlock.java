package arsenic.module.impl.blatant;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.event.impl.EventUpdate;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.EnumProperty;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

@ModuleInfo(name = "Autoblock", category = ModuleCategory.BLATANT)
public class AutoBlock extends Module {

    public EnumProperty<bMode> blockMode = new EnumProperty<bMode>("Mode: ", bMode.HYPIXEL) {
        @Override
        public void onValueUpdate() {
            switch(getValue()) {
                case VANILLA:
                    block = true;
                    break;
                case HYPIXEL:
                case LEGITSEMI:
                case LEGITSPAM:
                    block = false;
                    break;
            }
        }
    };
    private boolean block;

    @Override
    protected void postApplyConfig() {
        blockMode.onValueUpdate();
    }

    @EventLink
    public final Listener<EventTick> eventTickListener = eventTick -> {
        switch(blockMode.getValue()) {
            case LEGITSPAM:
                if(Mouse.isButtonDown(1) || Keyboard.isKeyDown(mc.gameSettings.keyBindUseItem.getKeyCode())) {
                    if(mc.gameSettings.keyBindUseItem.isKeyDown())
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                    KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
                }
                break;
            case LEGITSEMI:
                if(Mouse.isButtonDown(1) || Keyboard.isKeyDown(mc.gameSettings.keyBindUseItem.getKeyCode())) {
                    if(mc.gameSettings.keyBindUseItem.isKeyDown())
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                    if(!mc.gameSettings.keyBindAttack.isKeyDown())
                        KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
                }
                break;
        }
    };

    @EventLink
    public final Listener<EventUpdate.Post> eventUpdateListener =  event -> {
        if(blockMode.getValue() == bMode.HYPIXEL) {

        }
    };


    public boolean shouldBlock() {
        return block;
    }

    public enum bMode {
        VANILLA,
        HYPIXEL,
        LEGITSPAM,
        LEGITSEMI
    }
}
