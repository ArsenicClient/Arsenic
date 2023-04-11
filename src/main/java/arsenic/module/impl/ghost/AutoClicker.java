package arsenic.module.impl.ghost;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMouse;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.java.SoundUtils;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.Sys;
import org.lwjgl.input.Mouse;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ModuleInfo(name = "AutoClicker", category = ModuleCategory.GHOST)
public class AutoClicker extends Module {

    public final RangeProperty rangeProperty = new RangeProperty("Cps", new RangeValue(1, 20, 7, 9, 0.1d));

    private ExecutorService executor;


    @EventLink
    public final Listener<EventMouse.Down> keyEventDown = event -> {
        if(mc.currentScreen != null)
            return;
        if(executor != null)
            executor.shutdownNow();
        executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            while(Mouse.isButtonDown(event.button)) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), true);
                KeyBinding.onTick(mc.gameSettings.keyBindAttack.getKeyCode());
                SoundUtils.playSound("click");d
                sleep(genDownTime());
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
                sleep(genUpTime());
            }
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

}
