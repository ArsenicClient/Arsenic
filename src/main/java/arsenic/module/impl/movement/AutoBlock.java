package arsenic.module.impl.movement;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.Priorities;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.timer.MSTimer;
import com.sun.org.apache.xpath.internal.operations.Bool;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import scala.sys.BooleanProp;

import static arsenic.utils.lag.LagManager.*;

@ModuleInfo(name = "AutoBlock", category = ModuleCategory.BLATANT, dev = true)
public class AutoBlock extends Module {

    public final DoubleProperty cps = new DoubleProperty("CPS", new DoubleValue(1, 10, 8, 0.5));
    public final BooleanProperty serverRender = new BooleanProperty("test", false);

    private boolean isBlocking = false;
    private boolean blinkReset = false;
    private final MSTimer blockTimer = new MSTimer();

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onLiving = event -> {
        if (!canBlock()) {
            stopBlocking();
            return;
        }
        ItemStack heldItem = mc.thePlayer.getHeldItem();
        MovingObjectPosition movingObjectPosition = event.getRayTraceEntity();
        long blockInterval = (long) (1000.0D / cps.getValue().getInput());
        if (!isBlocking) {
            startBlocking(heldItem, movingObjectPosition);
            blinkReset = true;
        } else if (blockTimer.hasTimeElapsed(blockInterval, true)) {
            acquire(this.getClass());
            stopBlocking();
        }
    };

    @RequiresPlayer
    @EventLink(value = Priorities.LOW)
    public final Listener<EventUpdate.Post> eventUpdatePost = event -> {
        if(blinkReset) {
            release(this.getClass());
            acquire(this.getClass());
            blinkReset = false;
        }
    };

    private void startBlocking(ItemStack itemStack, MovingObjectPosition movingObjectPosition) {
        if(movingObjectPosition.entityHit != null) {
            sendPacket(
                    new C02PacketUseEntity(
                            movingObjectPosition.entityHit,
                            new Vec3(
                                    movingObjectPosition.hitVec.xCoord - movingObjectPosition.entityHit.posX,
                                    movingObjectPosition.hitVec.yCoord - movingObjectPosition.entityHit.posY,
                                    movingObjectPosition.hitVec.zCoord - movingObjectPosition.entityHit.posZ)
                    )
            );
            sendPacket(new C02PacketUseEntity(movingObjectPosition.entityHit, C02PacketUseEntity.Action.INTERACT));
        }
        sendPacket(new C08PacketPlayerBlockPlacement(itemStack));
        mc.thePlayer.setItemInUse(itemStack, 72000);
        blockTimer.reset();
        isBlocking = true;
    }

    private void stopBlocking() {
        if (!isBlocking) {
            return;
        }

        sendPacket(new C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                BlockPos.ORIGIN,
                EnumFacing.DOWN
        ));
        mc.thePlayer.stopUsingItem();
        isBlocking = false;
    }


    private boolean canBlock() {
        if (mc.thePlayer == null || mc.thePlayer.getHeldItem() == null) {
            return false;
        }

        if (isUseKeyPressed()) {
            return false;
        }

        return mc.thePlayer.getHeldItem().getItem() instanceof ItemSword;
    }

    private boolean isUseKeyPressed() {
        int keyCode = mc.gameSettings.keyBindUseItem.getKeyCode();
        return keyCode < 0 ? Mouse.isButtonDown(keyCode + 100) : Keyboard.isKeyDown(keyCode);
    }

    public boolean isBlocked() {
        return isBlocking && serverRender.getValue();
    }

    @Override
    protected void onDisable() {
        stopBlocking();
        release(this.getClass());
    }

    @Override
    protected void onEnable() {
        isBlocking = false;
        blockTimer.reset();
    }
}