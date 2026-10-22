import arsenic.module.ModuleTier;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.EntityGlow;
import arsenic.utils.render.RenderUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.core.BlockPos;

/**
 * Highlights dropped items within a chosen range through walls, in the theme colour. Glow outlines each item itself, the
 * same as the player Glow mode; Box marks the block each item lies in.
 */
@ModuleInfo(name = "ItemESP", description = "Highlights dropped items through walls", category = ModuleCategory.RENDER, tier = ModuleTier.VISUAL)
public class ItemESP extends Module {

    public enum Mode {
        Glow,
        Box
    }

    public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.Glow);
    public final BooleanProperty render = new BooleanProperty("Render", true);
    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(8, 128, 48, 1));

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorld = event -> {
        if (!render.getValue()) return;
        int main = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
        double max = range.getValue().getInput();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ItemEntity) || mc.player.distanceTo(e) > max) continue;
            if (mode.getValue() == Mode.Glow) {
                EntityGlow.glow(e, main);
                continue;
            }
            BlockPos pos = e.blockPosition();
            RenderUtils.renderBlock(pos, ColorUtils.withAlpha(main, 70), false, true);
            RenderUtils.renderBlock(pos, ColorUtils.withAlpha(main, 220), true, false);
        }
    };
}
