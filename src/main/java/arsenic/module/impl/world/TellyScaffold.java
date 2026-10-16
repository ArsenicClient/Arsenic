package arsenic.module.impl.world;

import arsenic.module.ModuleTier;
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
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.SliderScale;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.minecraft.ScaffoldUtil;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.scaffoldcore.ScaffoldCore;
import arsenic.utils.scaffoldcore.TellyCore;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;

import java.util.Random;

/**
 * Telly bridging: sprint-jump forward with normal rotations and only turn to the block (and place) while airborne,
 * then turn back to sprint-jump again. {@link TellyCore} decides the turns; placement and aim are the same
 * {@link ScaffoldCore} that {@link Scaffold} uses, with sneaking left out.
 */
@ModuleInfo(name = "TellyScaffold", category = ModuleCategory.MOVEMENT, tier = ModuleTier.BLATANT)
public class TellyScaffold extends Module {

    public enum Rotations {
        /** Eased turns to 135 degrees off the camera, and back once the landing block is down. */
        Smooth,
        /** The original telly: turns at Rotation Speed, snapping back on landing. */
        Snap
    }

    public final EnumProperty<Rotations> rotations = new EnumProperty<>("Rotations", Rotations.Smooth);
    @PropertyInfo(reliesOn = "Rotations", value = "Smooth")
    public final DoubleProperty turnTicks = new DoubleProperty("Turn Ticks", new DoubleValue(3, 8, 5, 1));
    @PropertyInfo(reliesOn = "Rotations", value = "Smooth")
    public final DoubleProperty maxTurnStep = new DoubleProperty("Max Turn Step", new DoubleValue(30, 180, 70, 1));
    @PropertyInfo(reliesOn = "Rotations", value = "Smooth")
    public final DoubleProperty bridgeSpeed = new DoubleProperty("Bridge Speed", new DoubleValue(20, 180, 90, 1));
    @PropertyInfo(reliesOn = "Rotations", value = "Smooth")
    public final DoubleProperty sideAngle = new DoubleProperty("Side Angle", new DoubleValue(0, 60, 45, 1));
    @PropertyInfo(reliesOn = "Rotations", value = "Snap")
    public final RangeProperty rotationSpeed = new RangeProperty("Rotation Speed", new RangeValue(1, 360, 180, 360, 1), SliderScale.LOG);
    public final DoubleProperty startTick = new DoubleProperty("Start Tick", new DoubleValue(1, 8, 1, 1));
    public final BooleanProperty autoJump = new BooleanProperty("Auto Jump", true);
    public final BooleanProperty sprint = new BooleanProperty("Sprint", true);

    private final TellyCore smooth = new TellyCore(TellyCore.Tuning.best(), ScaffoldCore.Tuning.best(), new Random());
    private final TellyCore snap = new TellyCore(TellyCore.Tuning.legacy(), ScaffoldCore.Tuning.best(), new Random());
    private TellyCore core = smooth;
    private int airTicks;

    @Override
    protected void onEnable() {
        smooth.reset();
        snap.reset();
        airTicks = 0;
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        smooth.reset();
        snap.reset();
        super.onDisable();
    }

    private boolean scaffoldActive() {
        Scaffold scaffold = Arsenic.getArsenic().getModuleManager().getModuleByClass(Scaffold.class);
        return scaffold != null && scaffold.isEnabled();
    }

    private boolean movingForward() {
        return mc.gameSettings.keyBindForward.isKeyDown();
    }

    /** The core for the selected rotations, with the settings copied in; switching resets the one left. */
    private TellyCore core() {
        TellyCore selected = rotations.getValue() == Rotations.Smooth ? smooth : snap;
        if (selected != core) {
            core.reset();
            core = selected;
        }
        TellyCore.Tuning tun = core.tun;
        tun.startTick = (int) startTick.getValue().getInput();
        tun.outTicks = (float) turnTicks.getValue().getInput();
        tun.maxStep = (float) maxTurnStep.getValue().getInput();
        tun.bridgeSpeed = (float) bridgeSpeed.getValue().getInput();
        tun.sideAngle = (float) sideAngle.getValue().getInput();
        return core;
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
        float[] nudge = core.nudge();
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
        event.setBlockUserInput(true);
        // the blocks come out once airborne, where the bridging happens
        boolean hasBlock = mc.thePlayer.onGround
                ? ScaffoldUtil.isUsable(mc.thePlayer.inventory.getStackInSlot(ScaffoldUtil.getBlockSlot())) : keyBlock();
        TellyCore.Output out = core().rotate(input(hasBlock), ScaffoldUtil.WORLD, airTicks, mc.thePlayer.rotationPitch);
        event.setSpeed(out.speed);
        if (!out.modify)
            return;
        event.setSmoothing(out.smoothing);
        event.setPreventDuplicateLook(out.preventDuplicateLook);
        event.setYaw(out.yaw);
        event.setPitch(out.pitch);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onRotationPost = event -> {
        if (!core.bridging() || scaffoldActive())
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
        ScaffoldCore.Target target = core.target();
        if (target == null)
            return;
        BlockPos pos = new BlockPos(target.x, target.y, target.z);
        RenderUtils.renderBlock(pos, Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor(), true, false);
        RenderUtils.renderBlockFace(pos, EnumFacing.getFront(target.face),
                Arsenic.getArsenic().getThemeManager().getCurrentTheme().getBlack(), true, true);
    };
}
