package arsenic.module.impl.misc.chargetp;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMove;
import arsenic.event.impl.EventPacket;
import arsenic.event.types.CancellableEvent;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.Packet;
import net.minecraft.stats.StatFileWriter;
import net.minecraft.util.MovementInput;

import java.util.ArrayList;
import java.util.List;


@ModuleInfo(name = "ChargeTp", category = ModuleCategory.MOVEMENT)
public class ChargeTp extends Module {

    private CustomNetworkHandler customNetworkManager = new CustomNetworkHandler(EnumPacketDirection.SERVERBOUND);

    private NetHandlerPlayClient customNetHandler = null;

    private MovementInput customMovementInput = new MovementInput() {
        public void updatePlayerMoveState() {
            moveForward = 1;
        }
    };

    private EntityPlayerSP customPlayer = null;

    private final List<Packet<?>> packets = new ArrayList<>();

    public void addToPacketList(Packet<?> p) {
        packets.add(p);
    }

    @Override
    protected void postApplyConfig() {
        onDisable();
    }

    @Override
    protected void onEnable() {
        packets.clear();
        customNetHandler = new NetHandlerPlayClient(mc, mc.currentScreen, customNetworkManager, mc.thePlayer.getGameProfile());
        customNetworkManager.setNetHandler(customNetHandler);
        customPlayer = new EntityPlayerSP(mc, mc.thePlayer.worldObj, customNetHandler, new StatFileWriter()) {
            @Override
            protected boolean isCurrentViewEntity() {
                return true;
            }
        };
        customPlayer.movementInput = customMovementInput;

        mc.theWorld.addEntityToWorld(9999, customPlayer);
        customPlayer.setPositionAndRotation(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, mc.thePlayer.rotationYaw, 0);
    }

    @EventLink
    public final Listener<EventPacket.OutGoing> eventPacket = event -> {
        if(mc.theWorld == null)
            return;
        event.cancel();
    };

    @EventLink
    public final Listener<EventMove> eventMove = event -> {
        event.setForward(0);
        event.setStrafe(0);
    };

    @Override
    protected void onDisable() {
        if(customPlayer == null)
            return;
        mc.thePlayer.setPosition(customPlayer.posX, customPlayer.posY, customPlayer.posZ);
        mc.theWorld.removeEntity(customPlayer);
        packets.forEach(packet -> mc.getNetHandler().addToSendQueue(packet));
    }
}
