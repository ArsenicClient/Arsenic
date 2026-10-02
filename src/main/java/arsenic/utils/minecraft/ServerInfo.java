package arsenic.utils.minecraft;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventUpdate;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;

/**
 * provides information about player's state(s) on server side
 */
public class ServerInfo {

    private final Minecraft mc = Minecraft.getInstance();
    public int onGroundTicks,offGroundTicks;
    public float yaw,pitch;
    public boolean blocking,sprinting;

    @RequiresPlayer
    @EventLink
    public final Listener<EventUpdate.Pre> preListener = pre -> {
        pitch = pre.getPitch();
        yaw = pre.getYaw();
        if (mc.player.onGround()){
            onGroundTicks++;
            offGroundTicks = 0;
        } else {
            onGroundTicks = 0;
            offGroundTicks++;
        }
    };

    @EventLink
    public final Listener<EventPacket.OutGoing> outGoingListener = e -> {
        if (e.getPacket() instanceof ServerboundPlayerCommandPacket command) {
            if (command.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING) {
                sprinting = true;
            }
            if (command.getAction() == ServerboundPlayerCommandPacket.Action.STOP_SPRINTING) {
                sprinting = false;
            }
        }
        // "blocking" now means using a sword that can block (1.21.5+ blocks_attacks component)
        if (e.getPacket() instanceof ServerboundUseItemPacket && mc.player != null && PlayerUtils.isPlayerHoldingSword()) {
            blocking = true;
        }
        if (e.getPacket() instanceof ServerboundPlayerActionPacket action
                && action.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
            blocking = false;
        }
    };
}
