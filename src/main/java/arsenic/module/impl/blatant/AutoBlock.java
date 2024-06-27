package arsenic.module.impl.blatant;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.player.Blink;
import arsenic.module.property.impl.EnumProperty;
import arsenic.utils.minecraft.PacketUtil;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

@ModuleInfo(name = "Autoblock", category = ModuleCategory.BLATANT)
public class AutoBlock extends Module {

    public final EnumProperty<bMode> blockMode = new EnumProperty<>("Mode: ", bMode.VANILLA);

    private boolean blinkAB;
    private boolean block;
    private boolean blink;
    private boolean swapped;
    private int serverSlot;
    public boolean renderBlock;

    @Override
    protected void onEnable() {
        blinkAB = true;
        block = false;
        blink = false;
        swapped = false;
        renderBlock = false;
        super.onEnable();
    }

    @EventLink
    public final Listener<EventTick> eventTickListener = eventTick -> {
        if (nullCheck()) {
            return;
        }
        if (Arsenic.getInstance().getModuleManager().getModuleByClass(KillAura.class).isEnabled() && Arsenic.getInstance().getModuleManager().getModuleByClass(KillAura.class).target != null && isHoldingSword()) {
            if (blockMode.getValue() == bMode.HYPIXEL) {
                renderBlock = true;
                if (blinkAB) {
                    Arsenic.getInstance().getModuleManager().getModuleByClass(Blink.class).setEnabled(true);
                    blink = true;

                    if (serverSlot != mc.thePlayer.inventory.currentItem % 8 + 1) {
                        mc.thePlayer.sendQueue.addToSendQueue(new C09PacketHeldItemChange(serverSlot = mc.thePlayer.inventory.currentItem % 8 + 1));
                        swapped = true;
                        block = false;
                    }

                    blinkAB = false;
                } else {
                    if (serverSlot != mc.thePlayer.inventory.currentItem) {
                        mc.thePlayer.sendQueue.addToSendQueue(new C09PacketHeldItemChange(serverSlot = mc.thePlayer.inventory.currentItem));
                        swapped = false;
                    }

                    Arsenic.getInstance().getModuleManager().getModuleByClass(KillAura.class).attack(true);
                    Arsenic.getInstance().getModuleManager().getModuleByClass(KillAura.class).switchTargets = true;

                    mc.getNetHandler().addToSendQueue(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
                    block = true;

                    Arsenic.getInstance().getModuleManager().getModuleByClass(Blink.class).setEnabled(false);
                    blink = false;

                    blinkAB = true;
                }
            }
            if (blockMode.getValue() == bMode.VANILLA) {
                PacketUtil.send(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
            }
        } else {
            unblock();
        }
    };

    public boolean isHoldingSword() {
        return mc.thePlayer.getHeldItem() != null && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword;
    }

    private void unblock() {
        if (block) {
            block = false;
            mc.getNetHandler().addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
        }

        if (blink) {
            Arsenic.getInstance().getModuleManager().getModuleByClass(Blink.class).setEnabled(false);
            blink = false;
        }

        if (swapped) {
            if (serverSlot != mc.thePlayer.inventory.currentItem) {
                mc.thePlayer.sendQueue.addToSendQueue(new C09PacketHeldItemChange(serverSlot = mc.thePlayer.inventory.currentItem));
                swapped = false;
            }
        }
        renderBlock = false;
    }

    public enum bMode {
        VANILLA,
        HYPIXEL,
    }
}
