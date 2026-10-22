package arsenic.module.impl.visual;

import arsenic.module.ModuleTier;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventShader;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.utils.render.BlurBackdrop;
import arsenic.utils.render.QuadBatch;
import arsenic.utils.render.RenderContext;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import org.joml.Matrix3x2fStack;

/**
 * Blur and glow behind HUD elements.
 * <p>
 * The 1.8 client rendered bloom and blur masks into framebuffers and ran Kawase shaders over them.
 * Minecraft 26.x renders the GUI through its own deferred pipeline, so the two are rebuilt from it:
 * <ul>
 * <li>Blur: listeners of {@link EventShader.Blur} draw their shapes as usual, and those shapes are
 * filled with a blurred copy of the world behind them ({@link BlurBackdrop}), so panels frost
 * whatever is behind them exactly like before.</li>
 * <li>Bloom: listeners of {@link EventShader.Bloom} are drawn several times, faint and offset
 * around two rings, which reads as a soft halo.</li>
 * </ul>
 */
@ModuleInfo(name = "PostProcessing", category = ModuleCategory.GUI, hidden = true, tier = ModuleTier.QOL)
public class PostProcessing extends Module {

    private static final int BLUR_ITERATIONS = 2;
    private static final int BLUR_OFFSET = 1;
    private static final int BLOOM_ITERATIONS = 2;
    private static final int BLOOM_OFFSET = 1;
    /** Ring samples per glow pass. More is smoother and costs a redraw of every glowing element each. */
    private static final int SAMPLES = 8;

    /** Called from the HUD pass first: the frosted backgrounds of the panels. */
    public void renderBlur() {
        GpuTextureView blurred = BlurBackdrop.texture();
        if (blurred == null)
            return;
        int before = QuadBatch.sampledShapes();
        QuadBatch.sampleScreen(blurred);
        try {
            Arsenic.getInstance().getEventManager().post(new EventShader.Blur(BLUR_ITERATIONS, BLUR_OFFSET));
        } finally {
            QuadBatch.sampleScreen(null);
        }
        // the blurred copy is only made on frames where a panel was actually drawn
        if (QuadBatch.sampledShapes() != before)
            BlurBackdrop.request();
    }

    /** Called from the HUD pass, before the HUD itself is drawn. */
    public void renderGlow() {
        EventShader.Bloom bloom = new EventShader.Bloom(BLOOM_ITERATIONS, BLOOM_OFFSET);
        float radius = 1.5f + bloom.getIterations() * bloom.getOffset() * 0.75f;
        float previous = RenderContext.getAlpha();
        Matrix3x2fStack pose = RenderContext.graphics().pose();
        try {
            // an inner, brighter ring and an outer, fainter one: a falloff like the blurred mask had
            for (int ring = 1; ring <= 2; ring++) {
                float r = radius * ring / 2f;
                RenderContext.setAlpha(previous * (ring == 1 ? 0.10f : 0.06f));
                for (int i = 0; i < SAMPLES; i++) {
                    double angle = Math.PI * 2 * (i + ring * 0.5) / SAMPLES;
                    pose.pushMatrix();
                    pose.translate((float) Math.cos(angle) * r, (float) Math.sin(angle) * r);
                    Arsenic.getInstance().getEventManager().post(bloom);
                    pose.popMatrix();
                }
            }
        } finally {
            RenderContext.setAlpha(previous);
        }
    }
}
