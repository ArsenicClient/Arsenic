package arsenic.module.impl.world;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.Priorities;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMovementInput;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.SliderScale;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.minecraft.ScaffoldUtil;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.scaffoldcore.ScaffoldCore;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;

import java.util.Random;

/**
 * Telly bridging: sprint-jump forward with normal rotations and only turn to the block (and place)
 * once the jump is well under way, then turn back before landing. Placement and aiming are the same
 * {@link ScaffoldCore} that {@link Scaffold} uses, with sneaking left out.
 */
@ModuleInfo(name = "TellyScaffold", category = ModuleCategory.MOVEMENT, tier = arsenic.module.ModuleTier.BLATANT)
public class TellyScaffold extends Module {

    public final RangeProperty rotationSpeed = new RangeProperty("Rotation Speed", new RangeValue(1, 360, 180, 360, 1), SliderScale.LOG);
    public final DoubleProperty startTick = new DoubleProperty("Start Tick", new DoubleValue(1, 8, 3, 1));
    public final BooleanProperty autoJump = new BooleanProperty("Auto Jump", true);
    public final BooleanProperty sprint = new BooleanProperty("Sprint", true);

    private final ScaffoldCore core = new ScaffoldCore(ScaffoldCore.Tuning.best(), new Random());
    private int airTicks;
    private boolean bridging;

    @Override
    protected void onEnable() {
        core.reset();
        airTicks = 0;
        bridging = false;
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        core.reset();
        bridging = false;
        super.onDisable();
    }

    private boolean scaffoldActive() {
        Scaffold scaffold = Arsenic.getArsenic().getModuleManager().getModuleByClass(Scaffold.class);
        return scaffold != null && scaffold.isEnabled();
    }

    private boolean movingForward() {
        return mc.gameSettings.keyBindForward.isKeyDown();
    }

    private ScaffoldCore.Input input(boolean hasBlock) {
        ScaffoldCore.Input in = ScaffoldUtil.coreInput(true);
        in.hasBlock = hasBlock;
        in.speedMin = rotationSpeed.getValue().getMin();
        in.speedMax = rotationSpeed.getValue().getMax();
        in.eagle = false;
        return in;
    }

    private boolean keyBlock() {
        if (!ScaffoldUtil.isUsable(mc.thePlayer.inventory.getCurrentItem()))
            mc.thePlayer.inventory.currentItem = ScaffoldUtil.getBlockSlot();
        return mc.thePlayer.inventory.getCurrentItem() != null
                && mc.thePlayer.inventory.getCurrentItem().getItem() instanceof ItemBlock;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        airTicks = mc.thePlayer.onGround ? 0 : airTicks + 1;
        if (sprint.getValue() && movingForward() && !mc.thePlayer.isSneaking())
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
    };

    @RequiresPlayer
    @EventLink(Priorities.HIGH)
    public final Listener<EventMovementInput> onMovementInput = event -> {
        if (scaffoldActive())
            return;
        if (autoJump.getValue() && mc.thePlayer.onGround && event.getSpeed() > 0 && !mc.thePlayer.isSneaking())
            event.setJump(true);
        float[] nudge = bridging ? core.nudge() : null;
        if (nudge == null || (event.getSpeed() == 0 && event.getStrafe() == 0))
            return;
        float scale = Math.max(Math.abs(event.getSpeed()), Math.abs(event.getStrafe()));
        event.setSpeed(nudge[0] * scale);
        event.setStrafe(nudge[1] * scale);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (scaffoldActive())
            return;
        bridging = !mc.thePlayer.onGround && airTicks >= (int) startTick.getValue().getInput();
        if (!bridging) {
            core.reset();
            // free look again: turn back to the camera quickly
            event.setSpeed((float) rotationSpeed.getValue().getMax());
            return;
        }
        ScaffoldCore.Rotation rotation = core.rotate(input(keyBlock()), ScaffoldUtil.WORLD);
        event.setSpeed(rotation.speed);
        event.setPreventDuplicateLook(rotation.preventDuplicateLook);
        event.setYaw(rotation.yaw);
        event.setPitch(rotation.pitch);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onRotationPost = event -> {
        if (!bridging || scaffoldActive())
            return;
        Item held = mc.thePlayer.inventory.getCurrentItem() == null ? null
                : mc.thePlayer.inventory.getCurrentItem().getItem();
        if (!(held instanceof ItemBlock))
            return;
        ScaffoldCore.Action action = core.post(input(true), ScaffoldUtil.WORLD, event.getYaw(), event.getPitch());
        if (!action.place)
            return;
        mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem(),
                new BlockPos(action.x, action.y, action.z), EnumFacing.getFront(action.face),
                new Vec3(action.hitX, action.hitY, action.hitZ));
        mc.thePlayer.swingItem();
    };

    @EventLink
    public final Listener<EventRenderWorldLast> renderWorldLast = event -> {
        ScaffoldCore.Target target = bridging ? core.target() : null;
        if (target == null)
            return;
        BlockPos pos = new BlockPos(target.x, target.y, target.z);
        RenderUtils.renderBlock(pos, Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor(), true, false);
        RenderUtils.renderBlockFace(pos, EnumFacing.getFront(target.face),
                Arsenic.getArsenic().getThemeManager().getCurrentTheme().getBlack(), true, true);
    };
}
