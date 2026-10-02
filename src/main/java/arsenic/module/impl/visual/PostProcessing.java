package arsenic.module.impl.visual;

import arsenic.event.impl.EventShader;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.utils.render.RenderContext;
import org.joml.Matrix3x2fStack;

/**
 * Glow behind HUD elements.
 * <p>
 * The 1.8 client rendered bloom and blur masks into framebuffers and ran Kawase shaders over them.
 * Minecraft 26.x renders through its own pipeline abstraction (with a Vulkan backend), so arbitrary
 * framebuffer passes from mods are not available. Bloom is approximated instead: listeners of
 * {@link EventShader.Bloom} are drawn several times, faint and offset around a ring, which reads as
 * a soft halo. Blur only has a vanilla equivalent behind whole screens, which the ClickGUI uses for
 * its backdrop; {@link EventShader.Blur} is no longer posted.
 */
@ModuleInfo(name = "PostProcessing", category = ModuleCategory.GUI, hidden = true)
public class PostProcessing extends Module {

    private static final int BLOOM_ITERATIONS = 2;
    private static final int BLOOM_OFFSET = 1;
    /** Ring samples per glow pass. More is smoother and costs a redraw of every glowing element each. */
    private static final int SAMPLES = 8;

    /** Called from the HUD pass, before the HUD itself is drawn. */
    public void renderGlow() {
        EventShader.Bloom bloom = new EventShader.Bloom(BLOOM_ITERATIONS, BLOOM_OFFSET);
        float radius = 1.5f + bloom.getIterations() * bloom.getOffset() * 0.75f;
        float previous = RenderContext.getAlpha();
        Matrix3x2fStack pose = RenderContext.graphics().pose();
        try {
            RenderContext.setAlpha(previous * 0.12f);
            for (int i = 0; i < SAMPLES; i++) {
                double angle = Math.PI * 2 * i / SAMPLES;
                pose.pushMatrix();
                pose.translate((float) Math.cos(angle) * radius, (float) Math.sin(angle) * radius);
                Arsenic.getInstance().getEventManager().post(bloom);
                pose.popMatrix();
            }
        } finally {
            RenderContext.setAlpha(previous);
        }
    }
}
