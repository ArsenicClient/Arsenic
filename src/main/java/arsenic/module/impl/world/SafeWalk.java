package arsenic.module.impl.world;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMove;
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
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import org.lwjgl.input.Keyboard;
import arsenic.module.property.PropertyInfo;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;

@ModuleInfo(name = "SafeWalk", category = ModuleCategory.MOVEMENT)
public class SafeWalk extends Module {
    public final EnumProperty<sMode> mode = new EnumProperty<>("Mode: ", sMode.S_SHIFT);
    public final BooleanProperty onlySPressed = new BooleanProperty("Only S pressed", false);
    public final BooleanProperty onlySneak = new BooleanProperty("Only sneak", false);
    public final BooleanProperty pitchCheck = new BooleanProperty("Pitch Check", false);
    @PropertyInfo(reliesOn = "Pitch Check",value = "true")
    public final DoubleProperty pitch = new DoubleProperty("Pitch", new DoubleValue(0, 90, 45, 5));
    public final DoubleProperty precision = new DoubleProperty("Precision", new DoubleValue(0, 0.2, 0, 0.01));

    private long lastSneakTime = -1;

    private BlockPos lastOverBlock;

    @EventLink
    public final Listener<EventTick> tickEvent = tickEvent -> {
        if (mode.getValue() != sMode.S_SHIFT) return;

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

        BlockPos blockPos = PlayerUtils.getBlockUnderPlayer();
        if (!mc.theWorld.isAirBlock(blockPos)) {
            lastOverBlock = blockPos;
        }

        if (lastOverBlock == null) return;

        // Project position forward using motion
        double speed = Math.sqrt(mc.thePlayer.motionX * mc.thePlayer.motionX
                + mc.thePlayer.motionZ * mc.thePlayer.motionZ);

        // Look further ahead at higher speeds
        double lookahead = 1.0 + speed * 2.0;

        double predictedX = mc.thePlayer.posX + mc.thePlayer.motionX * lookahead;
        double predictedZ = mc.thePlayer.posZ + mc.thePlayer.motionZ * lookahead;

        double dist = Math.max(
                Math.abs(predictedX - lastOverBlock.getX() - 0.5),
                Math.abs(predictedZ - lastOverBlock.getZ() - 0.5)
        );

        setShift(dist > 0.78 - precision.getValue().getInput());
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
