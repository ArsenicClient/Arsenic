package arsenic.module.impl.ghost;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventDisplayGuiScreen;
import arsenic.event.impl.EventMouse;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.functionalinterfaces.IVoidFunction;
import arsenic.utils.java.SoundUtils;
import arsenic.utils.timer.Timer;
import net.minecraft.client.settings.KeyBinding;

import java.util.concurrent.ExecutorService;

@ModuleInfo(name = "AutoClicker", category = ModuleCategory.GHOST)
public class AutoClicker extends Module {

    public final RangeProperty rangeProperty = new RangeProperty("Cps", new RangeValue(1, 20, 7, 9, 0.1d));
    public final BooleanProperty playSound = new BooleanProperty("Click Sound", true);

    private boolean mouseDown;
    private Timer timer = new Timer();
    private ExecutorService executor;
    private IVoidFunction nextAction;

    private IVoidFunction down =  () -> {
        KeyBinding.setKeyBindState(getKeyBindNeeded(), true);
        KeyBinding.onTick(getKeyBindNeeded());
        if(playSound.getValue())
            SoundUtils.playSound("click");
        nextAction = getUp();
        timer.setCooldown(genUpTime());
    };
    private IVoidFunction up = () -> {
        KeyBinding.setKeyBindState(getKeyBindNeeded(), false);
        nextAction = down;
        timer.setCooldown(genDownTime());
    };

    //bypass forward referance aahhhh
    private IVoidFunction getUp() {
        return up;
    }

    private int getKeyBindNeeded() { return mc.gameSettings.keyBindAttack.getKeyCode();}

    @EventLink
    public final Listener<EventMouse.Down> downEvent = event -> {
        if(mc.currentScreen != null || event.button != 0)
            return;
        mouseDown = true;
        nextAction = up;
        timer.start();
    };

    @EventLink
    public final Listener<EventDisplayGuiScreen> eventDisplayGuiScreenListener = event -> mouseDown = false;

    @EventLink
    public final Listener<EventMouse.Up> upEvent = event -> {
        if(event.button != 0)
            return;
        mouseDown = false;
    };

    @EventLink
    public final Listener<EventTick> tickEvent = eventTick -> {
        if(mc.currentScreen != null || !mouseDown)
            return;

        if(timer.firstFinish()) {
            nextAction.voidFunction();
            timer.start();
        }
    };

    @Override
    protected void onDisable() {
       mouseDown = false;
    }

    private int genDownTime() {
        return (int) (500/rangeProperty.getValue().getRandomInRange());
    }

    private int genUpTime() {
        return (int) (500/rangeProperty.getValue().getRandomInRange());
    }
}
