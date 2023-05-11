package arsenic.module.impl.blatant;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.EnumProperty;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
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
            case VANILLA:
                if(Mouse.isButtonDown(1)) {
                    if(mc.gameSettings.keyBindUseItem.isKeyDown())
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                }
                break;
            case LEGITSPAM:
                if(Mouse.isButtonDown(1)) {
                    if(mc.gameSettings.keyBindUseItem.isKeyDown())
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                    KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
                }
                break;
            case LEGITSEMI:
                if(Mouse.isButtonDown(1)) {
                    if(mc.gameSettings.keyBindUseItem.isKeyDown())
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                    if(!mc.gameSettings.keyBindAttack.isKeyDown())
                        KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
                }
                break;
            case HYPIXEL:
                if(!block && Mouse.isButtonDown(1)) {
                    block = true;
                    mc.getNetHandler().addToSendQueue(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
                } else if (block && !Mouse.isButtonDown(1)) {
                    mc.getNetHandler().addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
                    block = false;
                }
                break;
        }
    };

    public boolean shouldBlock() {
        if(mc.thePlayer.getHeldItem() != null && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword)
            return block;
        return false;
    }

    public enum bMode {
        VANILLA,
        HYPIXEL,
        LEGITSPAM,
        LEGITSEMI
    }
}
