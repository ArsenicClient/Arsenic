package arsenic.module.impl.world;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventKey;
import arsenic.event.impl.EventMove;
import arsenic.event.impl.EventSilentRotation;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;


@ModuleInfo(name = "scaffoldTest", category = ModuleCategory.WORLD)
public class ScaffoldTest extends Module {

    public BooleanProperty bool = new BooleanProperty("test", false);
    public BooleanProperty place = new BooleanProperty("Place", false);
    @EventLink
    public final Listener<EventMove> eventMoveListener = event -> {
        if(bool.getValue())
            return;
        event.setForward(-event.getForward());
        event.setStrafe(-event.getStrafe());
    };

    @EventLink
    public final Listener<EventSilentRotation> eventSilentRotationListener = event -> {
        //tower
        event.setYaw(MathHelper.wrapAngleTo180_float(event.getYaw() + 180f));
        if(mc.thePlayer.motionX == 0 && mc.thePlayer.motionZ == 0 && !PlayerUtils.playerOverAir()) {
            //float rotations = RotationUtils.getPlayerRotationsToBlock(PlayerUtils.playerOverAir());
            event.setPitch(90);
            return;
        }
        float pitchd = ((mc.thePlayer.rotationYaw % 90)/45) * 4f;
        if(pitchd > 4)
            pitchd = 8 - pitchd;

        event.setSpeed(180f);
        event.setPitch(83 - pitchd);

        if (place.getValue()) {
            BlockPos placePos = new BlockPos((int) Math.floor(mc.thePlayer.getPositionVector().xCoord),(int) Math.floor(mc.thePlayer.getPositionVector().yCoord) - 1,(int) Math.floor(mc.thePlayer.getPositionVector().zCoord));
            if (mc.thePlayer.getHeldItem() != null) {
                ItemStack heldItem = mc.thePlayer.getHeldItem();
                if (PlayerUtils.playerOverAir() && heldItem.getItem() instanceof ItemBlock) {
                    C08PacketPlayerBlockPlacement packet = new C08PacketPlayerBlockPlacement(
                            placePos, EnumFacing.UP.getIndex(), null, 0, 0, 0);
                    Minecraft.getMinecraft().getNetHandler().addToSendQueue(packet);
                }
            }
        }
    };
}
