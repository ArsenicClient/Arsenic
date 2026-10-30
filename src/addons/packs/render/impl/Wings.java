import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.injection.accessor.IMixinRenderManager;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.Group;
import arsenic.module.property.PropertyGroup;
import arsenic.module.property.ShowWhen;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.ColourProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.java.ColorUtils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Procedurally built cosmetic wings (bat, dragon, angel, phoenix, butterfly, fairy). Client side only. Submitted by polycraft. */
@ModuleInfo(name = "Wings", description = "Cosmetic wings (6 types)", category = ModuleCategory.RENDER, tier = ModuleTier.QOL)
public class Wings extends Module {

    public enum Style { Default, Theme, Rainbow, Custom }

    /** base, accent, bone, glow, amplitude, speed, rigid, opacity, scale, root height */
    public enum Type {
        Bat(0x9E9E9E, 0x9E9E9E, 0x1E1E1E, 0xFFFFFF, 1f, 1f, false, 1f, 0.95f, 1.30f),
        Dragon(0x6E1F1F, 0x1A0808, 0x150606, 0xFF6A3A, 1.25f, 0.7f, false, 1f, 0.95f, 1.32f),
        Angel(0xF4F4F4, 0xE6C66E, 0xBDBDBD, 0xFFF2C4, 1.1f, 0.75f, false, 1f, 0.85f, 1.34f),
        Phoenix(0xE8401C, 0xFFD43B, 0x7A1A08, 0xFF9A2E, 1.1f, 0.9f, false, 1f, 0.85f, 1.34f),
        Butterfly(0xFF8C1A, 0x141414, 0x141414, 0xFFD39A, 2.2f, 2.4f, true, 1f, 0.8f, 1.25f),
        Fairy(0x9FE8FF, 0xF7A8FF, 0xD8F6FF, 0xE6FBFF, 1.4f, 3.4f, true, 0.55f, 0.75f, 1.28f);

        final int base, accent, bone, glow;
        final float amp, speed, alpha, scale, rootY;
        final boolean rigid;

        Type(int base, int accent, int bone, int glow, float amp, float speed, boolean rigid, float alpha, float scale, float rootY) {
            this.base = base; this.accent = accent; this.bone = bone; this.glow = glow;
            this.amp = amp; this.speed = speed; this.rigid = rigid; this.alpha = alpha; this.scale = scale; this.rootY = rootY;
        }
    }

    public final EnumProperty<Type> type = new EnumProperty<>("Type", Type.Bat);
    public final DoubleProperty size = new DoubleProperty("Size", new DoubleValue(0.5, 1.6, 1.0, 0.05));
    public final DoubleProperty spread = new DoubleProperty("Spread", new DoubleValue(0, 60, 22, 1));
    public final DoubleProperty flapAmount = new DoubleProperty("Flap Amount", new DoubleValue(0, 40, 16, 1));
    public final DoubleProperty flapSpeed = new DoubleProperty("Flap Speed", new DoubleValue(0.2, 3, 1, 0.05));
    public final BooleanProperty others = new BooleanProperty("Others", false);

    @PropertyGroup(Group.VISUAL)
    public final EnumProperty<Style> style = new EnumProperty<>("Style", Style.Default);
    @PropertyGroup(Group.VISUAL) @ShowWhen(property = "Style", is = "Custom")
    public final ColourProperty colour = new ColourProperty("Colour", 0xFF9E9E9E);
    @PropertyGroup(Group.VISUAL)
    public final DoubleProperty opacity = new DoubleProperty("Opacity", new DoubleValue(0.2, 1, 0.95, 0.05));
    @PropertyGroup(Group.VISUAL)
    public final BooleanProperty texture = new BooleanProperty("Pixel Texture", true);
    @PropertyGroup(Group.VISUAL)
    public final BooleanProperty glow = new BooleanProperty("Glow", true);
    @PropertyGroup(Group.VISUAL) @ShowWhen(property = "Glow", is = "true")
    public final DoubleProperty glowStrength = new DoubleProperty("Glow Strength", new DoubleValue(0.1, 2, 1, 0.05));
    @PropertyGroup(Group.VISUAL)
    public final BooleanProperty sparkles = new BooleanProperty("Sparkles", true);

    private static final float PIXELS = 16f;
    private static final int SUBDIV = 4;

    /** One wing's geometry in "wing units" (u = outwards, v = up). */
    private static final class Shape {
        final List<float[]> tris = new ArrayList<>();           // u1,v1,u2,v2,u3,v3, l1,l2,l3, a1,a2,a3, noise, depth
        final List<float[]> bones = new ArrayList<>();          // quad u,v x4, depth
        final List<List<float[]>> boneLines = new ArrayList<>();
        final List<List<float[]>> rimLines = new ArrayList<>();
        final List<float[]> tips = new ArrayList<>();
        float noise = 0.5f, lineLift = 0.02f, boneOff = 0.01f;
    }

