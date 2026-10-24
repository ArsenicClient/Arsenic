package arsenic.module.impl.visual;

import arsenic.gui.themes.ThemeManager;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.render.GlowRenderer;
import net.minecraft.client.renderer.GlStateManager;

@ModuleInfo(name = "ViewModel", category = ModuleCategory.RENDER, hidden = true, tier = ModuleTier.VISUAL,
        description = "First-person hand position, size and held-item glow")
public class ViewModel extends Module {

    public enum GlowMode {
        Off, Glow, Cham
    }

    public final DoubleProperty x = new DoubleProperty("X", new DoubleValue(-1, 1, 0, 0.1));
    public final DoubleProperty y = new DoubleProperty("Y", new DoubleValue(-1, 1, 0, 0.1));
    public final DoubleProperty z = new DoubleProperty("Z", new DoubleValue(-1, 1, 0, 0.1));
    public final DoubleProperty size = new DoubleProperty("Size", new DoubleValue(0.5, 1.5, 1, 0.05));
    public final EnumProperty<GlowMode> glowMode = new EnumProperty<>("Glow", GlowMode.Off);
    public final DoubleProperty glowRadius = new DoubleProperty("Glow radius", new DoubleValue(2, 16, 7, 1));
    public final DoubleProperty glowStrength = new DoubleProperty("Glow strength", new DoubleValue(1, 6, 3, 0.5));

    private final GlowRenderer glowRenderer = new GlowRenderer();

    public void applyPosition() {
        GlStateManager.translate(
                (float) x.getValue().getInput(),
                (float) y.getValue().getInput(),
                (float) z.getValue().getInput());
    }

    public void applyScale() {
        float scale = (float) size.getValue().getInput();
        GlStateManager.scale(scale, scale, scale);
    }

    public GlowMode glowMode() {
        return glowMode.getValue();
    }

    public void renderHeldGlow(Runnable render) {
        GlowMode mode = glowMode();
        if (mode == GlowMode.Off)
            return;
        glowRenderer.renderSilhouette(render, ThemeManager.getMainColor(),
                (int) glowRadius.getValue().getInput(),
                (float) glowStrength.getValue().getInput(),
                mode == GlowMode.Glow,
                mode == GlowMode.Cham ? 0.38f : 0f);
    }

    @Override
    protected void onDisable() {
        glowRenderer.release();
    }
}
