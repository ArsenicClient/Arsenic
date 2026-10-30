package arsenic.module.impl.world;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.Priorities;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMovementInput;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventShader;
import arsenic.event.impl.EventSilentRotation;
import arsenic.gui.design.M3;
import arsenic.gui.hud.HudEditorScreen;
import arsenic.gui.hud.HudElement;
import arsenic.gui.hud.HudStyle;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.ShowWhen;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.SliderScale;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.java.MathUtils;
import arsenic.utils.minecraft.ScaffoldUtil;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.scaffoldcore.ScaffoldCore;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.Random;

@ModuleInfo(name = "Scaffold", category = ModuleCategory.MOVEMENT, tier = ModuleTier.LEGIT)
public class Scaffold extends Module {

    public final RangeProperty rotationSpeed = new RangeProperty("Rotation Speed", new RangeValue(1, 360, 180, 360, 1), SliderScale.LOG);
    public BooleanProperty eagle = new BooleanProperty("Eagle", true);
    @ShowWhen(property = "Eagle", is = "true")
    public final DoubleProperty safety = new DoubleProperty("Safety", new DoubleValue(0, 3, 2, 0.1));

    private final ScaffoldCore core = new ScaffoldCore(ScaffoldCore.Tuning.best(), new Random());
    private BlockData blockData;
    private float animatedScale;
    public static int blockCounterX = -1;
    public static int blockCounterY = -1;
    private final HudElement hud = hudElement("Scaffold", -1, -1, 144, 42);
    private float animatedRingFill = 0f;
    private int maxBlockCount = 0;
    private int blocksPlacedInSession = 0;
    private float animatedBlockCount = 0f;
    private static final int BPS_SAMPLE_WINDOW_MS = 3000;
    private final long[] placementTimestamps = new long[512];
    private int placementHead = 0;
    private int placementCount = 0;
    private float blockFlashIntensity = 0f;

