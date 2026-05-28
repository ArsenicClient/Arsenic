package arsenic.module.impl.world;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
import arsenic.injection.accessor.IMixinMovementInputFromOptions;
import arsenic.injection.mixin.MixinEntityPlayer;
import arsenic.injection.mixin.MixinEntityPlayerSP;
import arsenic.injection.mixin.MixinMovementInputFromOptions;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;
import arsenic.module.property.PropertyInfo;

import java.util.List;

import static arsenic.utils.minecraft.ScaffoldUtil.willFallNextTick;

@ModuleInfo(name = "SafeWalk", category = ModuleCategory.MOVEMENT)
public class SafeWalk extends Module {
    public final BooleanProperty onlySPressed = new BooleanProperty("Only S pressed", false);
    public final BooleanProperty onlySneak = new BooleanProperty("Only sneak", false);
    public final BooleanProperty pitchCheck = new BooleanProperty("Pitch Check", false);
    public final BooleanProperty inAir = new BooleanProperty("inAir", false);
    @PropertyInfo(reliesOn = "Pitch Check",value = "true")
    public final DoubleProperty pitch = new DoubleProperty("Pitch", new DoubleValue(0, 90, 45, 5));
    public final DoubleProperty precision = new DoubleProperty("Safety", new DoubleValue(1, 3, 0, 0.1));

    @RequiresPlayer
    @EventLink
    public final Listener<EventLiving> tickEvent = tickEvent -> {
        // Early exits
        if (onlySPressed.getValue() && !mc.gameSettings.keyBindBack.isKeyDown()) {
            setShift(Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode()));
            return;
        }
        if (pitchCheck.getValue() && mc.thePlayer.rotationPitch < pitch.getValue().getInput()) {
            setShift(false);
            return;
        }
        if (onlySneak.getValue() && !Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) {
            setShift(false);
            return;
        }
        if (!inAir.getValue() && !mc.thePlayer.onGround) {
            setShift(false);
            return;
        }

        setShift(willFallNextTick(precision.getValue().getInput()));
    };

    @Override
    public void onDisable() {
        setShift(false);
    }

    private void setShift(boolean sh) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), sh);
    }
}
