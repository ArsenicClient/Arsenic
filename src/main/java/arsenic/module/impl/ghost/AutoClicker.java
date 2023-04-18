package arsenic.module.impl.ghost;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMouse;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.java.SoundUtils;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import org.lwjgl.Sys;
import org.lwjgl.input.Mouse;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ModuleInfo(name = "AutoClicker", category = ModuleCategory.GHOST)
public class AutoClicker extends Module {

    public final RangeProperty rangeProperty = new RangeProperty("Cps", new RangeValue(1, 20, 7, 9, 0.1d));
    public final BooleanProperty playSound = new BooleanProperty("Click Sound", true);
    public final EnumProperty<aMode> autoBlock = new EnumProperty<>("AutoBlock: ", aMode.NONE);

    private ExecutorService executor;


    @EventLink
    public final Listener<EventMouse.Down> keyEventDown = event -> {
        if(mc.currentScreen != null && event.button == 0) //lc only
            return;
        if(executor != null)
            executor.shutdownNow();
        executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            while(Mouse.isButtonDown(event.button)) {
                autoBlock.getValue().onMouseDown();
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), true);
                KeyBinding.onTick(mc.gameSettings.keyBindAttack.getKeyCode());
                if(playSound.getValue())
                    SoundUtils.playSound("click");

                sleep(genDownTime());
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
                autoBlock.getValue().onMouseUp();
                sleep(genDownTime());
            }
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        });
    };

    @EventLink
    public final Listener<EventMouse.Up> keyEventUp = event -> {
        if(executor != null)
            executor.shutdownNow();
    };

    @Override
    protected void onDisable() {
        if(executor != null)
            executor.shutdownNow();
    }

    private void sleep(int ms) {
        try {Thread.sleep(ms);} catch (InterruptedException ignored) {}
    }

    private int genDownTime() {
        return (int) (500/rangeProperty.getValue().getRandomInRange());
    }

    private int genUpTime() {
        return (int) (500/rangeProperty.getValue().getRandomInRange());
    }

    public enum aMode {
        NONE {

        },
        LEGIT {
            @Override
            void onMouseDown() {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
            }

            @Override
            void onMouseUp() {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
            }

            @Override
            void onFinish() {
                onMouseUp();
            }
        },
        Vanilla {
            @Override
            void onMouseDown() {
                mc.getNetHandler().addToSendQueue(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
            }

            @Override
            void onFinish() {
                mc.getNetHandler().addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
            }
        },

        ItemChange {
            @Override
            void onMouseDown() {
                Vanilla.onMouseDown();
                mc.thePlayer.sendQueue.addToSendQueue(new C09PacketHeldItemChange((mc.thePlayer.inventory.currentItem + 1) % 9));
                mc.thePlayer.sendQueue.addToSendQueue(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
            }

            @Override
            void onMouseUp() {
                Vanilla.onMouseUp();
            }

            @Override
            void onFinish() {
                Vanilla.onFinish();
            }
        };

        void onMouseDown() {

        }
        void onMouseUp() {

        }
        void onFinish() {

        }

    }

}
