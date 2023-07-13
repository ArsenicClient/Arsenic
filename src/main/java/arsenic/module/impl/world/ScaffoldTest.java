package arsenic.module.impl.world;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMove;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;


@ModuleInfo(name = "Scaffold", category = ModuleCategory.WORLD)
public class ScaffoldTest extends Module {

    public BooleanProperty funny = new BooleanProperty("Invert Move Keys", false);
    public static BooleanProperty sprint = new BooleanProperty("Sprint", false);

    protected void onEnable(){
        if (!sprint.getValue()){
            mc.thePlayer.setSprinting(false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(),false);
        }
    }

    @EventLink
    public final Listener<EventTick> eventTickListener = event -> {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(),PlayerUtils.isPlayerHoldingBlocks());
        if (!PlayerUtils.isPlayerHoldingBlocks()) {
            for (int slot = 0; slot <= 8; slot++) {
                ItemStack itemInSlot = mc.thePlayer.inventory.getStackInSlot(slot);
                if (itemInSlot != null && itemInSlot.getItem() instanceof ItemBlock && !itemInSlot.getItem().getRegistryName().equalsIgnoreCase("minecraft:tnt") && (((ItemBlock) itemInSlot.getItem()).getBlock().isFullBlock() || ((ItemBlock) itemInSlot.getItem()).getBlock().isFullCube())) {
                    if (mc.thePlayer.inventory.currentItem != slot) {
                        mc.thePlayer.inventory.currentItem = slot;
                    }
                }
            }
        }
        setShift(PlayerUtils.playerOverAir()
                && mc.thePlayer.onGround
                || PlayerUtils.playerOverAir()); //shift on jump
    };
    @EventLink
    public final Listener<EventMove> eventMoveListener = event -> {
        if(!funny.getValue())
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

        float pitchd = ((mc.thePlayer.rotationYaw % 90) / 45) * 4f;
        if (pitchd > 4) {
            pitchd = 8 - pitchd;
        }
        //Fix for negative values
        if (pitchd < 0){ //horrid code imo but atleast it works :p
            pitchd = Math.abs(pitchd);
            pitchd = 8 - pitchd;
            if (Math.abs(pitchd) > 5){
                pitchd = 0;
            }
        }

        event.setSpeed(180f);
        event.setPitch(82 - pitchd);
    };
    protected void onDisable(){
        setShift(false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(),false);
    }
    private void setShift(boolean sh) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), sh);
    }
}
