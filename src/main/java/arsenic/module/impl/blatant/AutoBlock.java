package arsenic.module.impl.blatant;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.functionalinterfaces.IVoidFunction;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemSword;
import org.lwjgl.input.Mouse;

@ModuleInfo(name = "Autoblock", category = ModuleCategory.BLATANT)
public class AutoBlock extends Module {

    public final EnumProperty<bMode> blockMode = new EnumProperty<bMode>("Mode: ", bMode.VANILLA) {
        @Override
        public void onValueUpdate() {
            switch(getValue()) {
                case VANILLA:
                    block = true;
                    break;
                case LEGITSEMI:
                case LEGITSPAM:
                    block = false;
                    break;
            }
        }
    };

    public final EnumProperty<bMode2> downMode = new EnumProperty<>("DownMode: ", bMode2.ONCLICK);
    public final DoubleProperty distance = new DoubleProperty("Distance", new DoubleValue(0, 6, 4, 0.1));


    private boolean block;
    @Override
    protected void postApplyConfig() {
        blockMode.onValueUpdate();
    }

    @Override
    protected void onDisable() {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        }

    @EventLink
    public final Listener<EventTick> eventTickListener = eventTick -> {
        switch(blockMode.getValue()) {
            case LEGITSPAM:
                if (Mouse.isButtonDown(1)) {
                    if (mc.gameSettings.keyBindUseItem.isKeyDown())
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                    KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
                }
                break;
            case LEGITSEMI:
                if (Mouse.isButtonDown(1)) {
                    if (mc.gameSettings.keyBindUseItem.isKeyDown())
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                    if (!mc.gameSettings.keyBindAttack.isKeyDown())
                        KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
                }
                break;
        }
        if(isHoldingSword())
            downMode.getValue().doThing();
    };

    public boolean isHoldingSword() {
        return mc.thePlayer.getHeldItem() != null && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword;
    }

    public boolean shouldBlock() {
        return isHoldingSword() && block;
    }

    public enum bMode {
        VANILLA,
        LEGITSPAM,
        LEGITSEMI
    }

    public enum bMode2 {
        ONCLICK(() -> {}),
        ONLMB(() -> KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), Mouse.isButtonDown(0))),
        DISTANCE(() -> KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), PlayerUtils.getClosestPlayerWithin(Arsenic.getArsenic().getModuleManager().getModuleByClass(AutoBlock.class).distance.getValue().getInput()) != null));

        private final IVoidFunction b;
        bMode2(IVoidFunction b) {
            this.b = b;
        }

        public void doThing() {
            b.voidFunction();
        }
    }
}
