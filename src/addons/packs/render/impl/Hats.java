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
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Procedurally built cosmetic hats. Client side only: nobody else sees them. Submitted by polycraft. */
@ModuleInfo(name = "Hats", description = "Cosmetic hats (6 types)", category = ModuleCategory.RENDER, tier = ModuleTier.QOL)
public class Hats extends Module {

    public enum Style { Default, Theme, Rainbow, Custom }

    /** primary, secondary, glow colour */
    public enum HatType {
        Crown(0xF5C542, 0x8E1B2B, 0xFFE9A0),
        TopHat(0x1E1E22, 0xB3202A, 0xFFFFFF),
        Witch(0x3B2A5C, 0x7BC94A, 0xFFE45C),
        Halo(0xFFE680, 0xFFFFFF, 0xFFF2B0),
        Mushroom(0xD6312B, 0xF2EEE4, 0xB8FFB0),
        Propeller(0x2E6FD8, 0xF2C230, 0xFFFFFF);

        final int primary, secondary, glow;
        HatType(int p, int s, int g) { primary = p; secondary = s; glow = g; }
    }

    public final EnumProperty<HatType> type = new EnumProperty<>("Type", HatType.Crown);
    public final DoubleProperty size = new DoubleProperty("Size", new DoubleValue(0.5, 1.8, 1.0, 0.05));
    public final DoubleProperty height = new DoubleProperty("Height", new DoubleValue(-0.2, 0.4, 0, 0.01));
    public final DoubleProperty tilt = new DoubleProperty("Tilt", new DoubleValue(-30, 30, 0, 1));
    public final BooleanProperty animate = new BooleanProperty("Animate", true);
    public final BooleanProperty others = new BooleanProperty("Others", false);

    @PropertyGroup(Group.VISUAL)
    public final EnumProperty<Style> style = new EnumProperty<>("Style", Style.Default);
    @PropertyGroup(Group.VISUAL) @ShowWhen(property = "Style", is = "Custom")
    public final ColourProperty colour = new ColourProperty("Colour", 0xFFF5C542);
    @PropertyGroup(Group.VISUAL)
    public final BooleanProperty glow = new BooleanProperty("Glow", true);
    @PropertyGroup(Group.VISUAL) @ShowWhen(property = "Glow", is = "true")
    public final DoubleProperty glowStrength = new DoubleProperty("Glow Strength", new DoubleValue(0.1, 2, 1, 0.05));
    @PropertyGroup(Group.VISUAL)
    public final BooleanProperty sparkles = new BooleanProperty("Sparkles", true);

    // ================= geometry =================
    private static final float LX, LY, LZ;
    static {
        float x = 0.3f, y = 0.9f, z = 0.45f, l = (float) Math.sqrt(x * x + y * y + z * z);
        LX = x / l; LY = y / l; LZ = z / l;
    }

