package arsenic.event;

import arsenic.event.impl.EventRenderWorldLast;
import arsenic.main.Arsenic;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class ForgeEvents {

    //this file makes me wanna cry

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Arsenic.getArsenic().getEventManager().getBus().post(new EventRenderWorldLast(event.context, event.partialTicks));
    }
}