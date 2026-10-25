import arsenic.gui.themes.ThemeManager;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.ShowWhen;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.render.FirstPersonRender;
import arsenic.utils.render.GlowRenderer;
import net.minecraft.client.renderer.GlStateManager;

@ModuleInfo(name = "ViewModel", category = ModuleCategory.RENDER, tier = ModuleTier.VISUAL,
        description = "Offsets, scales and glows the held item in first person")
public class ViewModel extends Module implements FirstPersonRender.ViewModelHandler {

    public enum GlowMode {
        None,
        Glow,
        Cham
    }

    public final DoubleProperty posX = new DoubleProperty("X", new DoubleValue(-2.0, 2.0, 0.0, 0.05));
    public final DoubleProperty posY = new DoubleProperty("Y", new DoubleValue(-2.0, 2.0, 0.0, 0.05));
    public final DoubleProperty posZ = new DoubleProperty("Z", new DoubleValue(-2.0, 2.0, 0.0, 0.05));
    public final DoubleProperty scale = new DoubleProperty("Scale", new DoubleValue(0.1, 2.0, 1.0, 0.05));
    public final EnumProperty<GlowMode> glow = new EnumProperty<>("Glow", GlowMode.None);
    @ShowWhen(property = "Glow", is = {"Glow", "Cham"})
    public final DoubleProperty glowRadius = new DoubleProperty("Glow Radius", new DoubleValue(1, 20, 8, 1));
    @ShowWhen(property = "Glow", is = {"Glow", "Cham"})
    public final DoubleProperty glowStrength = new DoubleProperty("Glow Strength", new DoubleValue(0.5, 5.0, 2.5, 0.1));

    private final GlowRenderer glowRenderer = new GlowRenderer();

    public ViewModel() {
        FirstPersonRender.setViewModelHandler(this);
    }

    @Override
    protected void onEnable() {
        FirstPersonRender.setViewModelHandler(this);
    }

    @Override
    protected void onDisable() {
        glowRenderer.release();
    }

    @Override
    public boolean isActive() {
        return isEnabled();
    }

    @Override
    public void applyPosition() {
        GlStateManager.translate(
                posX.getValue().getInput(),
                posY.getValue().getInput(),
                posZ.getValue().getInput());
    }

    @Override
    public void applyScale() {
        double s = scale.getValue().getInput();
        GlStateManager.scale(s, s, s);
    }

    @Override
    public FirstPersonRender.GlowPass glowPass() {
        GlowMode m = glow.getValue();
        if (m == GlowMode.Glow)
            return FirstPersonRender.GlowPass.BEFORE;
        if (m == GlowMode.Cham)
            return FirstPersonRender.GlowPass.AFTER;
        return FirstPersonRender.GlowPass.NONE;
    }

    @Override
    public void renderHeldGlow(Runnable drawAction) {
        GlowMode mode = glow.getValue();
        if (mode == GlowMode.None)
            return;
        int radius = (int) glowRadius.getValue().getInput();
        float strength = (float) glowStrength.getValue().getInput();
        boolean outlineOnly = mode == GlowMode.Glow;
        float fill = outlineOnly ? 0f : 0.35f;
        glowRenderer.renderSilhouette(drawAction, ThemeManager.getMainColor(), radius, strength, outlineOnly, fill);
    }
}
