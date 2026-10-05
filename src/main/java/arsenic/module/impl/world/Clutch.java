package arsenic.module.impl.world;

import arsenic.utils.minecraft.PlayerUtils;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.Priorities;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMovementInput;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.SliderScale;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.ScaffoldUtil;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.scaffoldcore.ClutchCore;
import arsenic.utils.scaffoldcore.ScaffoldCore;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

@ModuleInfo(name = "Clutch", category = ModuleCategory.MOVEMENT)
public class Clutch extends Module {
    public final DoubleProperty rotationSpeed = new DoubleProperty("Rotation Speed", new DoubleValue(10, 360, 200, 1), SliderScale.LOG);

    private final ClutchCore core = new ClutchCore(ClutchCore.Tuning.best(), new Random());
    private final ConcurrentLinkedQueue<double[]> velocities = new ConcurrentLinkedQueue<>();

    @Override
    protected void onEnable() {
        core.reset();
        velocities.clear();
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        core.reset();
        velocities.clear();
        super.onDisable();
    }

    @EventLink
    public final Listener<EventPacket.Incoming.Pre> velocityListener = event -> {
        if (mc.player == null || !(event.getPacket() instanceof ClientboundSetEntityMotionPacket))
            return;
        ClientboundSetEntityMotionPacket p = (ClientboundSetEntityMotionPacket) event.getPacket();
        if (p.id() == mc.player.getId())
            velocities.add(new double[]{p.movement().x / 8000.0, p.movement().y / 8000.0, p.movement().z / 8000.0});
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> eventSilentRotationListener = event -> {
        double[] v;
        while ((v = velocities.poll()) != null)
            core.onVelocity(v[0], v[1], v[2]);
        if (isScaffoldActive()) {
            core.reset();
            return;
        }
        ScaffoldCore.Input in = ScaffoldUtil.coreInput(true);
        in.hasBlock = ScaffoldUtil.isUsable(mc.player.getInventory().getItem(ScaffoldUtil.getBlockSlot()));
        ClutchCore.Rotation rotation = core.rotate(in, ScaffoldUtil.WORLD, rotationSpeed.getValue().getInput());
        if (core.active() && in.hasBlock)
            keyBlock();
        if (!rotation.set)
            return;
        event.setSpeed(rotation.speed);
        event.setPreventDuplicateLook(rotation.preventDuplicateLook);
        event.setYaw(rotation.yaw);
        event.setPitch(rotation.pitch);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> eventSilentRotationPostListener = event -> {
        if (!core.active())
            return;
        ItemStack held = mc.player.getMainHandItem();
        if (held == null || !(held.getItem() instanceof BlockItem))
            return;
        ScaffoldCore.Input in = ScaffoldUtil.coreInput(false);
        ScaffoldCore.Action action = core.post(in, ScaffoldUtil.WORLD, event.getYaw(), event.getPitch());
        if (!action.place)
            return;
        arsenic.utils.minecraft.ScaffoldUtil.placeBlock(new BlockPos(action.x, action.y, action.z), Direction.from3DDataValue(action.face),
                new Vec3(action.hitX, action.hitY, action.hitZ));
        PlayerUtils.swingItem();
    };

    @RequiresPlayer
    @EventLink(Priorities.HIGH)
    public final Listener<EventMovementInput> movementInputListener = event -> {
        float[] keys = core.keys();
        if (keys == null || isScaffoldActive())
            return;
        float scale = mc.player.isShiftKeyDown() ? 0.3F : 1F;
        event.setSpeed(keys[0] * scale);
        event.setStrafe(keys[1] * scale);
    };

    @EventLink
    public final Listener<EventRenderWorldLast> renderWorldLast = event -> {
        ScaffoldCore.Target target = core.target();
        if (target == null)
            return;
        BlockPos pos = new BlockPos(target.x, target.y, target.z);
        RenderUtils.renderBlock(pos, Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor(), true, false);
        RenderUtils.renderBlockFace(pos, Direction.from3DDataValue(target.face),
                Arsenic.getArsenic().getThemeManager().getCurrentTheme().getBlack(), true, true);
    };

    private boolean isScaffoldActive() {
        Scaffold scaffold = Arsenic.getArsenic().getModuleManager().getModuleByClass(Scaffold.class);
        return scaffold != null && scaffold.isEnabled();
    }

    private void keyBlock() {
        ItemStack current = mc.player.getMainHandItem();
        if (current == null || !(current.getItem() instanceof BlockItem) || current.getCount() <= 1)
            mc.player.getInventory().setSelectedSlot(ScaffoldUtil.getBlockSlot());
    }
}
