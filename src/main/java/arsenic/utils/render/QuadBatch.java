package arsenic.utils.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/**
 * Collects untextured, float-precision quads and submits them as a single GUI element.
 * <p>
 * {@link GuiGraphicsExtractor#fill} only takes integer corners, which is too coarse for rounded
 * corners and circles. The GUI pipeline itself draws arbitrary quads, so shapes are built here as
 * quads (a triangle is a quad with a repeated vertex) and handed to the render state directly.
 */
public final class QuadBatch {

    private float[] xs = new float[64];
    private float[] ys = new float[64];
    private int[] colors = new int[64];
    private int vertexCount;

    private float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
    private float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;

    public QuadBatch vertex(float x, float y, int color) {
        if (vertexCount == xs.length) {
            int size = xs.length * 2;
            xs = java.util.Arrays.copyOf(xs, size);
            ys = java.util.Arrays.copyOf(ys, size);
            colors = java.util.Arrays.copyOf(colors, size);
        }
        xs[vertexCount] = x;
        ys[vertexCount] = y;
        colors[vertexCount] = RenderContext.applyAlpha(color);
        vertexCount++;
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);
        return this;
    }

    /**
     * Any convex quad. The GUI pipeline culls back faces, so the corners are reordered if needed -
     * callers can list them in either direction.
     */
    public QuadBatch quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int color) {
        if (isBackFacing(x0, y0, x1, y1, x2, y2))
            return vertex(x0, y0, color).vertex(x3, y3, color).vertex(x2, y2, color).vertex(x1, y1, color);
        return vertex(x0, y0, color).vertex(x1, y1, color).vertex(x2, y2, color).vertex(x3, y3, color);
    }

    /** Front faces have a negative signed area in screen space (y down), matching vanilla fills. */
    private static boolean isBackFacing(float x0, float y0, float x1, float y1, float x2, float y2) {
        return (x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0) > 0;
    }

    public QuadBatch rect(float x0, float y0, float x1, float y1, int color) {
        if (x1 <= x0 || y1 <= y0)
            return this;
        return quad(x0, y0, x0, y1, x1, y1, x1, y0, color);
    }

    public QuadBatch triangle(float x0, float y0, float x1, float y1, float x2, float y2, int color) {
        if (isBackFacing(x0, y0, x1, y1, x2, y2))
            return vertex(x0, y0, color).vertex(x2, y2, color).vertex(x1, y1, color).vertex(x1, y1, color);
        return vertex(x0, y0, color).vertex(x1, y1, color).vertex(x2, y2, color).vertex(x2, y2, color);
    }

    public QuadBatch triangle(float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2, int c2) {
        if (isBackFacing(x0, y0, x1, y1, x2, y2))
            return vertex(x0, y0, c0).vertex(x2, y2, c2).vertex(x1, y1, c1).vertex(x1, y1, c1);
        return vertex(x0, y0, c0).vertex(x1, y1, c1).vertex(x2, y2, c2).vertex(x2, y2, c2);
    }

    public boolean isEmpty() {
        return vertexCount == 0;
    }

    public void submit() {
        submit(RenderContext.graphics());
    }

    public void submit(GuiGraphicsExtractor graphics) {
        if (vertexCount == 0)
            return;
        Matrix3x2f pose = new Matrix3x2f(graphics.pose());
        ScreenRectangle scissor = graphics.scissorStack.peek();
        int bx = (int) Math.floor(minX), by = (int) Math.floor(minY);
        ScreenRectangle bounds = new ScreenRectangle(bx, by,
                (int) Math.ceil(maxX) - bx + 1, (int) Math.ceil(maxY) - by + 1).transformMaxBounds(pose);
        if (scissor != null)
            bounds = scissor.intersection(bounds);
        graphics.guiRenderState.addGuiElement(new State(
                java.util.Arrays.copyOf(xs, vertexCount),
                java.util.Arrays.copyOf(ys, vertexCount),
                java.util.Arrays.copyOf(colors, vertexCount),
                pose, scissor, bounds));
    }

    private record State(float[] xs, float[] ys, int[] colors, Matrix3x2fc pose,
                         @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds)
            implements GuiElementRenderState {

        @Override
        public void buildVertices(VertexConsumer consumer) {
            for (int i = 0; i < xs.length; i++)
                consumer.addVertexWith2DPose(pose, xs[i], ys[i]).setColor(colors[i]);
        }

        @Override
        public RenderPipeline pipeline() {
            return RenderPipelines.GUI;
        }

        @Override
        public TextureSetup textureSetup() {
            return TextureSetup.noTexture();
        }
    }
}
