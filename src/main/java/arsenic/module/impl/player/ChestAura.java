package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;

@ModuleInfo(name = "ChestAura", category = ModuleCategory.PLAYER)
public class ChestAura extends Module {

    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(1, 8, 5, 0.5));
    public final BooleanProperty chest = new BooleanProperty("Chest", true);
    public final BooleanProperty enderChest = new BooleanProperty("Ender Chest", true);

    private long lastOpenTime;

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.currentScreen != null) return;
        if (System.currentTimeMillis() - lastOpenTime < 200) return;

        double r = range.getValue().getInput();
        for (Object o : mc.theWorld.loadedTileEntityList) {
            TileEntity te = (TileEntity) o;
            if (!shouldOpen(te)) continue;

            BlockPos pos = te.getPos();
            double dist = mc.thePlayer.getDistance(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (dist > r) continue;

            mc.thePlayer.swingItem();
            mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(),
                    pos, EnumFacing.UP, new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            lastOpenTime = System.currentTimeMillis();
            break;
        }
    };

    private boolean shouldOpen(TileEntity te) {
        if (te instanceof TileEntityChest && chest.getValue()) return true;
        if (te instanceof TileEntityEnderChest && enderChest.getValue()) return true;
        return false;
    }

    @Override
    protected void onDisable() {
        lastOpenTime = 0;
    }
}
