package arsenic.module.impl.visual;

import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;

@ModuleInfo(name = "SwingAnimation", category = ModuleCategory.RENDER, hidden = true, tier = ModuleTier.VISUAL,
        description = "Custom first-person swing animations")
public class SwingAnimation extends Module {

    public enum Mode {
        Smooth, Self, Self2, Down, Forward, Touch, Pander, Curt, BlockHit
    }

    public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.Smooth);
    public final DoubleProperty power = new DoubleProperty("Strength", new DoubleValue(1, 10, 5, 1));
    public final DoubleProperty speed = new DoubleProperty("Speed", new DoubleValue(0, 10, 7, 1));
    public final DoubleProperty angle = new DoubleProperty("Angle", new DoubleValue(0, 360, 0, 1));

    /** Replaces ItemRenderer.transformFirstPersonItem for normal held items. */
    public void applyTransform(float equipProgress, float swingProgress) {
        float power = (float) this.power.getValue().getInput();
        float angle = (float) this.angle.getValue().getInput();
        float anim = MathHelper.sin(swingProgress * (float) Math.PI);
        float sin2 = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float) Math.PI);

        Mode mode = this.mode.getValue();
        if (mode != Mode.Pander)
            GlStateManager.translate(0f, equipProgress * -0.6f, 0f);

        switch (mode) {
            case Smooth: {
                GlStateManager.translate(0.56f, -0.52f, -0.72f);
                float f = power * 10f;
                float g = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
                GlStateManager.rotate(45f + g * (-f / 4f), 0f, 1f, 0f);
                float extra = MathHelper.sin(MathHelper.sqrt_float(swingProgress * swingProgress) * (float) Math.PI);
                GlStateManager.rotate(extra * -(f / 4f), 0f, 0f, 1f);
                GlStateManager.rotate(extra * -f, 1f, 0f, 0f);
                GlStateManager.rotate(-45f, 0f, 1f, 0f);
                break;
            }
            case Self: {
                GlStateManager.translate(0.56f, -0.52f, -0.72f);
                GlStateManager.rotate(90f, 0f, 1f, 0f);
                GlStateManager.rotate(-60f, 0f, 0f, 1f);
                GlStateManager.rotate(-angle - power * 10f * anim, 1f, 0f, 0f);
                break;
            }
            case Self2: {
                GlStateManager.translate(0.56f, -0.52f, -0.72f);
                GlStateManager.rotate(90f, 0f, 1f, 0f);
                GlStateManager.rotate(-30f, 0f, 0f, 1f);
                GlStateManager.rotate(-angle - power * 10f * anim, 1f, 0f, 0f);
                break;
            }
            case Down: {
                GlStateManager.translate(0.56f, -0.52f - anim * power / 24f, -0.72f);
                GlStateManager.rotate(90f, 0f, 1f, 0f);
                GlStateManager.rotate(-30f, 0f, 0f, 1f);
                GlStateManager.rotate(-90f, 1f, 0f, 0f);
                break;
            }
            case Forward: {
                GlStateManager.translate(0.56f, -0.52f, -0.72f);
                GlStateManager.translate(0f, 0f, -0.3f * sin2);
                GlStateManager.rotate(sin2 * -35f, 1f, 0f, 0f);
                GlStateManager.rotate(sin2 * 35f, 0f, 0f, 1f);
                break;
            }
            case Touch: {
                GlStateManager.translate(0.56f, -0.52f, -0.72f);
                GlStateManager.scale(1f, 1f, 1f + anim * power / 4f);
                GlStateManager.translate(0f, 0f, -0.265f);
                GlStateManager.rotate(-100f, 1f, 0f, 0f);
                break;
            }
            case Pander: {
                GlStateManager.translate(0.56f, -0.52f, -0.72f);
                GlStateManager.scale(0.8f, 0.8f, 0.8f);
                GlStateManager.translate(0.3f - anim * 0.15f, 0.2f - equipProgress * 0.12f,
                        -0.15f - anim * 0.13f);
                GlStateManager.rotate(76f - 10f * anim, 0f, 1f, 0f);
                GlStateManager.rotate(-16f - 8f * anim, 0f, 0f, 1f);
                GlStateManager.rotate(-83f - 26f * anim, 1f, 0f, 0f);
                break;
            }
            case Curt: {
                GlStateManager.translate(0.56f, -0.52f, -0.72f);
                float f = MathHelper.sqrt_float(swingProgress);
                float g = MathHelper.sin(f * (float) Math.PI);
                float extra = MathHelper.sin(swingProgress * (float) Math.PI);
                GlStateManager.translate(0.4f - g * 0.2f, -0.2f + g * 0.3f, -0.5f - extra * 0.2f);
                GlStateManager.rotate(91f, 0f, 1f, 0f);
                GlStateManager.rotate(-40f + g * -100f, 0f, 0f, 1f);
                GlStateManager.rotate(-60f, 1f, 0f, 0f);
                break;
            }
            case BlockHit: {
                GlStateManager.translate(0.56f, -0.52f, -0.72f);
                float f = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
                float g = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float) Math.PI);
                GlStateManager.rotate(45f, 0f, 1f, 0f);
                GlStateManager.rotate(f * -20f, 1f, 0f, 0f);
                GlStateManager.rotate(g * -20f, 0f, 0f, 1f);
                GlStateManager.rotate(g * -80f, 1f, 0f, 0f);
                GlStateManager.translate(0.4f, 0.2f, 0.2f);
                GlStateManager.translate(-0.5f, 0.08f, 0f);
                GlStateManager.rotate(20f, 0f, 1f, 0f);
                GlStateManager.rotate(-80f, 1f, 0f, 0f);
                GlStateManager.rotate(20f, 0f, 1f, 0f);
                break;
            }
        }
        GlStateManager.scale(0.4f, 0.4f, 0.4f);
    }

    /** LivingEntity.getArmSwingAnimationEnd HEAD_RETURN; -1 keeps vanilla. */
    public static int swingDuration(net.minecraft.entity.EntityLivingBase self) {
        if (!(self instanceof EntityPlayerSP))
            return -1;
        SwingAnimation animation = arsenic.main.Arsenic.getArsenic().getModuleManager()
                .getModuleByClass(SwingAnimation.class);
        if (animation == null || !animation.isEnabled())
            return -1;
        return Math.max(2, Math.round(12f - (float) animation.speed.getValue().getInput()));
    }
}
