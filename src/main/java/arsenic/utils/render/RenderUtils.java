package arsenic.utils.render;

import arsenic.utils.java.UtilityClass;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;

/**
 * Colour helpers plus world-space drawing.
 * <p>
 * World drawing goes through vanilla's gizmo system (the same thing debug renderers use), so it
 * only works while a gizmo collector is open - that is, inside an {@code EventRenderWorldLast}
 * listener. Gizmo coordinates are absolute world positions; there is no camera offset to subtract
 * like there was with 1.8's {@code viewerPosX}.
 */
public class RenderUtils extends UtilityClass {

    // ---------------------------------------------------------------
    //  Colour
    // ---------------------------------------------------------------

    public static int alpha(Color color, int newAlpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), newAlpha).getRGB();
    }

    public static int withAlpha(int color, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    public static Color interpolateColoursColor(Color a, Color b, float f) {
        float rf = 1 - f;
        int red = (int) (a.getRed() * rf + b.getRed() * f);
        int green = (int) (a.getGreen() * rf + b.getGreen() * f);
        int blue = (int) (a.getBlue() * rf + b.getBlue() * f);
        int alpha = (int) (a.getAlpha() * rf + b.getAlpha() * f);
        return new Color(red, green, blue, alpha);
    }

    public static int interpolateColours(Color a, Color b, float f) {
        return interpolateColoursColor(a, b, f).getRGB();
    }

    public static int interpolateColoursInt(int a, int b, float f) {
        f = Mth.clamp(f, 0f, 1f);
        float rf = 1 - f;
        int alpha = (int) (((a >>> 24) & 0xFF) * rf + ((b >>> 24) & 0xFF) * f);
        int red = (int) (((a >> 16) & 0xFF) * rf + ((b >> 16) & 0xFF) * f);
        int green = (int) (((a >> 8) & 0xFF) * rf + ((b >> 8) & 0xFF) * f);
        int blue = (int) ((a & 0xFF) * rf + (b & 0xFF) * f);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    /** Textures ship in the jar's assets, so they are addressed by id rather than loaded by hand. */
    public static Identifier getResourcePath(String path) {
        String trimmed = path.startsWith("/") ? path.substring(1) : path;
        if (trimmed.startsWith("assets/arsenic/"))
            trimmed = trimmed.substring("assets/arsenic/".length());
        return Identifier.fromNamespaceAndPath("arsenic", trimmed);
    }

    // ---------------------------------------------------------------
    //  World space (gizmos)
    // ---------------------------------------------------------------

    public static float partialTicks() {
        return mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    public static Vec3 interpolatedPosition(Entity entity) {
        return entity.getPosition(partialTicks());
    }

    public static AABB interpolatedBox(Entity entity) {
        Vec3 offset = interpolatedPosition(entity).subtract(entity.position());
        return entity.getBoundingBox().move(offset);
    }

    public static void renderBlock(BlockPos blockPos, int color, boolean outline, boolean shade) {
        renderBox(new AABB(blockPos), color, outline, shade);
    }

    public static void renderBox(AABB box, int color, boolean outline, boolean shade) {
        GizmoStyle style;
        int fill = withAlpha(color, Math.max(1, ((color >>> 24) & 0xFF) / 3));
        if (outline && shade)
            style = GizmoStyle.strokeAndFill(opaque(color), 2f, fill);
        else if (shade)
            style = GizmoStyle.fill(fill);
        else
            style = GizmoStyle.stroke(opaque(color), 2f);
        Gizmos.cuboid(box, style).setAlwaysOnTop();
    }

    public static void renderBlockFace(BlockPos blockPos, Direction facing, int color, boolean outline, boolean shade) {
        AABB block = new AABB(blockPos);
        Vec3 min = new Vec3(block.minX, block.minY, block.minZ);
        Vec3 max = new Vec3(block.maxX, block.maxY, block.maxZ);
        GizmoStyle style = outline && !shade ? GizmoStyle.stroke(opaque(color), 2f) : GizmoStyle.fill(color);
        Gizmos.rect(min, max, facing, style).setAlwaysOnTop();
    }

    public static void drawBoundingBox(AABB box, int color) {
        Gizmos.cuboid(box, GizmoStyle.stroke(opaque(color), 1.5f)).setAlwaysOnTop();
    }

    public static void drawShadedBoundingBox(AABB box, int r, int g, int b, int a) {
        Gizmos.cuboid(box, GizmoStyle.fill((a << 24) | (r << 16) | (g << 8) | b)).setAlwaysOnTop();
    }

    public static void drawLine(Vec3 from, Vec3 to, int color, float width) {
        Gizmos.line(from, to, color, width).setAlwaysOnTop();
    }

    /** A filled quad through four corners, in order round its edge. */
    public static void drawQuad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
        Gizmos.rect(a, b, c, d, GizmoStyle.fill(color)).setAlwaysOnTop();
    }

    /** A triangle (a quad with its last corner doubled). */
    public static void drawTriangle(Vec3 a, Vec3 b, Vec3 c, int color) {
        Gizmos.rect(a, b, c, c, GizmoStyle.fill(color)).setAlwaysOnTop();
    }

    /** A flat ring lying on the ground (in the x/z plane) round {@code centre}. */
    public static void drawFlatCircle(Vec3 centre, double radius, int color, float width) {
        int segments = Math.max(16, (int) (radius * 24));
        Vec3 prev = null;
        for (int i = 0; i <= segments; i++) {
            double angle = i * Math.PI * 2 / segments;
            Vec3 point = centre.add(radius * Math.cos(angle), 0, radius * Math.sin(angle));
            if (prev != null)
                drawLine(prev, point, color, width);
            prev = point;
        }
    }

    /** A filled disc lying on the ground round {@code centre}. */
    public static void drawFilledCircle(Vec3 centre, double radius, int color) {
        int segments = Math.max(16, (int) (radius * 24));
        Vec3 prev = null;
        for (int i = 0; i <= segments; i++) {
            double angle = i * Math.PI * 2 / segments;
            Vec3 point = centre.add(radius * Math.cos(angle), 0, radius * Math.sin(angle));
            if (prev != null)
                drawTriangle(centre, prev, point, color);
            prev = point;
        }
    }

    /** A dot of {@code size} pixels. */
    public static void drawPoint(Vec3 pos, int color, float size) {
        Gizmos.point(pos, color, size).setAlwaysOnTop();
    }

    /** Text floating at {@code pos}, always facing the camera. {@code scale} 1 is the size of a nametag. */
    public static void drawWorldText(Vec3 pos, String text, int color, float scale) {
        Gizmos.billboardText(text, pos, net.minecraft.gizmos.TextGizmo.Style.forColorAndCentered(opaque(color))
                .withScale(net.minecraft.gizmos.TextGizmo.Style.DEFAULT_SCALE * scale)).setAlwaysOnTop();
    }

    /** The camera's position, which world-space effects that face the viewer need. */
    public static Vec3 cameraPosition() {
        return mc.gameRenderer.mainCamera().position();
    }

    public static void drawLineToEntity(Entity e, int color, float width) {
        if (e == null || mc.player == null)
            return;
        Vec3 target = interpolatedPosition(e).add(0, e.getEyeHeight(), 0);
        Vec3 eyes = mc.gameRenderer.mainCamera().position();
        // start just in front of the camera so the line reads as coming from the crosshair
        Vec3 look = Vec3.directionFromRotation(mc.player.getXRot(), mc.player.getYRot());
        drawLine(eyes.add(look.scale(0.2)), target, color, width);
    }

    public static double ticks = 0;
    public static long lastFrame = 0;

    /** The bobbing ring drawn around a target - a solid band fading to transparent, plus a rim. */
    public static void drawCircle(Entity entity, double rad, int colored, float alpha) {
        long now = System.currentTimeMillis();
        if (lastFrame != 0)
            ticks += .004 * (now - lastFrame);
        lastFrame = now;

        Vec3 pos = interpolatedPosition(entity);
        double y = pos.y + Math.sin(ticks) + 1;
        double tail = y - Math.sin(ticks + 1) / 2.7f;
        int band = withAlpha(colored, (int) (.52f * alpha * 255));
        int rim = withAlpha(colored, (int) (.5f * alpha * 255));

        int segments = 48;
        Vec3 prevTop = null, prevBottom = null;
        for (int seg = 0; seg <= segments; seg++) {
            double angle = seg * (Math.PI * 2) / segments;
            double x = pos.x + rad * Math.cos(angle);
            double z = pos.z + rad * Math.sin(angle);
            Vec3 top = new Vec3(x, y, z);
            Vec3 bottom = new Vec3(x, tail, z);
            if (prevTop != null) {
                Gizmos.rect(prevTop, top, bottom, prevBottom, GizmoStyle.fill(withAlpha(band, ((band >>> 24) & 0xFF) / 2)));
                Gizmos.line(prevTop, top, rim, 1.5f);
            }
            prevTop = top;
            prevBottom = bottom;
        }
    }

    /** Same as {@link #drawCircle(Entity, double, int, float)}; the position is interpolated by the gizmo pass already. */
    public static void drawCircle(Entity entity, float partialTicks, double rad, int colored, float alpha) {
        drawRing(entity, partialTicks, rad, colored, alpha, TargetFx.Style.CLASSIC);
    }

    private static final TargetFx.Target fxTarget = new TargetFx.Target();
    private static final long fxStart = System.nanoTime();

    /** Draws one of the {@link TargetFx} styles round the entity. Call from an EventRenderWorldLast listener. */
    public static void drawRing(Entity entity, float partialTicks, double rad, int colored, float alpha, TargetFx.Style style) {
        // callers pass either 0-255 or 0-1
        alpha = alpha > 1f ? alpha / 255f : alpha;

        long now = System.currentTimeMillis();
        if (consumeHit(entity))
            lastHitMs = now;

        TargetFx.Target t = fxTarget;
        Vec3 pos = entity.getPosition(partialTicks);
        t.x = pos.x;
        t.y = pos.y;
        t.z = pos.z;
        t.height = entity.getBbHeight();
        t.radius = rad;
        float health = 1f;
        t.absorption = 0f;
        t.maxHealth = 20f;
        if (entity instanceof LivingEntity living) {
            t.maxHealth = Math.max(1f, living.getMaxHealth());
            health = Mth.clamp(living.getHealth() / t.maxHealth, 0f, 1f);
            t.absorption = Mth.clamp(living.getAbsorptionAmount() / t.maxHealth, 0f, 1f);
        }
        trackHealth(health, now);
        t.health = shownHealth;
        t.healthLag = lagHealth;
        t.hit = now - lastHitMs < 500 ? 1f - (now - lastHitMs) / 500f : 0f;
        t.color = colored;
        t.alpha = alpha;

        Vec3 cam = mc.gameRenderer.mainCamera().position();
        t.dirX = cam.x - t.x;
        t.dirZ = cam.z - t.z;

        TargetFx.render(style, t, (System.nanoTime() - fxStart) / 1e9);
    }

    // True once for each new hit on this entity. hurtTime jumps back up to its maximum on a hit, so a rise
    // since the last frame means a new one.
    private static int hurtTrackedId = -1;
    private static int lastHurtTime;
    private static long lastHitMs;

    // The health bar slides to the real health rather than jumping, and what was just lost lingers for a moment before
    // draining away after it. Reset with the hit tracking when the target changes.
    private static float shownHealth = 1f, lagHealth = 1f;
    private static long lastHealthMs, lagHoldUntil;
    private static int lastHealthId = -1;

    private static void trackHealth(float health, long now) {
        if (hurtTrackedId != lastHealthId) {
            lastHealthId = hurtTrackedId;
            shownHealth = lagHealth = health;
            lastHealthMs = now;
        }
        float dt = Math.min(0.1f, (now - lastHealthMs) / 1000f);
        lastHealthMs = now;
        if (health < shownHealth - 0.001f)
            lagHoldUntil = now + 450;
        shownHealth += (health - shownHealth) * Math.min(1f, dt * 14f);
        if (lagHealth < shownHealth)
            lagHealth = shownHealth;
        else if (now > lagHoldUntil)
            lagHealth = Math.max(shownHealth, lagHealth - dt * 0.7f);
    }

    private static boolean consumeHit(Entity entity) {
        if (entity.getId() != hurtTrackedId) {
            hurtTrackedId = entity.getId();
            lastHurtTime = 0;
        }
        int hurt = entity instanceof LivingEntity living ? living.hurtTime : 0;
        boolean hit = hurt > lastHurtTime;
        lastHurtTime = hurt;
        return hit;
    }

    private static int opaque(int color) {
        return ((color >>> 24) & 0xFF) == 0 ? color | 0xFF000000 : color;
    }

    public static final float PI2 = roundToFloat((Math.PI * 2D));

    public static float roundToFloat(double d) {
        return (float) ((double) Math.round(d * 1.0E8D) / 1.0E8D);
    }

    public static Double interpolate(double oldValue, double newValue, double interpolationValue) {
        return (oldValue + (newValue - oldValue) * interpolationValue);
    }
}