    @Override
    protected void onEnable() {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        blockData = null;
        animatedScale = 0f;
        core.reset();
        recentPlacements.clear();
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        animatedScale = 0f;
        setShift(false);
        super.onDisable();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> eventSilentRotationListener = event -> {
        event.setBlockUserInput(true);
        Item item = keyBlock();
        ScaffoldCore.Rotation rotation = core.rotate(input(true, item != null && haveBlocks()), ScaffoldUtil.WORLD);
        event.setSpeed(rotation.speed);
        event.setPreventDuplicateLook(rotation.preventDuplicateLook);
        event.setYaw(rotation.yaw);
        event.setPitch(rotation.pitch);
        updateTarget();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> eventSilentRotationPostListener = event -> {
        Item item = keyBlock();
        ScaffoldCore.Input in = input(true, item instanceof ItemBlock && haveBlocks());
        ScaffoldCore.Action action = core.post(in, ScaffoldUtil.WORLD, event.getYaw(), event.getPitch());
        boolean placed = false;
        if (action.place) {
            place(action);
            placed = true;
        }
        updateTarget();
        setShift(core.sneak(in, ScaffoldUtil.WORLD, placed));
    };

    @RequiresPlayer
    @EventLink(Priorities.HIGH)
    public final Listener<EventMovementInput> eventMovementInputListener = event -> {
        float[] nudge = core.nudge();
        if (nudge == null || (event.getSpeed() == 0 && event.getStrafe() == 0))
            return;
        float scale = Math.max(Math.abs(event.getSpeed()), Math.abs(event.getStrafe()));
        event.setSpeed(nudge[0] * scale);
        event.setStrafe(nudge[1] * scale);
    };

    private ScaffoldCore.Input input(boolean movement, boolean hasBlock) {
        ScaffoldCore.Input in = ScaffoldUtil.coreInput(movement);
        in.hasBlock = hasBlock;
        in.speedMin = rotationSpeed.getValue().getMin();
        in.speedMax = rotationSpeed.getValue().getMax();
        in.eagle = eagle.getValue();
        in.safety = safety.getValue().getInput();
        return in;
    }

    private void updateTarget() {
        ScaffoldCore.Target target = core.target();
        blockData = target == null ? null
                : new BlockData(new BlockPos(target.x, target.y, target.z), EnumFacing.getFront(target.face));
    }

    private void setShift(boolean shift) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(),
                shift || (mc.currentScreen == null && Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())));
    }

    private void place(ScaffoldCore.Action action) {
        mc.playerController.onPlayerRightClick(
                mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem(),
                new BlockPos(action.x, action.y, action.z), EnumFacing.getFront(action.face),
                new Vec3(action.hitX, action.hitY, action.hitZ)
        );
        mc.thePlayer.swingItem();
        recentPlacements.addLast(System.currentTimeMillis());
        recordPlacement();
    }

    @EventLink
    public final Listener<EventRenderWorldLast> renderWorldLast = event -> {
        if (blockData == null) {
            return;
        }
        RenderUtils.renderBlock(blockData.getPosition(), Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor(),
                true, false);
        RenderUtils.renderBlockFace(blockData.getPosition(), blockData.facing, M3.alpha(M3.primaryContainer(), 160),
                true, true);
    };

    @EventLink
    public final Listener<EventRender2D> onRender2D = event -> {
        int blockCount = getBlockCount();
        boolean inEditor = mc.currentScreen instanceof HudEditorScreen;
        if ((isEnabled() && blockCount > 0) || inEditor) {
            animatedScale = interpolate(animatedScale, 1.0f, 0.14f);
        } else {
            animatedScale = interpolate(animatedScale, 0.0f, 0.16f);
        }
        blockFlashIntensity = interpolate(blockFlashIntensity, 0.0f, 0.14f);

        if (animatedScale <= 0.01f) return;

        drawBlockCounter();
    };

    @EventLink
    public final Listener<EventShader.Blur> blurListener = event -> {
        if (animatedScale <= 0.01f) return;
        HudDimensions d = computeHudDimensions();
        if (d == null) return;

        GL11.glPushMatrix();
        applyScaleTransform(d.cx, d.cy);
        DrawUtils.drawRoundedRect(d.x, d.y, d.x + d.w, d.y + d.h, HudStyle.RADIUS_CARD, 0xFFFFFFFF);
        GL11.glPopMatrix();
    };

    private final ArrayDeque<Long> recentPlacements = new ArrayDeque<>();

    private boolean haveBlocks() {
        long now = System.currentTimeMillis();
        int ping = arsenic.utils.lag.LagManager.getPing();
        long window = (ping > 0 ? ping : 100) + 60;
        while (!recentPlacements.isEmpty() && now - recentPlacements.peekFirst() > window)
            recentPlacements.pollFirst();
        return getBlockCount() - recentPlacements.size() > 0;
    }

    private int getBlockCount() {
        if (mc.thePlayer == null) return 0;
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
            if (stack != null && stack.getItem() instanceof ItemBlock && stack.stackSize > 0) {
                count += stack.stackSize;
            }
        }
        return count;
    }

    private void drawBlockCounter() {
        FontRendererExtension<?> bodyFr = HudStyle.body();
        FontRendererExtension<?> labelFr = HudStyle.label();
        if (bodyFr == null || labelFr == null) return;

        int blockCount = getBlockCount();
        float alpha = MathUtils.clamp01(animatedScale);

        HudDimensions d = computeHudDimensions();
        if (d == null) return;

        if (blockCount > maxBlockCount) {
            maxBlockCount = blockCount;
        }
        animatedBlockCount = interpolate(animatedBlockCount, blockCount, 0.16f);
        float displayCount = MathUtils.clamp(animatedBlockCount, 0, Math.max(1, maxBlockCount));
        animatedRingFill = maxBlockCount > 0 ? MathUtils.clamp01(displayCount / maxBlockCount) : 0f;

        GL11.glPushMatrix();
        applyScaleTransform(d.cx, d.cy);

        float x = HudStyle.snap(d.x);
        float y = HudStyle.snap(d.y);
        float w = HudStyle.snap(d.w);
        float h = HudStyle.snap(d.h);
        float pad = 7f;
        float flashI = MathUtils.clamp01(blockFlashIntensity);

        HudStyle.card(x, y, x + w, y + h, HudStyle.RADIUS_CARD, (int) (232 * alpha));
        if (flashI > 0.02f) {
            DrawUtils.drawRoundedOutline(x, y, x + w, y + h, HudStyle.RADIUS_CARD, 1.2f,
                    M3.alpha(M3.primary(), (int) (210 * flashI * alpha)));
        }

        // Left circular progress ring centered vertically at (ringCX, ringCY)
        float ringR = d.ringRadius;
        float ringCX = HudStyle.snap(x + pad + ringR);
        float ringCY = y + h / 2f;

        float fill = animatedRingFill;
        int baseAccent = fill > 0.35f ? M3.primary() : (fill > 0.15f ? M3.tertiary() : M3.error());
        int arcColor = M3.alpha(baseAccent, (int) (255 * alpha));
        int trackColor = M3.alpha(M3.surfaceContainerHighest(), (int) (215 * alpha));

        DrawUtils.drawCircle(ringCX, ringCY, ringR - 1.5f, M3.alpha(M3.surfaceContainerHigh(), (int) (180 * alpha)));
        drawArc(ringCX, ringCY, ringR, 2.4f, 0f, 1f, trackColor);
        if (fill > 0.005f) {
            drawArc(ringCX, ringCY, ringR, 2.4f, 0f, fill, arcColor);
        }

        String countStr = String.valueOf(blockCount);
        int countColor = M3.alpha(M3.onSurface(), (int) (255 * alpha));
        if (flashI > 0.01f) {
            float popScale = 1f + flashI * 0.14f;
            GL11.glPushMatrix();
            GL11.glTranslatef(ringCX, ringCY, 0f);
            GL11.glScalef(popScale, popScale, 1f);
            GL11.glTranslatef(-ringCX, -ringCY, 0f);
            labelFr.drawString(countStr, ringCX, ringCY, countColor, labelFr.CENTREX, labelFr.CENTREY);
            GL11.glPopMatrix();
        } else {
            labelFr.drawString(countStr, ringCX, ringCY, countColor, labelFr.CENTREX, labelFr.CENTREY);
        }

        float colX = HudStyle.snap(ringCX + ringR + 7f);
        float rightX = HudStyle.snap(x + w - pad);

        // Row 1: "Blocks" label on left, percentage pill chip on right
        float row1Cy = y + pad + 5f;
        labelFr.drawString("Blocks", colX, row1Cy, countColor, labelFr.CENTREY);
        String pctStr = Math.round(fill * 100f) + "%";
        HudStyle.rightChip(labelFr, pctStr, rightX, row1Cy, 11f, 4f,
                M3.alpha(M3.primaryContainer(), (int) (220 * alpha)),
                M3.alpha(M3.onPrimaryContainer(), (int) (255 * alpha)));

        // Row 2: BPS & BPM telemetry centered at y + 22f
        float row2Cy = y + 22f;
        float bps = computeBps();
        int bpm = Math.round(bps * 60f);
        String rateLine = String.format("%.1f BPS  ·  %d BPM", bps, bpm);
        bodyFr.setScale(0.85f);
        bodyFr.drawString(rateLine, colX, row2Cy, M3.alpha(M3.onSurfaceVariant(), (int) (235 * alpha)), bodyFr.CENTREY);
        bodyFr.resetScale();

        // Row 3: M3 linear progress bar aligned with bottom padding (y + h - pad)
        float barY2 = y + h - pad;
        float barY1 = barY2 - 4f;
        HudStyle.progressBar(colX, barY1, rightX, barY2, fill, 0f, arcColor, 0, trackColor);

        GL11.glPopMatrix();
        HudStyle.resetGl();
    }

    private static class HudDimensions {
        int x, y, w, h;
        float cx, cy;
        float ringRadius;
    }

    private HudDimensions computeHudDimensions() {
        FontRendererExtension<?> bodyFr = HudStyle.body();
        FontRendererExtension<?> labelFr = HudStyle.label();
        if (bodyFr == null || labelFr == null) return null;

        float bps = computeBps();
        int bpm = Math.round(bps * 60f);
        String bpsLine = String.format("%.1f BPS  ·  %d BPM", bps, bpm);

        float pad = 7f;
        float ringRadius = 13f;
        float ringDiam = ringRadius * 2f;
        float row1W = labelFr.getWidth("Blocks") + 8f + labelFr.getWidth("100%") + 10f;
        float row2W = bodyFr.getWidth(bpsLine) * 0.85f + 4f;
        float colW = Math.max(86f, Math.max(row1W, row2W));

        int w = Math.round(pad + ringDiam + 7f + colW + pad);
        int h = 42;

        ScaledResolution sr = new ScaledResolution(mc);
        if (hud.x == -1 && blockCounterX == -1) {
            hud.x = (sr.getScaledWidth() - w) / 2;
        } else if (blockCounterX != -1 && hud.x == -1) {
            hud.x = blockCounterX;
        }
        if (hud.y == -1 && blockCounterY == -1) {
            hud.y = sr.getScaledHeight() - 48 - h;
        } else if (blockCounterY != -1 && hud.y == -1) {
            hud.y = blockCounterY;
        }
        hud.setSize(w, h);
        blockCounterX = hud.x;
        blockCounterY = hud.y;

        HudDimensions d = new HudDimensions();
        d.x = hud.x;
        d.y = hud.y;
        d.w = w;
        d.h = h;
        d.cx = d.x + w / 2f;
        d.cy = d.y + h / 2f;
        d.ringRadius = ringRadius;
        return d;
    }

    private void applyScaleTransform(float cx, float cy) {
        GL11.glTranslated(cx, cy, 0);
        GL11.glScalef(animatedScale, animatedScale, 1.0f);
        GL11.glTranslated(-cx, -cy, 0);
    }

    private static void drawArc(float cx, float cy, float radius,
                                float lineWidth, float startFraction, float endFraction,
                                int color) {
        float a = ((color >>> 24) & 0xFF) / 255f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(lineWidth * Math.max(1f, HudStyle.scaleFactor() * 0.5f));
        GL11.glColor4f(r, g, b, a);

        int segments = 64;
        int start = (int) (startFraction * segments);
        int end = (int) (endFraction * segments);

        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int i = start; i <= end; i++) {
            double angle = -Math.PI / 2.0 + (i / (double) segments) * 2.0 * Math.PI;
            GL11.glVertex2f(cx + (float) (Math.cos(angle) * radius),
                    cy + (float) (Math.sin(angle) * radius));
        }
        GL11.glEnd();

        GL11.glLineWidth(1f);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPopAttrib();

        RenderUtils.resetColor();
    }

    private void recordPlacement() {
        long now = System.currentTimeMillis();
        placementTimestamps[placementTimestamps.length > 0 ? placementHead % placementTimestamps.length : 0] = now;
        placementHead++;
        placementCount = Math.min(placementCount + 1, placementTimestamps.length);
        blocksPlacedInSession = Math.min(blocksPlacedInSession + 1, 2304);
        blockFlashIntensity = 1.0f;
    }

    private float computeBps() {
        if (placementCount == 0) return 0f;
        long now = System.currentTimeMillis();
        long cutoff = now - BPS_SAMPLE_WINDOW_MS;
        int inWindow = 0;
        int total = Math.min(placementCount, placementTimestamps.length);
        int start = placementHead - total;
        if (start < 0) start += placementTimestamps.length;
        for (int i = 0; i < total; i++) {
            int idx = (start + i) % placementTimestamps.length;
            if (placementTimestamps[idx] > cutoff) inWindow++;
        }
        float windowSecs = BPS_SAMPLE_WINDOW_MS / 1000f;
        return inWindow / windowSecs;
    }

    private float interpolate(float current, float target, float speed) {
        return current + (target - current) * speed;
    }

    private Item keyBlock() {
        if (!ScaffoldUtil.isUsable(mc.thePlayer.inventory.getCurrentItem())) {
            mc.thePlayer.inventory.currentItem = ScaffoldUtil.getBlockSlot();
        }
        if (mc.thePlayer.inventory.getCurrentItem() == null)
            return null;
        return mc.thePlayer.inventory.getCurrentItem().getItem();
    }

    public static float[] getRotationsForFace(BlockPos blockPos, EnumFacing facing, float lockedYaw) {
        return ScaffoldCore.rotationsForFace(ScaffoldUtil.coreInput(false), blockPos.getX(), blockPos.getY(), blockPos.getZ(),
                facing.getIndex(), lockedYaw);
    }

    public static float[] getFreeRotationsForFace(BlockPos blockPos, EnumFacing facing) {
        return ScaffoldCore.freeRotationsForFace(ScaffoldUtil.coreInput(false), blockPos.getX(), blockPos.getY(), blockPos.getZ(),
                facing.getIndex());
    }

    public static class BlockData {
        private BlockPos position;
        private EnumFacing facing;

        public BlockData(final BlockPos position, final EnumFacing facing) {
            this.position = position;
            this.facing = facing;
        }

        public EnumFacing getFacing() { return facing; }
        public BlockPos getPosition() { return position; }
    }
}
