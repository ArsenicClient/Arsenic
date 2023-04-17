package arsenic.module.impl.client;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventPlayerJoinWorld;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;

import java.util.HashMap;
import java.util.List;


@ModuleInfo(name = "Antibot", category = ModuleCategory.OTHER)
public class AntiBot extends Module {

    public final BooleanProperty invis = new BooleanProperty("Invis", false);
    public final BooleanProperty test = new BooleanProperty("Test", false);

    public final HashMap<EntityPlayer, BlockPos> map = new HashMap<>();

    @EventLink
    public final Listener<EventPlayerJoinWorld> eventEntityJoinWorldListener = event -> {
        map.put(event.getEntity(), event.getEntity().getPosition());
    };


    //very very simple
    public boolean isARealPlayer(EntityPlayer entityPlayer) {
        if(!isEnabled())
            return true;
        String name = entityPlayer.getDisplayName().getUnformattedText();
        if (name.contains("§") && name.contains("[NPC]"))
            return false;

        if (name.isEmpty() && entityPlayer.getName().isEmpty())
            return false;

        if(entityPlayer.isInvisible() && invis.getValue())
            return false;

        if(!map.containsKey(entityPlayer))
            return true;

        if(Math.abs(map.get(entityPlayer).distanceSq(entityPlayer.getPosition())) < 2) {
            return false;
        }

        return true;
    }


    @EventLink
    public final Listener<EventPacket.Incoming> eventPacketListener = event -> {
        if(event.getPacket() instanceof S18PacketEntityTeleport) {
            PlayerUtils.addWaterMarkedMessageToChat("s " + mc.theWorld.getEntityByID(((S18PacketEntityTeleport) event.getPacket()).getEntityId()).getName());
        }
    };
}
