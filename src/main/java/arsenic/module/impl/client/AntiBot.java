package arsenic.module.impl.client;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.util.BlockPos;

import java.util.HashMap;


@ModuleInfo(name = "Antibot", category = ModuleCategory.OTHER)
public class AntiBot extends Module {

    public final BooleanProperty invis = new BooleanProperty("Invis", false);
    public final BooleanProperty tpmove = new BooleanProperty("tp", false);

    public final HashMap<EntityPlayer, BlockPos> map = new HashMap<>();


    @Override
    protected void onEnable() {
        map.clear();
    }

    //very very simple will improve later maybe
    public boolean isARealPlayer(EntityPlayer entityPlayer) {
        if(!isEnabled())
            return true;
        String name = entityPlayer.getDisplayName().getUnformattedText();
        if (name.contains("§") && name.contains("[NPC]"))
            return false;

        if(entityPlayer.getName().startsWith("§c")) //skywars bots
            return false;

        if (name.isEmpty() && entityPlayer.getName().isEmpty())
            return false;

        if(entityPlayer.isInvisible() && invis.getValue())
            return false;

        if(!map.containsKey(entityPlayer))
            return true;

        //mostly for the player above you
        return !(Math.abs(map.get(entityPlayer).distanceSq(entityPlayer.getPosition())) < 5);
    }



    @EventLink
    public final Listener<EventPacket.Incoming.Post> eventPacketListener = event -> {
        try {
            if (event.getPacket() instanceof S18PacketEntityTeleport) {
                Entity entity = mc.theWorld.getEntityByID(((S18PacketEntityTeleport) event.getPacket()).getEntityId());
                if (entity instanceof EntityPlayer)
                    map.put((EntityPlayer) entity, entity.getPosition());
            }
        } catch (Exception e) {
        }
    };

    /*

    @EventLink
    public final Listener<EventRender2D> eventRender2DListener = event -> {
        if(mc.currentScreen != null)
            return;
        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen(). getFontRenderer();
        if(fr == null)
            return;
        float yOffSet = 0;
        for(Map.Entry<EntityPlayer, BlockPos> entry: ((HashMap<EntityPlayer, BlockPos>) map.clone()).entrySet()) {
            fr.drawString(entry.getKey().getName() + " d: " + entry.getKey().getDistanceSq(entry.getValue()), 0, yOffSet, -1);
            yOffSet += fr.getHeight(entry.getKey().getName());
        }
    }; */
}
