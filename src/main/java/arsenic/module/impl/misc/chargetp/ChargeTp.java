package arsenic.module.impl.misc.chargetp;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMove;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventUpdate;
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

    private final CustomNetworkHandler customNetworkManager = new CustomNetworkHandler(EnumPacketDirection.SERVERBOUND);

    private final MovementInput customMovementInput = new MovementInput() {
        @Override
        public void updatePlayerMoveState() {
            moveForward = 1;
            jump = true;
        }
    };
    private int ticks;

    private final List<Packet<?>> packets = new ArrayList<>();

    public void addToPacketList(Packet<?> p) {
        packets.add(p);
    }

    @Override
    protected void postApplyConfig() {
        setEnabled(false);
    }

    @Override
    protected void onEnable() {
        ticks = 0;
        packets.clear();
    }

    @EventLink
    public final Listener<EventPacket.OutGoing> eventPacket = event -> {
        if(mc.theWorld == null)
            return;
        event.cancel();
    };

    @EventLink
    public final Listener<EventUpdate.Post> eventUpdate = event -> ticks++;

    @EventLink
    public final Listener<EventMove> eventMove = event -> {
        event.setForward(0);
        event.setStrafe(0);
    };

    @Override
    protected void onDisable() {
        NetHandlerPlayClient customNetHandler = new NetHandlerPlayClient(mc, mc.currentScreen, customNetworkManager, mc.thePlayer.getGameProfile());
        customNetworkManager.setNetHandler(customNetHandler);
        CustomPlayer customPlayer = new CustomPlayer(customNetHandler);
        customPlayer.movementInput = customMovementInput;

        mc.theWorld.addEntityToWorld(9999, customPlayer);
        customPlayer.setPositionAndRotation(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, mc.thePlayer.rotationYaw, 0);
        for(int i = 0; i < ticks; i++) {
            customPlayer.update();
        }


        mc.thePlayer.setPosition(customPlayer.posX, customPlayer.posY, customPlayer.posZ);
        mc.theWorld.removeEntity(customPlayer);
        packets.forEach(packet -> mc.getNetHandler().addToSendQueue(packet));
    }
}
