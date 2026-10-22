package arsenic.utils.render;

import arsenic.main.Arsenic;
import arsenic.module.impl.visual.ESP;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Chams: other players' models drawn as flat, translucent colour that shows through walls.
 *
 * 1.8 did this with a polygon offset and GL colour state around the model render. 26.x records the
 * model draw with a render type, so the model of each target is submitted with a render type of our
 * own instead: the entity shader, unlit (emissive), sampling a plain white texture so the tint is
 * the whole colour, with the depth test off so walls do not hide it.
 */
public final class ChamsRenderer {

    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("arsenic", "chams_white");

    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("arsenic", "pipeline/chams"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .build();

    private static RenderType renderType;

    /** Render states of this frame's targets and their colour (ARGB), filled while entities are extracted. Weak: states are made per frame. */
    private static final Map<LivingEntityRenderState, Integer> TARGETS = Collections.synchronizedMap(new WeakHashMap<>());

    private ChamsRenderer() {
    }

    private static RenderType renderType() {
        if (renderType == null) {
            NativeImage white = new NativeImage(1, 1, false);
            white.setPixel(0, 0, 0xFFFFFFFF);
            Minecraft.getInstance().getTextureManager().register(WHITE, new DynamicTexture(() -> "Arsenic chams", white));
            renderType = RenderType.create("arsenic_chams", RenderSetup.builder(PIPELINE)
                    .withTexture("Sampler0", WHITE)
                    .useOverlay()
                    .sortOnUpload()
                    .createRenderSetup());
        }
        return renderType;
    }

    /** After an entity's render state is extracted: remember whether it is drawn as chams this frame. */
    public static void extracted(LivingEntity entity, LivingEntityRenderState state) {
        ESP esp = settings();
        if (esp != null && esp.isChamsEnabled() && entity instanceof Player player && esp.isTarget(player))
            TARGETS.put(state, (Math.round(esp.getChamsAlpha() * 255f) << 24) | (esp.getChamsColour(player) & 0xFFFFFF));
        else
            TARGETS.remove(state);
    }

    /** The render type for the model of {@code state}, or {@code original} when it is not a target. */
    public static RenderType renderType(LivingEntityRenderState state, RenderType original) {
        return original != null && TARGETS.containsKey(state) ? renderType() : original;
    }

    /** The model tint for {@code state}, or {@code original} when it is not a target. */
    public static int tint(LivingEntityRenderState state, int original) {
        Integer colour = TARGETS.get(state);
        return colour == null ? original : colour;
    }

    private static ESP settings() {
        try {
            ESP esp = Arsenic.getArsenic().getModuleManager().getModuleByClass(ESP.class);
            return esp != null && esp.isEnabled() ? esp : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
