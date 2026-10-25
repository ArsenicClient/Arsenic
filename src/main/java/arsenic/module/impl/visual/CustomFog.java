package arsenic.module.impl.visual;

import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.runtime.Access;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

@ModuleInfo(name = "CustomFog", category = ModuleCategory.RENDER, hidden = true, tier = ModuleTier.VISUAL,
        description = "Changes the world fog colour and distance")
public class CustomFog extends Module {

    public final DoubleProperty distance = new DoubleProperty("Fog distance", new DoubleValue(10, 255, 80, 5));

    private static final Access.FieldRef FOG_RED = Access.field(EntityRenderer.class, "fogColorRed");
    private static final Access.FieldRef FOG_GREEN = Access.field(EntityRenderer.class, "fogColorGreen");
    private static final Access.FieldRef FOG_BLUE = Access.field(EntityRenderer.class, "fogColorBlue");
    private static final FloatBuffer FOG_COLOR = BufferUtils.createFloatBuffer(4);

    private static CustomFog active() {
        CustomFog fog = Arsenic.getArsenic().getModuleManager().getModuleByClass(CustomFog.class);
        return fog != null && fog.isEnabled() ? fog : null;
    }

    /** setupFog HEAD: make the fog colour match the active theme before vanilla writes its colour buffer. */
    public static void setupFogHead(EntityRenderer self, int pass, float partialTicks) {
        CustomFog fog = active();
        if (fog == null)
            return;
        int colour = ThemeManager.getMainColor();
        FOG_RED.set(self, ((colour >> 16) & 0xFF) / 255f);
        FOG_GREEN.set(self, ((colour >> 8) & 0xFF) / 255f);
        FOG_BLUE.set(self, (colour & 0xFF) / 255f);
    }

    /** setupFog RETURN: override vanilla's start/end while keeping the terrain fog mode. */
    public static void setupFogReturn(EntityRenderer self, int pass, float partialTicks) {
        CustomFog fog = active();
        if (fog == null)
            return;
        int colour = ThemeManager.getMainColor();
        FOG_COLOR.clear();
        FOG_COLOR.put(((colour >> 16) & 0xFF) / 255f)
                .put(((colour >> 8) & 0xFF) / 255f)
                .put((colour & 0xFF) / 255f)
                .put(1f);
        FOG_COLOR.flip();
        GL11.glFog(GL11.GL_FOG_COLOR, FOG_COLOR);

        float far = (float) fog.distance.getValue().getInput();
        GlStateManager.setFog(GL11.GL_LINEAR);
        GlStateManager.setFogStart(pass == -1 ? 0f : far * 0.55f);
        GlStateManager.setFogEnd(far);
    }
}
