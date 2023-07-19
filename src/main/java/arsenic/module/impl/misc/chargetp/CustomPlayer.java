package arsenic.module.impl.misc.chargetp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.stats.StatFileWriter;

public class CustomPlayer extends EntityPlayerSP {

    private int ticks;

    public CustomPlayer(NetHandlerPlayClient netHandler) {
        super(Minecraft.getMinecraft(), Minecraft.getMinecraft().thePlayer.worldObj, netHandler, new StatFileWriter());
    }

    @Override
    protected boolean isCurrentViewEntity() {
        return true;
    }

    @Override
    public void onUpdate() {

    }

    public void update() {
        ticks++;
        super.onUpdate();
    }

    public int getTicks() {
        return ticks;
    }


}