    /** Light and accent (0..1) of a membrane point; e = 0 at the panel centre, 1 on the edge. */
    private interface Pattern { float[] at(float u, float v, float e); }

    private final Map<Type, Shape> shapes = new EnumMap<>(Type.class);
    private final float[] tmp = new float[3];
    private float mSweep, mFlap, mWave, mBend, mCurl;      // animation state of the wing being drawn

    private static final class Anim { float phase, amp = 0.4f, speed = 1f; long last; }
    private final Map<Integer, Anim> anims = new HashMap<>();

    @Override
    protected void onDisable() { anims.clear(); }

    // ================= rendering =================
    @EventLink
    public final Listener<EventRenderWorldLast> onRender = event -> {
        if (mc.theWorld == null || mc.thePlayer == null) return;
        IMixinRenderManager rm = (IMixinRenderManager) mc.getRenderManager();
        double rx = rm.getRenderPosX(), ry = rm.getRenderPosY(), rz = rm.getRenderPosZ();
        if (anims.size() > 80) anims.clear();

        Type ty = type.getValue();
        Shape sh = shape(ty);
        float time = (System.currentTimeMillis() % 1_000_000L) / 1000f;
        int[] pal = palette(ty, time);

        mc.entityRenderer.disableLightmap();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.enableDepth();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        try {
            for (EntityPlayer p : mc.theWorld.playerEntities) {
                boolean self = p == mc.thePlayer;
                if (!self && !others.getValue()) continue;
                if (p.isInvisible() || p.isDead) continue;
                if (self && mc.gameSettings.thirdPersonView == 0 && mc.getRenderViewEntity() == p) continue;
                if (!self && p.getDistanceSqToEntity(mc.thePlayer) > 48 * 48) continue;
                renderFor(p, event.partialTicks, rx, ry, rz, ty, sh, pal, time);
            }
        } finally {
            GlStateManager.shadeModel(GL11.GL_FLAT);
            GlStateManager.depthMask(true);
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.enableCull();
            GlStateManager.enableTexture2D();
            GL11.glLineWidth(1f);
            GL11.glPointSize(1f);
            GlStateManager.resetColor();
            mc.entityRenderer.enableLightmap();
        }
    };

