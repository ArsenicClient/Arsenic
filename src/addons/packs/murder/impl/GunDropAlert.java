import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.RenderUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Murder Mystery: when a bow lands on the ground (a dropped detective gun) it is announced in chat once and outlined on
 * its block until it is picked up or gone.
 */
@ModuleInfo(name = "GunDropAlert", description = "Announces a dropped bow (the detective's gun) and outlines it", category = ModuleCategory.RENDER)
public class GunDropAlert extends Module {

    public final BooleanProperty render = new BooleanProperty("Render", true);
    public final BooleanProperty chat = new BooleanProperty("Chat Alert", true);

    private final Set<Integer> known = new HashSet<>();
    private final Set<BlockPos> outlined = new HashSet<>();

    @Override
    protected void onEnable() {
        known.clear();
        outlined.clear();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        Set<Integer> present = new HashSet<>();
        outlined.clear();
        for (Entity e : mc.theWorld.loadedEntityList) {
            if (!(e instanceof EntityItem)) continue;
            ItemStack stack = ((EntityItem) e).getEntityItem();
            if (stack == null || stack.getItem() != Items.bow) continue;
            present.add(e.getEntityId());
            outlined.add(new BlockPos(e.posX, e.posY, e.posZ));
            if (known.add(e.getEntityId()) && chat.getValue()) {
                PlayerUtils.addWaterMarkedMessageToChat("A bow dropped (" + (int) mc.thePlayer.getDistanceToEntity(e) + "m away)");
            }
        }
        Iterator<Integer> it = known.iterator();
        while (it.hasNext()) if (!present.contains(it.next())) it.remove();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorld = event -> {
        if (!render.getValue() || outlined.isEmpty()) return;
        int main = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
        for (BlockPos p : outlined) {
            RenderUtils.renderBlock(p, ColorUtils.withAlpha(main, 90), false, true);
            RenderUtils.renderBlock(p, ColorUtils.withAlpha(main, 230), true, false);
        }
    };
}
