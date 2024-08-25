package arsenic.module.impl.world;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.minecraft.MoveUtil;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.RenderUtils;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.BlockPos;
import org.lwjgl.input.Keyboard;
import arsenic.module.property.PropertyInfo;

@ModuleInfo(name = "SafeWalk", category = ModuleCategory.MOVEMENT)
public class SafeWalk extends Module {
    public final EnumProperty<sMode> mode = new EnumProperty<>("Mode: ", sMode.S_SHIFT);
    public final BooleanProperty onlySPressed = new BooleanProperty("Only S pressed", false);
    public final BooleanProperty onlySneak = new BooleanProperty("Only sneak", false);
    public final BooleanProperty pitchCheck = new BooleanProperty("Pitch Check", false);
    @PropertyInfo(reliesOn = "Pitch Check",value = "true")
    public final DoubleProperty pitch = new DoubleProperty("Pitch", new DoubleValue(0, 90, 45, 5));
    public final RangeProperty delay = new RangeProperty("Delay", new RangeValue(0, 500, 100, 200, 1));

    private long lastSneakTime = -1;

    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if(mode.getValue() == sMode.S_SHIFT) {
            if ((onlySPressed.getValue() && !mc.gameSettings.keyBindBack.isKeyDown())
                    || pitchCheck.getValue() && (mc.thePlayer.rotationPitch < pitch.getValue().getInput())
                    || (onlySneak.getValue() && !Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode()))
            ) {
                setShift(Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode()));
                return;
            }
            final long currentTime = System.currentTimeMillis();
            if (PlayerUtils.playerOverAir()) {
                setShift(true);
                lastSneakTime = currentTime;
            } else if (lastSneakTime != -1
                    && currentTime - lastSneakTime > Math.random() * (delay.getValue().getMax() - delay.getValue().getMin()) + delay.getValue().getMin()) {
                setShift(false);
                lastSneakTime = -1;
            }
        }
    };

    public boolean mixinResult(boolean flag) {
        if(flag)
            return true;
        return mc.thePlayer.onGround && mode.getValue() == sMode.NO_SHIFT;
    }
    @Override
    public void onDisable() {
        lastSneakTime = -1;
        setShift(false);
    }
    private void setShift(boolean sh) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), sh);
    }
    public enum sMode {
        S_SHIFT,
        NO_SHIFT,
    }
}