    private void renderFor(EntityPlayer p, float pt, double rx, double ry, double rz, Type ty, Shape sh, int[] pal, float time) {
        double x = p.lastTickPosX + (p.posX - p.lastTickPosX) * pt - rx;
        double y = p.lastTickPosY + (p.posY - p.lastTickPosY) * pt - ry;
        double z = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * pt - rz;
        float yaw = p.prevRenderYawOffset + MathHelper.wrapAngleTo180_float(p.renderYawOffset - p.prevRenderYawOffset) * pt;
        Anim an = updateAnim(p, ty);
        float sc = (float) size.getValue().getInput() * ty.scale;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(-yaw, 0f, 1f, 0f);                  // local +z = front, -z = back
        if (p.isSneaking()) {                                     // the torso pivots around the neck
            GlStateManager.translate(0, 1.42, 0);
            GlStateManager.rotate(28.6f, 1f, 0f, 0f);
            GlStateManager.translate(0, -1.5, 0);
        }
        for (int side = -1; side <= 1; side += 2) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(side * 0.07, ty.rootY, -0.16);  // shoulder blades
            GlStateManager.scale(side * sc, sc, sc);                 // mirrored for the left wing
            drawWing(sh, ty, an, pal, time);
            GlStateManager.popMatrix();
        }
        GlStateManager.popMatrix();
    }

    private void drawWing(Shape sh, Type ty, Anim an, int[] pal, float time) {
        float flapRad = (float) Math.toRadians(flapAmount.getValue().getInput()) * ty.amp * an.amp;
        float s = MathHelper.sin(an.phase);
        mSweep = (float) Math.toRadians(spread.getValue().getInput());
        if (ty.rigid) {                       // butterfly / fairy: rigid wing, never swings in front of the body
            mFlap = flapRad * (0.5f + 0.5f * s);
            mBend = 1f; mWave = 0f; mCurl = 0.03f;
        } else {                              // membranes and feathers: the tip flaps harder than the root
            mFlap = flapRad * s;
            mBend = 0.35f; mWave = 0.06f * an.amp * MathHelper.sin(an.phase + 0.8f); mCurl = 0.10f;
        }

        float br = (pal[0] >> 16 & 255) / 255f, bg = (pal[0] >> 8 & 255) / 255f, bb = (pal[0] & 255) / 255f;
        float ar = (pal[1] >> 16 & 255) / 255f, ag = (pal[1] >> 8 & 255) / 255f, ab = (pal[1] & 255) / 255f;
        float or = (pal[2] >> 16 & 255) / 255f, og = (pal[2] >> 8 & 255) / 255f, ob = (pal[2] & 255) / 255f;
        float gr = (pal[3] >> 16 & 255) / 255f, gg = (pal[3] >> 8 & 255) / 255f, gb = (pal[3] & 255) / 255f;
        float alpha = (float) opacity.getValue().getInput() * ty.alpha;
        boolean tex = texture.getValue();
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();

        // 1. membrane / feathers
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.depthMask(alpha >= 0.99f);
        wr.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        for (float[] t : sh.tris) {
            float n = tex ? 1f - t[12] : 1f - sh.noise * 0.5f;
            for (int k = 0; k < 3; k++) {
                map(t[k * 2], t[k * 2 + 1], t[13]);
                float a = t[9 + k], l = t[6 + k] * n;
                wr.pos(tmp[0], tmp[1], tmp[2])
                        .color(c((br + (ar - br) * a) * l), c((bg + (ag - bg) * a) * l), c((bb + (ab - bb) * a) * l), alpha)
                        .endVertex();
            }
        }
        tess.draw();

        // 2. bones, veins, feather shafts, spikes (both sides)
        GlStateManager.depthMask(true);
        int[] order = {0, 1, 2, 0, 2, 3};
        wr.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        for (int sd = -1; sd <= 1; sd += 2) {
            for (float[] q : sh.bones) {
                float d = q[8] + sh.boneOff * sd;
                for (int idx : order) {
                    map(q[idx * 2], q[idx * 2 + 1], d);
                    float shade = idx < 2 ? 1f : 0.8f;
                    wr.pos(tmp[0], tmp[1], tmp[2]).color(or * shade, og * shade, ob * shade, Math.max(alpha, 0.85f)).endVertex();
                }
            }
        }
        tess.draw();

        // 3. glow (additive)
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GlStateManager.depthMask(false);
        float flicker = ty == Type.Phoenix ? 0.7f + 0.3f * MathHelper.sin(time * 11f) * MathHelper.sin(time * 7.3f + 1f) : 1f;
        if (glow.getValue()) {
            float g = (float) glowStrength.getValue().getInput() * flicker;
            lines(sh.boneLines, sh.lineLift, 5f, 0.10f * g, gr, gg, gb);
            lines(sh.boneLines, sh.lineLift, 1.6f, 0.45f * g, gr, gg, gb);
            lines(sh.rimLines, sh.lineLift, 3f, 0.08f * g, gr, gg, gb);
            lines(sh.rimLines, sh.lineLift, 1.1f, 0.35f * g, gr, gg, gb);
        }

        // 4. sparkles on the wing tips
        if (sparkles.getValue()) {
            float[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
                    {0.4f, 0.4f, 0}, {-0.4f, 0.4f, 0}, {0.4f, -0.4f, 0}, {-0.4f, -0.4f, 0}};
            for (int i = 0; i < sh.tips.size(); i++) {
                float[] tip = sh.tips.get(i);
                map(tip[0], tip[1], 0f);
                float cx = tmp[0], cy = tmp[1], cz = tmp[2];
                float tw = (0.55f + 0.45f * MathHelper.sin(time * 4f + i * 2.1f)) * flicker;
                float sz = 0.11f * tw;
                GL11.glLineWidth(2f);
                wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
                for (float[] dv : dirs) {
                    wr.pos(cx, cy, cz).color(gr, gg, gb, 0.95f * tw).endVertex();
                    wr.pos(cx + dv[0] * sz, cy + dv[1] * sz, cz + dv[2] * sz).color(gr, gg, gb, 0f).endVertex();
                }
                tess.draw();
                GL11.glPointSize(4f);
                wr.begin(GL11.GL_POINTS, DefaultVertexFormats.POSITION_COLOR);
                wr.pos(cx, cy, cz).color(1f, 1f, 1f, 0.9f * tw).endVertex();
                tess.draw();
            }
        }
        GlStateManager.depthMask(true);
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void lines(List<List<float[]>> polys, float lift, float width, float a, float r, float g, float b) {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        GL11.glLineWidth(width);
        wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        for (int s = -1; s <= 1; s += 2) {
            float d = lift * s;
            for (List<float[]> pl : polys) {
                for (int i = 0; i < pl.size() - 1; i++) {
                    float[] p0 = pl.get(i), p1 = pl.get(i + 1);
                    map(p0[0], p0[1], d);
                    wr.pos(tmp[0], tmp[1], tmp[2]).color(r, g, b, a).endVertex();
                    map(p1[0], p1[1], d);
                    wr.pos(tmp[0], tmp[1], tmp[2]).color(r, g, b, a).endVertex();
                }
            }
        }
        tess.draw();
    }

    /** Wing plane -> local space: swept back, flapped, slightly curved. */
    private void map(float u, float v, float d) {
        float a = mSweep + mFlap * (mBend + (1f - mBend) * u);
        float cos = MathHelper.cos(a), sin = MathHelper.sin(a);
        tmp[0] = u * cos + d * sin;
        tmp[1] = v + mWave * u * u;
        tmp[2] = -u * sin + d * cos - mCurl * u * u;
    }

    private Anim updateAnim(EntityPlayer p, Type ty) {
        long now = System.nanoTime();
        Anim a = anims.get(p.getEntityId());
        if (a == null) {
            a = new Anim();
            a.last = now;
            a.phase = (p.getEntityId() * 1.37f) % 6.28f;
            anims.put(p.getEntityId(), a);
        }
        float dt = Math.min(0.1f, (now - a.last) / 1e9f);
        a.last = now;
        double mx = p.posX - p.prevPosX, mz = p.posZ - p.prevPosZ;
        boolean moving = mx * mx + mz * mz > 0.0009;
        boolean air = !p.onGround || p.capabilities.isFlying;
        float targetAmp = air ? 1f : moving ? 0.6f : 0.35f;
        float targetSpeed = air ? 2.2f : moving ? 1.4f : 1f;
        float k = Math.min(1f, dt * 3f);
        a.amp += (targetAmp - a.amp) * k;
        a.speed += (targetSpeed - a.speed) * k;
        a.phase = (a.phase + dt * a.speed * ty.speed * (float) flapSpeed.getValue().getInput() * (float) Math.PI)
                % (float) (Math.PI * 200);
        return a;
    }

    // ================= colours =================
    private int[] palette(Type t, float time) {
        switch (style.getValue()) {
            case Default: return new int[]{t.base, t.accent, t.bone, t.glow};
            case Theme: return derive(t, Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor() & 0xFFFFFF);
            case Rainbow: return derive(t, Color.HSBtoRGB((time * 0.08f) % 1f, 0.65f, 1f) & 0xFFFFFF);
            default: return derive(t, colour.getValue() & 0xFFFFFF);
        }
    }

    private static int[] derive(Type t, int base) {
        boolean darkAccent = ColorUtils.luminance(t.accent) < ColorUtils.luminance(t.base);
        int accent = ColorUtils.mixRgb(base, darkAccent ? 0x101010 : 0xFFFFFF, 0.6f) & 0xFFFFFF;
        int bone = (t == Type.Fairy ? ColorUtils.mixRgb(base, 0xFFFFFF, 0.7f) : ColorUtils.mixRgb(base, 0x000000, 0.8f)) & 0xFFFFFF;
        int glowC = ColorUtils.mixRgb(base, 0xFFFFFF, 0.55f) & 0xFFFFFF;
        return new int[]{base, accent, bone, glowC};
    }

    private static float c(float x) { return x < 0 ? 0 : x > 1 ? 1 : x; }

    // ================= shape construction (once per type) =================
    private Shape shape(Type t) {
        Shape s = shapes.get(t);
        if (s == null) {
            s = new Shape();
            switch (t) {
                case Bat: buildBat(s); break;
                case Dragon: buildDragon(s); break;
                case Angel: buildFeathered(s, 0.35f, 3f, 1f); break;
                case Phoenix: buildFeathered(s, 1f, 1.4f, 1.15f); break;
                case Butterfly: buildButterfly(s); break;
                default: buildFairy(s);
            }
            shapes.put(t, s);
        }
        return s;
    }

    private static void buildBat(Shape s) {
        s.noise = 0.58f;
        float[] R = {0f, 0f}, W = {0.42f, 0.30f}, T = {1.00f, 0.64f}, F1 = {1.12f, 0.04f}, F2 = {0.90f, -0.50f},
                F3 = {0.52f, -0.88f}, B = {0.06f, -0.62f};
        Pattern p = (u, v, e) -> new float[]{0.78f + 0.3f * MathHelper.clamp_float((v + 0.9f) / 1.55f, 0f, 1f), 0f};
        fingered(s, R, W, new float[]{0.16f, 0.24f},
                new float[][]{T, F1, F2, F3},
                new float[][]{{0.70f, 0.66f}, {0.80f, 0.24f}, {0.72f, -0.08f}, {0.52f, -0.34f}},
                B, new float[]{0.11f, 0.15f, 0.17f, 0.15f}, 0.025f, p, 0.075f, 0.055f, 0.042f);
        s.tips.add(T); s.tips.add(F1); s.tips.add(F3);
    }

    private static void buildDragon(Shape s) {
        s.noise = 0.45f;
        float[] R = {0f, 0f}, W = {0.48f, 0.40f}, CA = {0.18f, 0.30f}, T = {1.25f, 0.95f}, CT = {0.80f, 0.88f},
                F1 = {1.45f, 0.30f}, F2 = {1.25f, -0.32f}, F3 = {0.92f, -0.78f}, F4 = {0.48f, -1.02f}, B = {0.06f, -0.60f};
        Pattern p = (u, v, e) -> new float[]{
                0.72f + 0.3f * MathHelper.clamp_float((v + 1f) / 1.9f, 0f, 1f) + 0.1f * (1f - e),
                MathHelper.clamp_float((e - 0.55f) / 0.45f, 0f, 1f) * 0.85f};
        fingered(s, R, W, CA,
                new float[][]{T, F1, F2, F3, F4},
                new float[][]{CT, {0.98f, 0.44f}, {0.92f, 0.04f}, {0.74f, -0.30f}, {0.50f, -0.48f}},
                B, new float[]{0.14f, 0.20f, 0.20f, 0.20f, 0.17f}, 0.04f, p, 0.09f, 0.07f, 0.05f);
        List<float[]> arm = bez(R, CA, W, 6), thumb = bez(W, CT, T, 10);
        for (int i = 1; i < arm.size() - 1; i += 2) spikeOn(s, arm, i, 0.07f, 0.022f);
        for (int i = 2; i < thumb.size() - 1; i += 2) spikeOn(s, thumb, i, 0.06f, 0.018f);
        spike(s, W, -0.25f, 1f, 0.11f, 0.03f);                       // wrist claw
        spike(s, T, T[0] - CT[0], T[1] - CT[1], 0.13f, 0.025f);        // horn on the tip
        s.tips.add(T); s.tips.add(F1); s.tips.add(F2); s.tips.add(F4);
    }

    /** Membrane wing: arm, fingers, membrane panels between the fingers with a jagged edge. */
    private static void fingered(Shape s, float[] root, float[] wrist, float[] armCtrl, float[][] digits, float[][] ctrls,
                                 float[] back, float[] depths, float tooth, Pattern p, float armW, float thumbW, float fingerW) {
        List<float[]> arm = bez(root, armCtrl, wrist, 6);
        List<List<float[]>> d = new ArrayList<>();
        for (int i = 0; i < digits.length; i++) d.add(bez(wrist, ctrls[i], digits[i], 10));
        for (int i = 0; i < digits.length - 1; i++) {
            float[] hub = avg(wrist, digits[i], digits[i + 1]);
            List<float[]> edge = scallop(digits[i], digits[i + 1], hub, depths[i], 10, tooth);
            panel(s, hub, join(d.get(i), edge, rev(d.get(i + 1))), p);
            s.rimLines.add(edge);
        }
        float[] last = digits[digits.length - 1];
        float[] hub = avg(wrist, last, back, root);
        List<float[]> edge = scallop(last, back, hub, depths[digits.length - 1], 10, tooth);
        panel(s, hub, join(d.get(d.size() - 1), edge, line(back, root, 4), arm), p);
        s.rimLines.add(edge);
        ribbon(s, arm, armW, armW * 0.73f, 0f, true);
        ribbon(s, d.get(0), thumbW, 0.008f, 0f, true);
        for (int i = 1; i < d.size(); i++) ribbon(s, d.get(i), fingerW, 0.006f, 0f, true);
    }

    // ---- feathers (angel, phoenix) ----
    private static final float[] E0 = {0f, 0.05f}, EC = {0.35f, 0.80f}, E1 = {0.98f, 0.78f};   // leading edge

    private static void buildFeathered(Shape s, float accAmt, float accPow, float lenMul) {
        s.noise = 0.2f; s.lineLift = 0.045f; s.boneOff = 0.004f;
        feathers(s, 13, 0.06f, 1f, lenMul, 0f, 0f, 0.9f, accAmt, accPow, true, 1);              // primaries
        feathers(s, 11, 0.03f, 0.97f, 0.55f, 0.03f, 0.016f, 0.97f, accAmt * 0.5f, accPow, false, 500);   // coverts
        feathers(s, 9, 0f, 0.92f, 0.28f, 0.06f, 0.032f, 1.05f, accAmt * 0.25f, accPow, false, 900);      // small feathers
    }

    private static void feathers(Shape s, int count, float t0, float t1, float lenMul, float lift, float depth, float light,
                                 float accAmt, float accPow, boolean primary, int seedBase) {
        for (int k = 0; k < count; k++) {
            float t = t0 + (t1 - t0) * k / (count - 1f);
            float[] base = bezAt(E0, EC, E1, t);
            base[1] += lift;
            float ang = (float) Math.toRadians(-92 + 62 * t);           // down near the back, outwards at the tip
            float len = (0.42f + 0.5f * t) * lenMul;
            float w = 0.13f + 0.05f * t;
            feather(s, base, ang, len, w, depth + k * 0.0009f, light, accAmt, accPow,
                    primary, primary, primary && k >= count - 3, seedBase + k * 31);
        }
    }

    private static void feather(Shape s, float[] base, float ang, float len, float w, float depth, float light,
                                float accAmt, float accPow, boolean shaft, boolean rim, boolean tip, int seed) {
        final int n = 6;
        float dx = MathHelper.cos(ang), dy = MathHelper.sin(ang), nx = -dy, ny = dx;
        float[][] c = new float[n + 1][], l = new float[n + 1][], r = new float[n + 1][];
        float[] acc = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            float x = i / (float) n;
            float q = (x - 0.7f) / 0.3f;
            float h = x < 0.7f ? 0.5f * w * (0.55f + 0.45f * x / 0.7f) : 0.5f * w * (float) Math.sqrt(Math.max(0f, 1f - q * q));
            float px = base[0] + dx * len * x, py = base[1] + dy * len * x;
            c[i] = new float[]{px, py};
            l[i] = new float[]{px + nx * h, py + ny * h};
            r[i] = new float[]{px - nx * h * 0.8f, py - ny * h * 0.8f};    // asymmetric vanes
            acc[i] = accAmt * (float) Math.pow(x, accPow);
        }
        float lc = light, le = light * 0.78f;
        for (int i = 0; i < n; i++) {
            float nl = s.noise * (1f - hash(seed, i * 2)), nr = s.noise * (1f - hash(seed, i * 2 + 1));
            addTri(s, c[i], l[i], l[i + 1], lc, le, le, acc[i], acc[i], acc[i + 1], nl, depth);
            addTri(s, c[i], l[i + 1], c[i + 1], lc, le, lc, acc[i], acc[i + 1], acc[i + 1], nl, depth);
            addTri(s, c[i], r[i + 1], r[i], lc, le, le, acc[i], acc[i + 1], acc[i], nr, depth);
            addTri(s, c[i], c[i + 1], r[i + 1], lc, lc, le, acc[i], acc[i + 1], acc[i + 1], nr, depth);
        }
        if (shaft) {
            List<float[]> sp = new ArrayList<>();
            for (int i = 0; i < n; i++) sp.add(c[i]);
            ribbon(s, sp, 0.016f, 0.004f, depth, true);
        }
        if (rim) {                                   // outer half only, the base is hidden
            List<float[]> o = new ArrayList<>();
            for (int i = 3; i <= n; i++) o.add(l[i]);
            for (int i = n - 1; i >= 3; i--) o.add(r[i]);
            s.rimLines.add(o);
        }
        if (tip) s.tips.add(c[n]);
    }

    // ---- butterfly, fairy ----
    private static void buildButterfly(Shape s) {
        s.noise = 0.25f; s.boneOff = 0.008f;
        Pattern p = (u, v, e) -> {
            if (e > 0.8f) {                          // black border with light dots
                boolean dot = e > 0.86f && e < 0.97f && hash((int) Math.floor(u * 16), (int) Math.floor(v * 16) + 77) > 0.62f;
                return dot ? new float[]{2.4f, 0f} : new float[]{1f, 1f};
            }
            float d1 = dist(u, v, 0.80f, 0.60f), d2 = dist(u, v, 0.48f, -0.52f);
            if (d1 < 0.06f || d2 < 0.045f) return new float[]{0.9f, 1f};     // eyespot centre
            if (d1 < 0.11f || d2 < 0.08f) return new float[]{1.6f, 0f};      // light ring
            return new float[]{0.95f + 0.25f * e, 0f};
        };
        lobe(s, 0.50f, 0.40f, 0.62f, 0.42f, 35f, 0.03f, 5, p, 5, 0.022f);
        lobe(s, 0.32f, -0.32f, 0.47f, 0.30f, -50f, 0.04f, 3, p, 4, 0.018f);
    }

    private static void buildFairy(Shape s) {
        s.noise = 0.12f; s.boneOff = 0.006f;
        Pattern p = (u, v, e) -> new float[]{1.05f, (float) Math.pow(e, 1.6)};
        lobe(s, 0.55f, 0.45f, 0.71f, 0.20f, 30f, 0f, 1, p, 3, 0.012f);
        lobe(s, 0.45f, -0.20f, 0.52f, 0.15f, -25f, 0f, 1, p, 3, 0.010f);
    }

    private static void lobe(Shape s, float cx, float cy, float rx, float ry, float ang, float wobble, int lobes,
                             Pattern p, int veins, float veinW) {
        List<float[]> out = new ArrayList<>();
        int n = 32;
        for (int i = 0; i < n; i++) out.add(ellipsePoint(cx, cy, rx, ry, ang, 360f * i / n, 1f + wobble * MathHelper.sin(lobes * 6.2832f * i / n)));
        panel(s, new float[]{cx, cy}, out, p);
        List<float[]> rim = new ArrayList<>(out);
        rim.add(out.get(0));
        s.rimLines.add(rim);
        float[] root = {0f, 0f};
        for (int i = 0; i < veins; i++) {
            float t = veins == 1 ? 0f : -100f + 200f * i / (veins - 1);
            float[] end = ellipsePoint(cx, cy, rx, ry, ang, t, 0.93f);
            float[] ctrl = {(root[0] + end[0]) * 0.25f + cx * 0.5f, (root[1] + end[1]) * 0.25f + cy * 0.5f};
            ribbon(s, bez(root, ctrl, end, 8), veinW, veinW * 0.3f, 0f, true);
        }
        s.tips.add(ellipsePoint(cx, cy, rx, ry, ang, 0f, 1f));
    }

    private static float[] ellipsePoint(float cx, float cy, float rx, float ry, float angDeg, float tDeg, float k) {
        float a = (float) Math.toRadians(angDeg), t = (float) Math.toRadians(tDeg);
        float x = rx * MathHelper.cos(t) * k, y = ry * MathHelper.sin(t) * k;
        return new float[]{cx + x * MathHelper.cos(a) - y * MathHelper.sin(a), cy + x * MathHelper.sin(a) + y * MathHelper.cos(a)};
    }

    // ---- primitives ----
    private static void panel(Shape s, float[] hub, List<float[]> bd, Pattern p) {
        int n = bd.size();
        for (int i = 0; i < n; i++) {
            float[] a = bd.get(i), b = bd.get((i + 1) % n);
            for (int x = 0; x < SUBDIV; x++) for (int y = 0; y < SUBDIV - x; y++) {
                gridTri(s, hub, a, b, x, y, x + 1, y, x, y + 1, p);
                if (x + y + 2 <= SUBDIV) gridTri(s, hub, a, b, x + 1, y, x + 1, y + 1, x, y + 1, p);
            }
        }
    }

    private static void gridTri(Shape s, float[] h, float[] a, float[] b, int i1, int j1, int i2, int j2, int i3, int j3, Pattern p) {
        float[] p1 = pt(h, a, b, i1, j1), p2 = pt(h, a, b, i2, j2), p3 = pt(h, a, b, i3, j3);
        float[] q1 = p.at(p1[0], p1[1], (i1 + j1) / (float) SUBDIV);
        float[] q2 = p.at(p2[0], p2[1], (i2 + j2) / (float) SUBDIV);
        float[] q3 = p.at(p3[0], p3[1], (i3 + j3) / (float) SUBDIV);
        float cu = (p1[0] + p2[0] + p3[0]) / 3f, cv = (p1[1] + p2[1] + p3[1]) / 3f;
        addTri(s, p1, p2, p3, q1[0], q2[0], q3[0], q1[1], q2[1], q3[1], s.noise * (1f - pixel(cu, cv)), 0f);
    }

    private static void addTri(Shape s, float[] a, float[] b, float[] c, float l1, float l2, float l3,
                               float a1, float a2, float a3, float noise, float depth) {
        s.tris.add(new float[]{a[0], a[1], b[0], b[1], c[0], c[1], l1, l2, l3, a1, a2, a3, noise, depth});
    }

    private static float pixel(float u, float v) {
        return 0.65f * hash((int) Math.floor(u * PIXELS), (int) Math.floor(v * PIXELS))
                + 0.35f * hash((int) Math.floor(u * PIXELS / 2) + 999, (int) Math.floor(v * PIXELS / 2));
    }

    private static float[] pt(float[] h, float[] a, float[] b, int i, int j) {
        float fi = i / (float) SUBDIV, fj = j / (float) SUBDIV;
        return new float[]{h[0] + (a[0] - h[0]) * fi + (b[0] - h[0]) * fj, h[1] + (a[1] - h[1]) * fi + (b[1] - h[1]) * fj};
    }

    private static void ribbon(Shape s, List<float[]> pts, float w0, float w1, float depth, boolean glowLine) {
        int n = pts.size();
        float[][] l = new float[n][], r = new float[n][];
        for (int i = 0; i < n; i++) {
            float[] a = pts.get(Math.max(0, i - 1)), b = pts.get(Math.min(n - 1, i + 1)), p = pts.get(i);
            float tx = b[0] - a[0], ty = b[1] - a[1], len = (float) Math.sqrt(tx * tx + ty * ty);
            if (len < 1e-5f) len = 1;
            tx /= len; ty /= len;
            float w = (w0 + (w1 - w0) * i / (float) (n - 1)) / 2f;
            l[i] = new float[]{p[0] - ty * w, p[1] + tx * w};
            r[i] = new float[]{p[0] + ty * w, p[1] - tx * w};
        }
        for (int i = 0; i < n - 1; i++)
            s.bones.add(new float[]{l[i][0], l[i][1], r[i][0], r[i][1], r[i + 1][0], r[i + 1][1], l[i + 1][0], l[i + 1][1], depth});
        if (glowLine) s.boneLines.add(pts);
    }

    private static void spikeOn(Shape s, List<float[]> curve, int i, float len, float w) {
        float[] a = curve.get(i - 1), b = curve.get(i + 1);
        float tx = b[0] - a[0], ty = b[1] - a[1], l = (float) Math.sqrt(tx * tx + ty * ty);
        tx /= l; ty /= l;
        spike(s, curve.get(i), -ty + tx * 0.6f, tx + ty * 0.6f, len, w);   // outwards, leaning towards the tip
    }

    private static void spike(Shape s, float[] p, float dx, float dy, float len, float w) {
        float l = (float) Math.sqrt(dx * dx + dy * dy);
        dx /= l; dy /= l;
        float nx = -dy, ny = dx;
        float tx = p[0] + dx * len, ty = p[1] + dy * len;
        s.bones.add(new float[]{p[0] + nx * w, p[1] + ny * w, p[0] - nx * w, p[1] - ny * w, tx, ty, tx, ty, 0f});
    }

    private static List<float[]> bez(float[] a, float[] c, float[] b, int n) {
        List<float[]> out = new ArrayList<>();
        for (int i = 0; i <= n; i++) out.add(bezAt(a, c, b, i / (float) n));
        return out;
    }

    private static float[] bezAt(float[] a, float[] c, float[] b, float t) {
        float m = 1 - t;
        return new float[]{m * m * a[0] + 2 * m * t * c[0] + t * t * b[0], m * m * a[1] + 2 * m * t * c[1] + t * t * b[1]};
    }

    /** Membrane edge hollowed towards the centre, with small teeth. */
    private static List<float[]> scallop(float[] a, float[] b, float[] toward, float depth, int n, float tooth) {
        List<float[]> out = new ArrayList<>();
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            float x = a[0] + (b[0] - a[0]) * t, y = a[1] + (b[1] - a[1]) * t;
            float dx = toward[0] - x, dy = toward[1] - y, len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len > 1e-4f) { dx /= len; dy /= len; }
            float d = depth * (float) Math.sin(Math.PI * t);
            if (i % 2 == 1) d += tooth;
            out.add(new float[]{x + dx * d, y + dy * d});
        }
        return out;
    }

    private static List<float[]> line(float[] a, float[] b, int n) {
        List<float[]> out = new ArrayList<>();
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            out.add(new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t});
        }
        return out;
    }

    private static List<float[]> rev(List<float[]> l) { List<float[]> r = new ArrayList<>(l); Collections.reverse(r); return r; }

    @SafeVarargs
    private static List<float[]> join(List<float[]>... parts) {
        List<float[]> out = new ArrayList<>();
        for (List<float[]> part : parts) for (float[] p : part) {
            if (!out.isEmpty()) {
                float[] q = out.get(out.size() - 1);
                if (Math.abs(q[0] - p[0]) < 1e-4f && Math.abs(q[1] - p[1]) < 1e-4f) continue;
            }
            out.add(p);
        }
        if (out.size() > 1) {
            float[] f = out.get(0), l = out.get(out.size() - 1);
            if (Math.abs(f[0] - l[0]) < 1e-4f && Math.abs(f[1] - l[1]) < 1e-4f) out.remove(out.size() - 1);
        }
        return out;
    }

    private static float[] avg(float[]... pts) {
        float x = 0, y = 0;
        for (float[] p : pts) { x += p[0]; y += p[1]; }
        return new float[]{x / pts.length, y / pts.length};
    }

    private static float dist(float u, float v, float x, float y) {
        float dx = u - x, dy = v - y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static float hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535f;
    }
}
