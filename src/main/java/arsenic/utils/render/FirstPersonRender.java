package arsenic.utils.render;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;

/**
 * Hooks for first-person hand and held-item rendering, used by addons such as ViewModel and SwingAnimation.
 */
public final class FirstPersonRender {

    public enum GlowPass {
        NONE,
        BEFORE,
        AFTER
    }

    public interface ViewModelHandler {
        boolean isActive();
        void applyPosition();
        void applyScale();
        GlowPass glowPass();
        void renderHeldGlow(Runnable drawHeldItem);
    }

    public interface SwingHandler {
        boolean isActive();
        void applyTransform(float equipProgress, float swingProgress);
        int swingDuration(EntityLivingBase entity);
    }

    private static volatile ViewModelHandler viewModelHandler;
    private static volatile SwingHandler swingHandler;

    private FirstPersonRender() {}

    public static void setViewModelHandler(ViewModelHandler handler) {
        viewModelHandler = handler;
    }

    public static ViewModelHandler getViewModelHandler() {
        return viewModelHandler;
    }

    public static void setSwingHandler(SwingHandler handler) {
        swingHandler = handler;
    }

    public static SwingHandler getSwingHandler() {
        return swingHandler;
    }

    /** EntityLivingBase.getArmSwingAnimationEnd HEAD_RETURN; -1 keeps vanilla. */
    public static int swingDuration(EntityLivingBase self) {
        if (self != Minecraft.getMinecraft().thePlayer)
            return -1;
        SwingHandler h = swingHandler;
        return h != null && h.isActive() ? h.swingDuration(self) : -1;
    }
}
