import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.render.FirstPersonRender;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.util.MathHelper;

@ModuleInfo(name = "SwingAnimation", category = ModuleCategory.RENDER, tier = ModuleTier.VISUAL,
        description = "Custom first-person swing animation and speed")
public class SwingAnimation extends Module implements FirstPersonRender.SwingHandler {

    public enum Style {
        Vanilla,
        Smooth,
        Slide,
        Spin,
        Punch,
        Tap,
        Exhibition,
        Sigma
    }

    public final EnumProperty<Style> style = new EnumProperty<>("Style", Style.Smooth);
    public final DoubleProperty speed = new DoubleProperty("Speed", new DoubleValue(0.2, 3.0, 1.0, 0.05));

    public SwingAnimation() {
        FirstPersonRender.setSwingHandler(this);
    }

    @Override
    protected void onEnable() {
        FirstPersonRender.setSwingHandler(this);
    }

    @Override
    public boolean isActive() {
        return isEnabled();
    }

    @Override
    public void applyTransform(float equipProgress, float swingProgress) {
        float sin1 = MathHelper.sin(swingProgress * (float) Math.PI);
        float sinSqrt = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float) Math.PI);
        float sin2 = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);

        switch (style.getValue()) {
            case Vanilla:
                vanillaTransform(equipProgress, swingProgress);
                break;

            case Smooth:
                GlStateManager.translate(0.56F, -0.52F - equipProgress * 0.6F, -0.71999997F);
                GlStateManager.rotate(45.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotate(sinSqrt * -35.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotate(sin1 * -55.0F, 1.0F, 0.0F, 0.0F);
                GlStateManager.scale(0.4F, 0.4F, 0.4F);
                break;

            case Slide:
                GlStateManager.translate(0.56F, -0.52F - equipProgress * 0.6F, -0.71999997F);
                GlStateManager.translate(0.0F, sinSqrt * 0.15F, -sinSqrt * 0.25F);
                GlStateManager.rotate(45.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotate(sinSqrt * -65.0F, 1.0F, 0.0F, 0.0F);
                GlStateManager.scale(0.4F, 0.4F, 0.4F);
                break;

            case Spin:
                GlStateManager.translate(0.56F, -0.52F - equipProgress * 0.6F, -0.71999997F);
                GlStateManager.rotate(45.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotate(-swingProgress * 360.0F, 1.0F, 0.0F, 0.0F);
                GlStateManager.scale(0.4F, 0.4F, 0.4F);
                break;

            case Punch:
                GlStateManager.translate(0.56F, -0.52F - equipProgress * 0.6F, -0.71999997F);
                GlStateManager.translate(0.0F, 0.0F, -sin1 * 0.45F);
                GlStateManager.rotate(45.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotate(sin1 * -25.0F, 1.0F, 0.0F, 0.0F);
                GlStateManager.scale(0.4F, 0.4F, 0.4F);
                break;

            case Tap:
                GlStateManager.translate(0.56F, -0.52F - equipProgress * 0.6F, -0.71999997F);
                GlStateManager.rotate(45.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotate(sinSqrt * -22.0F, 1.0F, 0.0F, 0.0F);
                GlStateManager.rotate(sinSqrt * -10.0F, 0.0F, 0.0F, 1.0F);
                GlStateManager.scale(0.4F, 0.4F, 0.4F);
                break;

            case Exhibition:
                GlStateManager.translate(0.56F, -0.52F - equipProgress * 0.6F, -0.71999997F);
                GlStateManager.rotate(45.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotate(sin2 * -20.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotate(sinSqrt * -20.0F, 0.0F, 0.0F, 1.0F);
                GlStateManager.rotate(sinSqrt * -80.0F, 1.0F, 0.0F, 0.0F);
                GlStateManager.rotate(-20.0F, 1.0F, 0.0F, 0.0F);
                GlStateManager.scale(0.4F, 0.4F, 0.4F);
                break;

            case Sigma:
                GlStateManager.translate(0.56F, -0.52F - equipProgress * 0.6F, -0.71999997F);
                GlStateManager.translate(-sin1 * 0.2F, sin1 * 0.1F, 0.0F);
                GlStateManager.rotate(45.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotate(sin2 * -35.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotate(sinSqrt * -60.0F, 0.0F, 0.0F, 1.0F);
                GlStateManager.rotate(sinSqrt * -40.0F, 1.0F, 0.0F, 0.0F);
                GlStateManager.scale(0.4F, 0.4F, 0.4F);
                break;
        }
    }

    private static void vanillaTransform(float equipProgress, float swingProgress) {
        GlStateManager.translate(0.56F, -0.52F, -0.71999997F);
        GlStateManager.translate(0.0F, equipProgress * -0.6F, 0.0F);
        GlStateManager.rotate(45.0F, 0.0F, 1.0F, 0.0F);
        float f = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        float f1 = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float) Math.PI);
        GlStateManager.rotate(f * -20.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(f1 * -20.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(f1 * -80.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(0.4F, 0.4F, 0.4F);
    }

    @Override
    public int swingDuration(EntityLivingBase self) {
        double mul = speed.getValue().getInput();
        int base = self.isPotionActive(Potion.digSpeed)
                ? 6 - (1 + self.getActivePotionEffect(Potion.digSpeed).getAmplifier())
                : (self.isPotionActive(Potion.digSlowdown)
                        ? 6 + (1 + self.getActivePotionEffect(Potion.digSlowdown).getAmplifier()) * 2
                        : 6);
        return Math.max(1, (int) Math.round(base / mul));
    }
}
