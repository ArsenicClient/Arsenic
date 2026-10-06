package arsenic.module.impl.world;

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
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;

import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

@ModuleInfo(name = "Clutch", category = ModuleCategory.MOVEMENT, tier = arsenic.module.ModuleTier.BLATANT)
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
        if (mc.thePlayer == null || !(event.getPacket() instanceof S12PacketEntityVelocity))
            return;
        S12PacketEntityVelocity p = (S12PacketEntityVelocity) event.getPacket();
        if (p.getEntityID() == mc.thePlayer.getEntityId())
            velocities.add(new double[]{p.getMotionX() / 8000.0, p.getMotionY() / 8000.0, p.getMotionZ() / 8000.0});
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
        in.hasBlock = ScaffoldUtil.isUsable(mc.thePlayer.inventory.mainInventory[ScaffoldUtil.getBlockSlot()]);
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
        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        if (held == null || !(held.getItem() instanceof ItemBlock))
            return;
        ScaffoldCore.Input in = ScaffoldUtil.coreInput(false);
        ScaffoldCore.Action action = core.post(in, ScaffoldUtil.WORLD, event.getYaw(), event.getPitch());
        if (!action.place)
            return;
        mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, held,
                new BlockPos(action.x, action.y, action.z), EnumFacing.getFront(action.face),
                new Vec3(action.hitX, action.hitY, action.hitZ));
        mc.thePlayer.swingItem();
    };

    @RequiresPlayer
    @EventLink(Priorities.HIGH)
    public final Listener<EventMovementInput> movementInputListener = event -> {
        float[] keys = core.keys();
        if (keys == null || isScaffoldActive())
            return;
        float scale = mc.thePlayer.isSneaking() ? 0.3F : 1F;
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
        RenderUtils.renderBlockFace(pos, EnumFacing.getFront(target.face),
                Arsenic.getArsenic().getThemeManager().getCurrentTheme().getBlack(), true, true);
    };

    private boolean isScaffoldActive() {
        Scaffold scaffold = Arsenic.getArsenic().getModuleManager().getModuleByClass(Scaffold.class);
        return scaffold != null && scaffold.isEnabled();
    }

    private void keyBlock() {
        ItemStack current = mc.thePlayer.inventory.getCurrentItem();
        if (current == null || !(current.getItem() instanceof ItemBlock) || current.stackSize <= 1)
            mc.thePlayer.inventory.currentItem = ScaffoldUtil.getBlockSlot();
    }
}
