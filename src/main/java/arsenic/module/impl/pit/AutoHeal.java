package arsenic.module.impl.pit;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.DisplayMode;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleProperty.DoubleProperty;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.utils.timer.Timer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemSkull;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C09PacketHeldItemChange;

@ModuleInfo(name = "AutoHeal", category = ModuleCategory.PIT)
public class AutoHeal extends Module {

	/*
    private int originalSlot;
    private final Timer timer = new Timer();
    private final RangeProperty actionTime = new RangeProperty("Action time", 0, 300, 80, 120, 1, DisplayMode.MILLIS);
    private final RangeProperty cooldownTime = new RangeProperty("Cooldown", 0, 5000, 1000, 1200, 1, DisplayMode.MILLIS);
    private final DoubleProperty health = new DoubleProperty("Health", 15.0, 0.0, 20.0, 0.1, DisplayMode.NORMAL);
    private final EnumProperty<SwitchMode> switchMode = new EnumProperty<>("Swap Mode", SwitchMode.NORMAL);
    private SwitchingState state = SwitchingState.NONE;


    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if(mc.thePlayer != null && timer.hasFinished()) {
            state.func.voidFunction(this);
        }
    };

    private int getSlot() {
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack itemInSlot = mc.thePlayer.inventory.getStackInSlot(slot);
            if (itemInSlot != null && itemInSlot.getItem() instanceof ItemSkull
                            && (itemInSlot.getDisplayName().toLowerCase().contains("golden") && itemInSlot.getDisplayName().toLowerCase().contains("head"))) {
                return slot;
            }
        }
        return -1;
    }

    public enum SwitchingState {
        NONE(inst -> {
            if(mc.thePlayer.getHealth() > inst.health.getValue().getInput()  && inst.timer.hasFinished())
                return;
            int slot = inst.getSlot();
            if(slot == -1 ) return;
            inst.timer.setCooldown((long) inst.actionTime.getValue().getRandomInRange()/3);
            inst.state = inst.state.next();
        }), WAITINGTOSWITCH(inst -> {
            inst.timer.setCooldown((long) inst.actionTime.getValue().getRandomInRange()/3);
            inst.switchMode.getValue().switchTo.voidFunction(inst);
            inst.state = inst.state.next();
        }), SWITCHED(inst -> {
            KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
            inst.timer.setCooldown((long) inst.actionTime.getValue().getRandomInRange()/3);
            inst.state = inst.state.next();
        }), SWITCHEDANDCLICKED(inst -> {
            inst.switchMode.getValue().switchBack.voidFunction(inst);
            inst.timer.setCooldown((long) inst.cooldownTime.getValue().getRandomInRange());
            inst.state = inst.state.next();
        });

        private final InstanceInterface func;

        SwitchingState(InstanceInterface func) {
            this.func = func;
        }

        private static final SwitchingState[] vals = values();
        public SwitchingState next() {
            return vals[(this.ordinal()+1) % vals.length];
        }
    }

    private enum SwitchMode {
        NORMAL(inst -> {
            inst.originalSlot = mc.thePlayer.inventory.currentItem;
            mc.thePlayer.inventory.currentItem = inst.getSlot();
        }, inst -> {
            mc.thePlayer.inventory.currentItem = inst.originalSlot;
        }),Silent(inst -> {
            mc.thePlayer.sendQueue.addToSendQueue(new C09PacketHeldItemChange(inst.getSlot()));
        }, inst -> {
            mc.thePlayer.sendQueue.addToSendQueue(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
        });

        private final InstanceInterface switchTo, switchBack;

        SwitchMode(InstanceInterface switchTo, InstanceInterface switchBack) {
            this.switchTo = switchTo;
            this.switchBack = switchBack;
        }
    }

    @FunctionalInterface public interface InstanceInterface {void voidFunction(AutoHeal instance);}
    */

}