    private static float light(float nx, float ny, float nz) {
        float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-6f) return 0.8f;
        return 0.42f + 0.58f * Math.max(0f, (nx * LX + ny * LY + nz * LZ) / l);
    }

    private interface Warp { void apply(float[] p); }

    /** Triangle list: {x, y, z, light, slot (0 primary, 1 secondary, 2 fixed), r, g, b}. */
    private static final class Mesh {
        final List<float[]> v = new ArrayList<>();
        int slot;
        float cr = 1, cg = 1, cb = 1, ox, oy, oz;
        Warp warp;

        Mesh col(int s) { slot = s; return this; }
        Mesh fix(int rgb) { slot = 2; cr = (rgb >> 16 & 255) / 255f; cg = (rgb >> 8 & 255) / 255f; cb = (rgb & 255) / 255f; return this; }
        Mesh at(float x, float y, float z) { ox = x; oy = y; oz = z; return this; }

        void vert(float x, float y, float z, float shade) {
            float[] p = {x + ox, y + oy, z + oz};
            if (warp != null) warp.apply(p);
            v.add(new float[]{p[0], p[1], p[2], shade, slot, cr, cg, cb});
        }

        void tri(float[] a, float[] b, float[] c) {
            float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
            float wx = c[0] - a[0], wy = c[1] - a[1], wz = c[2] - a[2];
            float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
            float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            float d = l < 1e-9f ? 1f : Math.abs((nx * LX + ny * LY + nz * LZ) / l);
            float sh = 0.42f + 0.58f * d;
            vert(a[0], a[1], a[2], sh); vert(b[0], b[1], b[2], sh); vert(c[0], c[1], c[2], sh);
        }

        void quad(float[] a, float[] b, float[] c, float[] d) { tri(a, b, c); tri(a, c, d); }

        void lathe(float[][] pr, int segs) { lathe(pr, segs, null); }

        /** A (radius, height) profile turned around the Y axis. Walk the profile "outside on the right" for outward normals. */
        void lathe(float[][] pr, int segs, int[] sectors) {
            int keep = slot;
            for (int i = 0; i < pr.length - 1; i++) {
                float r0 = pr[i][0], y0 = pr[i][1], r1 = pr[i + 1][0], y1 = pr[i + 1][1];
                float dr = r1 - r0, dy = y1 - y0, nl = (float) Math.sqrt(dr * dr + dy * dy);
                if (nl < 1e-6f) continue;
                float nr = dy / nl, ny = -dr / nl;
                for (int k = 0; k < segs; k++) {
                    if (sectors != null) slot = sectors[k * sectors.length / segs];
                    double a0 = 2 * Math.PI * k / segs, a1 = 2 * Math.PI * (k + 1) / segs;
                    float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                    float l0 = light(nr * c0, ny, nr * s0), l1 = light(nr * c1, ny, nr * s1);
                    vert(r0 * c0, y0, r0 * s0, l0); vert(r1 * c0, y1, r1 * s0, l0); vert(r1 * c1, y1, r1 * s1, l1);
                    vert(r0 * c0, y0, r0 * s0, l0); vert(r1 * c1, y1, r1 * s1, l1); vert(r0 * c1, y0, r0 * s1, l1);
                }
            }
            slot = keep;
        }

        void sphere(float r, int segs) {
            int n = Math.max(3, segs / 2);
            float[][] pr = new float[n + 1][];
            for (int j = 0; j <= n; j++) {
                double f = -Math.PI / 2 + Math.PI * j / n;
                pr[j] = new float[]{(float) (r * Math.cos(f)), (float) (r * Math.sin(f))};
            }
            lathe(pr, segs);
        }

        void torus(float R, float r, int segs, int sides) {
            for (int i = 0; i < segs; i++) for (int j = 0; j < sides; j++) {
                float t0 = (float) (2 * Math.PI * i / segs), t1 = (float) (2 * Math.PI * (i + 1) / segs);
                float f0 = (float) (2 * Math.PI * j / sides), f1 = (float) (2 * Math.PI * (j + 1) / sides);
                tv(R, r, t0, f0); tv(R, r, t1, f0); tv(R, r, t1, f1);
                tv(R, r, t0, f0); tv(R, r, t1, f1); tv(R, r, t0, f1);
            }
        }

        private void tv(float R, float r, float t, float f) {
            float ct = (float) Math.cos(t), st = (float) Math.sin(t), cf = (float) Math.cos(f), sf = (float) Math.sin(f);
            vert((R + r * cf) * ct, r * sf, (R + r * cf) * st, light(cf * ct, sf, cf * st));
        }

        void box(float x0, float y0, float z0, float x1, float y1, float z1) {
            float[] a = {x0, y0, z0}, b = {x1, y0, z0}, c = {x1, y0, z1}, d = {x0, y0, z1};
            float[] e = {x0, y1, z0}, f = {x1, y1, z0}, g = {x1, y1, z1}, h = {x0, y1, z1};
            quad(a, b, c, d); quad(e, f, g, h); quad(a, b, f, e); quad(d, c, g, h); quad(a, d, h, e); quad(b, c, g, f);
        }

        /** Octahedron (gem shape). */
        void gem(float x, float y, float z, float sx, float sy, float sz) {
            float[] t = {x, y + sy, z}, bo = {x, y - sy, z};
            float[][] eq = {{x + sx, y, z}, {x, y, z + sz}, {x - sx, y, z}, {x, y, z - sz}};
            for (int i = 0; i < 4; i++) {
                float[] p = eq[i], q = eq[(i + 1) % 4];
                tri(p, q, t); tri(q, p, bo);
            }
        }

        /** Slightly thick five-pointed star in the XY plane. */
        void star(float ro, float ri, float th) {
            float[] f = {0, 0, th}, bk = {0, 0, -th};
            for (int i = 0; i < 10; i++) {
                double a0 = Math.PI / 2 + i * Math.PI / 5, a1 = Math.PI / 2 + (i + 1) * Math.PI / 5;
                float r0 = i % 2 == 0 ? ro : ri, r1 = (i + 1) % 2 == 0 ? ro : ri;
                float[] p0 = {(float) (r0 * Math.cos(a0)), (float) (r0 * Math.sin(a0)), 0};
                float[] p1 = {(float) (r1 * Math.cos(a1)), (float) (r1 * Math.sin(a1)), 0};
                tri(p0, p1, f); tri(p1, p0, bk);
            }
        }
    }

    private static final class Part {
        final Mesh mesh = new Mesh();
        final List<float[]> sparkles = new ArrayList<>();
        float px, py, pz, spin, bob, tilt, wiggle;
        boolean glow, pulse, propeller;
    }

    private static final class Hat {
        final List<Part> parts = new ArrayList<>();
        boolean spores;
        Part part(float x, float y, float z) { Part p = new Part(); p.px = x; p.py = y; p.pz = z; parts.add(p); return p; }
    }

    private static final class PState { float prop, speed = 360f; long last; }

    private final Map<HatType, Hat> hats = new EnumMap<>(HatType.class);
    private final Map<Integer, PState> states = new HashMap<>();

    @Override
    protected void onDisable() { states.clear(); }

    // ================= rendering =================
    @EventLink
    public final Listener<EventRenderWorldLast> onRender = event -> {
        if (mc.theWorld == null || mc.thePlayer == null) return;
        IMixinRenderManager rm = (IMixinRenderManager) mc.getRenderManager();
        double rx = rm.getRenderPosX(), ry = rm.getRenderPosY(), rz = rm.getRenderPosZ();
        if (states.size() > 80) states.clear();

        HatType ty = type.getValue();
        Hat hat = hat(ty);
        float time = (System.currentTimeMillis() % 3_600_000L) / 1000f;
        float[] pal = palette(ty, time);

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
                renderFor(p, event.partialTicks, rx, ry, rz, hat, pal, time);
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

    private void renderFor(EntityPlayer p, float pt, double rx, double ry, double rz, Hat hat, float[] pal, float time) {
        double x = p.lastTickPosX + (p.posX - p.lastTickPosX) * pt - rx;
        double y = p.lastTickPosY + (p.posY - p.lastTickPosY) * pt - ry;
        double z = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * pt - rz;
        float headYaw = p.prevRotationYawHead + MathHelper.wrapAngleTo180_float(p.rotationYawHead - p.prevRotationYawHead) * pt;
        float pitch = p.prevRotationPitch + (p.rotationPitch - p.prevRotationPitch) * pt;
        float neck = 1.40625f;                                       // neck height (model scaled by 0.9375)
        if (p.isSneaking()) {
            neck -= 0.25f;
            if (!(p instanceof EntityPlayerSP)) neck -= 0.125f;      // other players are lowered further
        }
        PState st = updateState(p);
        float sc = (float) size.getValue().getInput();

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(-headYaw, 0f, 1f, 0f);                 // +z = face
        GlStateManager.translate(0, neck, 0);
        GlStateManager.rotate(pitch, 1f, 0f, 0f);                    // follows the head up / down
        GlStateManager.translate(0, 0.47 + height.getValue().getInput(), 0);   // top of the head
        GlStateManager.rotate((float) tilt.getValue().getInput(), 0f, 0f, 1f);
        GlStateManager.scale(sc, sc, sc);

        // 1. opaque parts
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.depthMask(true);
        for (Part part : hat.parts) {
            pushPart(part, st, time);
            drawSolid(part.mesh, pal);
            GlStateManager.popMatrix();
        }

        // 2. glow, sparkles, spores (additive)
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GlStateManager.depthMask(false);
        float gs = (float) glowStrength.getValue().getInput();
        int idx = 0;
        for (Part part : hat.parts) {
            pushPart(part, st, time);
            if (part.glow && glow.getValue()) {
                for (int i = 0; i < 3; i++) {
                    float s = 1.07f + i * 0.09f;
                    GlStateManager.pushMatrix();
                    GlStateManager.scale(s, s, s);
                    drawFlat(part.mesh, pal[6], pal[7], pal[8], (0.32f - i * 0.1f) * gs);
                    GlStateManager.popMatrix();
                }
            }
            if (sparkles.getValue())
                for (float[] sp : part.sparkles) drawSparkle(sp[0], sp[1], sp[2], idx++, time, pal[6], pal[7], pal[8]);
            GlStateManager.popMatrix();
        }
        if (hat.spores && sparkles.getValue()) drawSpores(time, pal[6], pal[7], pal[8]);
        GlStateManager.depthMask(true);
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.popMatrix();
    }

    private void pushPart(Part part, PState st, float time) {
        boolean an = animate.getValue();
        GlStateManager.pushMatrix();
        float bob = an ? part.bob * MathHelper.sin(time * 2.2f) : 0f;
        GlStateManager.translate(part.px, part.py + bob, part.pz);
        if (part.tilt != 0) GlStateManager.rotate(part.tilt, 1f, 0f, 0f);
        if (an && part.wiggle != 0) GlStateManager.rotate(part.wiggle * MathHelper.sin(time * 3.1f), 0f, 0f, 1f);
        if (an) {
            float spin = part.propeller ? st.prop : (float) ((part.spin * (double) time) % 360.0);
            if (spin != 0) GlStateManager.rotate(spin, 0f, 1f, 0f);
            if (part.pulse) { float s = 1f + 0.08f * MathHelper.sin(time * 4f); GlStateManager.scale(s, s, s); }
        }
    }

    private void drawSolid(Mesh m, float[] pal) {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        for (float[] v : m.v) {
            int s = (int) v[4];
            float r, g, b;
            if (s == 0) { r = pal[0]; g = pal[1]; b = pal[2]; }
            else if (s == 1) { r = pal[3]; g = pal[4]; b = pal[5]; }
            else { r = v[5]; g = v[6]; b = v[7]; }
            float l = v[3];
            wr.pos(v[0], v[1], v[2]).color(c(r * l), c(g * l), c(b * l), 1f).endVertex();
        }
        tess.draw();
    }

    private void drawFlat(Mesh m, float r, float g, float b, float a) {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        for (float[] v : m.v) wr.pos(v[0], v[1], v[2]).color(r, g, b, a).endVertex();
        tess.draw();
    }

    private void drawSparkle(float cx, float cy, float cz, int i, float time, float gr, float gg, float gb) {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        float tw = 0.55f + 0.45f * MathHelper.sin(time * 4f + i * 2.1f);
        float s = 0.07f * tw;
        float[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
                {0.4f, 0.4f, 0}, {-0.4f, 0.4f, 0}, {0.4f, -0.4f, 0}, {-0.4f, -0.4f, 0}};
        GL11.glLineWidth(2f);
        wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        for (float[] d : dirs) {
            wr.pos(cx, cy, cz).color(gr, gg, gb, 0.95f * tw).endVertex();
            wr.pos(cx + d[0] * s, cy + d[1] * s, cz + d[2] * s).color(gr, gg, gb, 0f).endVertex();
        }
        tess.draw();
        GL11.glPointSize(4f);
        wr.begin(GL11.GL_POINTS, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(cx, cy, cz).color(1f, 1f, 1f, 0.9f * tw).endVertex();
        tess.draw();
    }

    /** Spores spiralling up from the mushroom cap. */
    private void drawSpores(float time, float gr, float gg, float gb) {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        final int n = 8;
        final float cycle = 3.2f;
        float[][] pts = new float[n][];
        for (int i = 0; i < n; i++) {
            float u = time / cycle + i / (float) n;
            int seed = (int) Math.floor(u);
            float t = u - seed;
            float a = hash(i, seed) * 6.2832f + t * 1.2f;
            float r = 0.25f + 0.2f * hash(seed, i + 31);
            pts[i] = new float[]{r * MathHelper.cos(a), 0.05f + 0.6f * t, r * MathHelper.sin(a), 0.9f * MathHelper.sin(t * (float) Math.PI)};
        }
        GL11.glPointSize(6f);
        wr.begin(GL11.GL_POINTS, DefaultVertexFormats.POSITION_COLOR);
        for (float[] p : pts) wr.pos(p[0], p[1], p[2]).color(gr, gg, gb, p[3] * 0.6f).endVertex();
        tess.draw();
        GL11.glPointSize(2.5f);
        wr.begin(GL11.GL_POINTS, DefaultVertexFormats.POSITION_COLOR);
        for (float[] p : pts) wr.pos(p[0], p[1], p[2]).color(1f, 1f, 1f, p[3]).endVertex();
        tess.draw();
    }

    private PState updateState(EntityPlayer p) {
        long now = System.nanoTime();
        PState s = states.get(p.getEntityId());
        if (s == null) { s = new PState(); s.last = now; states.put(p.getEntityId(), s); }
        float dt = Math.min(0.1f, (now - s.last) / 1e9f);
        s.last = now;
        double mx = p.posX - p.prevPosX, mz = p.posZ - p.prevPosZ;
        boolean moving = mx * mx + mz * mz > 0.0009;
        boolean air = !p.onGround || p.capabilities.isFlying;
        float target = air ? 1440f : moving ? 900f : 360f;          // propeller degrees per second
        s.speed += (target - s.speed) * Math.min(1f, dt * 2f);
        s.prop = (s.prop + s.speed * dt) % 360f;
        return s;
    }

    // ================= colours =================
    private float[] palette(HatType t, float time) {
        int p, s, g;
        if (style.getValue() == Style.Default) {
            p = t.primary; s = t.secondary; g = t.glow;
        } else {
            int base;
            switch (style.getValue()) {
                case Theme: base = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor() & 0xFFFFFF; break;
                case Rainbow: base = Color.HSBtoRGB((time * 0.08f) % 1f, 0.65f, 1f) & 0xFFFFFF; break;
                default: base = colour.getValue() & 0xFFFFFF;
            }
            boolean lighter = ColorUtils.luminance(t.secondary) > ColorUtils.luminance(t.primary);
            p = base;
            s = ColorUtils.mixRgb(base, lighter ? 0xFFFFFF : 0x101010, 0.6f) & 0xFFFFFF;
            g = ColorUtils.mixRgb(base, 0xFFFFFF, 0.55f) & 0xFFFFFF;
        }
        return new float[]{(p >> 16 & 255) / 255f, (p >> 8 & 255) / 255f, (p & 255) / 255f,
                (s >> 16 & 255) / 255f, (s >> 8 & 255) / 255f, (s & 255) / 255f,
                (g >> 16 & 255) / 255f, (g >> 8 & 255) / 255f, (g & 255) / 255f};
    }

    private static float c(float x) { return x < 0 ? 0 : x > 1 ? 1 : x; }

    // ================= hat construction (once per type) =================
    private Hat hat(HatType t) {
        Hat h = hats.get(t);
        if (h == null) {
            switch (t) {
                case Crown: h = buildCrown(); break;
                case TopHat: h = buildTopHat(); break;
                case Witch: h = buildWitch(); break;
                case Halo: h = buildHalo(); break;
                case Mushroom: h = buildMushroom(); break;
                default: h = buildPropeller();
            }
            hats.put(t, h);
        }
        return h;
    }

    private static Hat buildCrown() {
        Hat h = new Hat();
        Part b = h.part(0, 0, 0);
        Mesh m = b.mesh;
        m.col(0).lathe(new float[][]{{0.255f, 0}, {0.275f, 0}, {0.275f, 0.09f}, {0.255f, 0.09f}, {0.255f, 0}}, 32);   // band
        int[] gems = {0xE0193A, 0x2F6BFF, 0x2ECC71};
        for (int k = 0; k < 8; k++) {
            float a = (float) (Math.PI / 2 + k * Math.PI / 4);
            float[] l = pol(0.268f, a - 0.2f, 0.088f), r = pol(0.268f, a + 0.2f, 0.088f), tip = pol(0.26f, a, 0.21f);
            m.col(0).at(0, 0, 0).tri(l, r, tip);                                       // point
            m.at(tip[0], tip[1] + 0.012f, tip[2]).sphere(0.02f, 8);                    // pearl
            m.at(0, 0, 0);
            b.sparkles.add(new float[]{tip[0], tip[1] + 0.04f, tip[2]});
            if (k != 0) {
                float[] g = pol(0.278f, a, 0.045f);
                m.fix(gems[k % 3]).gem(g[0], g[1], g[2], 0.018f, 0.024f, 0.018f);      // band gems
            }
        }
        m.col(1).lathe(new float[][]{{0.255f, 0.02f}, {0.23f, 0.1f}, {0.16f, 0.155f}, {0, 0.175f}}, 24);   // velvet
        m.col(0).at(0, 0.2f, 0).sphere(0.03f, 10);                                     // orb
        m.at(0, 0, 0);
        m.box(-0.006f, 0.225f, -0.006f, 0.006f, 0.29f, 0.006f);                        // cross
        m.box(-0.025f, 0.255f, -0.006f, 0.025f, 0.267f, 0.006f);

        Part front = h.part(0, 0.045f, 0.285f);                                        // big front gem
        front.glow = true;
        front.pulse = true;
        front.mesh.fix(0xE0193A).gem(0, 0, 0, 0.03f, 0.038f, 0.014f);
        return h;
    }

    private static Hat buildTopHat() {
        Hat h = new Hat();
        Part b = h.part(0, 0, 0);
        Mesh m = b.mesh;
        m.col(0);
        m.warp = p -> { float ax = Math.abs(p[0]) - 0.2f; if (ax > 0) p[1] += 1.3f * ax * ax; };   // brim curled up at the sides
        m.lathe(new float[][]{{0, 0}, {0.37f, 0}, {0.37f, 0.022f}, {0.23f, 0.022f}}, 36);
        m.warp = null;
        m.lathe(new float[][]{{0.225f, 0.02f}, {0.24f, 0.44f}, {0, 0.44f}}, 36);      // body
        m.col(1).lathe(new float[][]{{0.2315f, 0.035f}, {0.2346f, 0.12f}}, 36);        // ribbon
        m.fix(0xF4F1E8).box(0.236f, 0.07f, -0.045f, 0.244f, 0.2f, 0.045f);            // playing card
        m.fix(0xC62828).gem(0.246f, 0.135f, 0f, 0.004f, 0.03f, 0.02f);                 // diamond suit
        b.sparkles.add(new float[]{0.25f, 0.2f, 0.045f});

        Part ears = h.part(0, 0.43f, 0);                                               // the bunny in the hat
        ears.wiggle = 7f;
        ears.mesh.warp = p -> p[0] += p[1] * 0.3f * Math.signum(p[0]);
        for (int sd = -1; sd <= 1; sd += 2) {
            ears.mesh.fix(0xF5F5F5).gem(sd * 0.055f, 0.1f, 0f, 0.028f, 0.11f, 0.016f);
            ears.mesh.fix(0xF4A6B8).gem(sd * 0.055f, 0.1f, 0.01f, 0.014f, 0.08f, 0.01f);
        }
        return h;
    }

    private static Hat buildWitch() {
        Hat h = new Hat();
        Part b = h.part(0, 0, 0);
        Mesh m = b.mesh;
        m.col(0);
        m.warp = p -> {                                                                // wavy brim
            float r = (float) Math.sqrt(p[0] * p[0] + p[2] * p[2]), a = (float) Math.atan2(p[2], p[0]);
            p[1] += 0.03f * (float) Math.sin(3 * a + 0.5f) * (r / 0.5f) * (r / 0.5f);
        };
        m.lathe(new float[][]{{0.15f, 0}, {0.5f, -0.005f}, {0.5f, 0.012f}, {0.15f, 0.02f}}, 48);
        final float H = 0.78f;
        m.warp = p -> {                                                                // tip bent backwards
            float t = Math.max(0f, Math.min(1f, p[1] / H));
            p[2] -= 0.38f * (float) Math.pow(t, 2.2);
            p[1] -= 0.14f * t * t * t;
        };
        int n = 14;
        float[][] cone = new float[n + 1][];
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            float r = 0.245f * (float) Math.pow(1 - t, 0.85);
            if (i >= 3 && i < n) r *= 1 + 0.05f * (float) Math.sin(i * 1.9);         // crumpled
            cone[i] = new float[]{r, 0.01f + (H - 0.01f) * t};
        }
        m.lathe(cone, 32);
        m.col(1).lathe(new float[][]{{0.25f, 0.015f}, {0.228f, 0.105f}}, 32);          // ribbon
        m.fix(0xE8C547);                                                               // buckle
        m.box(-0.04f, 0.08f, 0.238f, 0.04f, 0.09f, 0.25f);
        m.box(-0.04f, 0.03f, 0.238f, 0.04f, 0.04f, 0.25f);
        m.box(-0.04f, 0.03f, 0.238f, -0.03f, 0.09f, 0.25f);
        m.box(0.03f, 0.03f, 0.238f, 0.04f, 0.09f, 0.25f);
        m.warp = null;

        Part star = h.part(0, 0.68f, -0.40f);                                          // star on the tip
        star.glow = true;
        star.spin = 90f;
        star.mesh.fix(0xFFE45C).star(0.06f, 0.025f, 0.015f);
        star.sparkles.add(new float[]{0, 0, 0});
        return h;
    }

    private static Hat buildHalo() {
        Hat h = new Hat();
        Part p = h.part(0, 0.16f, 0);
        p.glow = true;
        p.spin = 40f;
        p.bob = 0.025f;
        p.tilt = -8f;
        p.mesh.col(0).torus(0.24f, 0.022f, 40, 10);
        for (int k = 0; k < 4; k++) p.sparkles.add(pol(0.24f, (float) (k * Math.PI / 2), 0));
        return h;
    }

    private static Hat buildMushroom() {
        Hat h = new Hat();
        h.spores = true;
        Mesh m = h.part(0, 0, 0).mesh;
        m.col(1).lathe(new float[][]{{0.2f, 0f}, {0.42f, -0.05f}}, 40);              // gills
        m.col(0).lathe(dome(0.42f, -0.05f, 0.35f, 10), 40);                           // cap
        float[][] dots = {{20, 0}, {25, 100}, {30, 200}, {22, 290}, {55, 50}, {58, 160}, {60, 250}, {50, 330}, {82, 20}};
        for (float[] d : dots) {
            double f = Math.toRadians(d[0]), t = Math.toRadians(d[1]);
            float x = (float) (0.42 * Math.cos(f) * Math.cos(t)), y = (float) (-0.05 + 0.35 * Math.sin(f)), z = (float) (0.42 * Math.cos(f) * Math.sin(t));
            m.col(1).at(x, y, z).sphere(d[0] > 75 ? 0.035f : 0.045f, 8);              // spots
        }
        m.at(0.07f, 0, -0.05f);                                                        // tiny mushroom
        m.fix(0xF2EEE4).lathe(new float[][]{{0.016f, 0.27f}, {0.014f, 0.35f}}, 10);
        m.col(1).lathe(new float[][]{{0.014f, 0.35f}, {0.055f, 0.345f}}, 16);
        m.col(0).lathe(dome(0.055f, 0.345f, 0.045f, 6), 16);
        m.at(0, 0, 0);
        return h;
    }

    private static Hat buildPropeller() {
        Hat h = new Hat();
        Mesh m = h.part(0, 0, 0).mesh;
        m.lathe(dome(0.29f, 0f, 0.22f, 10), 36, new int[]{0, 1, 0, 1, 0, 1});         // segmented cap
        m.col(1);
        for (int i = 0; i < 12; i++) {                                                 // visor
            float[] in0 = visor(i, 12, false), in1 = visor(i + 1, 12, false);
            float[] out0 = visor(i, 12, true), out1 = visor(i + 1, 12, true);
            m.quad(in0, out0, out1, in1);
        }
        m.at(0, 0.22f, 0).sphere(0.03f, 10);                                           // button
        m.at(0, 0, 0);
        m.fix(0x9E9E9E).lathe(new float[][]{{0.012f, 0.22f}, {0.012f, 0.3f}, {0, 0.3f}}, 8);   // stem

        Part pr = h.part(0, 0.3f, 0);                                                  // propeller
        pr.propeller = true;
        pr.mesh.fix(0xE53935).sphere(0.022f, 8);
        int[] bc = {0xE53935, 0x43A047};
        for (int bl = 0; bl < 2; bl++) {
            float ba = (float) (bl * Math.PI), ca = (float) Math.cos(ba), sa = (float) Math.sin(ba);
            float[][] q = {{0.02f, 0.012f, 0.03f}, {0.2f, 0.012f, 0.04f}, {0.235f, 0f, 0f}, {0.2f, -0.012f, -0.04f}, {0.02f, -0.012f, -0.03f}};
            for (float[] v : q) { float x = v[0], z = v[2]; v[0] = x * ca - z * sa; v[2] = x * sa + z * ca; }
            pr.mesh.fix(bc[bl]);
            pr.mesh.tri(q[0], q[1], q[3]);
            pr.mesh.tri(q[0], q[3], q[4]);
            pr.mesh.tri(q[1], q[2], q[3]);
        }
        return h;
    }

    private static float[] visor(int i, int n, boolean outer) {
        double a = Math.toRadians(25 + 130.0 * i / n);
        float ext = outer ? 0.2f * (float) Math.sin(Math.PI * i / n) : 0f;
        float r = 0.285f + ext;
        return new float[]{(float) (r * Math.cos(a)), 0.01f - 0.12f * ext, (float) (r * Math.sin(a))};
    }

    /** Dome profile, from the rim (R, y0) to the top (0, y0 + H). */
    private static float[][] dome(float R, float y0, float H, int n) {
        float[][] pr = new float[n + 1][];
        for (int j = 0; j <= n; j++) {
            double f = Math.PI / 2 * j / n;
            pr[j] = new float[]{j == n ? 0f : (float) (R * Math.cos(f)), (float) (y0 + H * Math.sin(f))};
        }
        return pr;
    }

    private static float[] pol(float r, float a, float y) {
        return new float[]{(float) (r * Math.cos(a)), y, (float) (r * Math.sin(a))};
    }

    private static float hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535f;
    }
}
