package arsenic.module.impl.world;

import arsenic.module.ModuleTier;
import arsenic.utils.java.MathUtils;
import arsenic.module.property.ShowWhen;
import arsenic.module.property.impl.SliderScale;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.Priorities;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.gui.design.M3;
import arsenic.gui.design.M3Icons;
import arsenic.gui.hud.HudStyle;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.minecraft.ScaffoldUtil;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.scaffoldcore.ScaffoldCore;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.awt.Color;
import java.util.Random;

import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

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
        if(blockData == null) {
            return;
        }
        RenderUtils.renderBlock(blockData.getPosition(), Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor(),
                true, false);
        RenderUtils.renderBlockFace(blockData.getPosition(), blockData.facing, Arsenic.getArsenic().getThemeManager().getCurrentTheme().getBlack(),
                true, true);
    };

    private final java.util.ArrayDeque<Long> recentPlacements = new java.util.ArrayDeque<>();

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
        int blockCount = getBlockCount();
        float alpha = Math.min(1f, animatedScale);

        HudDimensions d = computeHudDimensions();
        if (d == null) return;

        if (blockCount > maxBlockCount)
            maxBlockCount = blockCount;
        animatedBlockCount = interpolate(animatedBlockCount, blockCount, 0.15f);
        float displayCount = MathUtils.clamp(animatedBlockCount, 0, maxBlockCount);
        animatedRingFill = maxBlockCount > 0 ? displayCount / maxBlockCount : 0f;

        float flash = blockFlashIntensity;
        GL11.glPushMatrix();
        applyScaleTransform(d.cx, d.cy);

        int fill = M3.alpha(M3.surfaceContainerHigh(), (int) (alpha * 240));
        if (flash > 0.01f)
            fill = M3.mix(fill, M3.alpha(M3.primaryContainer(), (int) (alpha * 240)), flash * 0.55f);
        HudStyle.card(d.x, d.y, d.x + d.w, d.y + d.h, d.h / 2f, fill,
                M3.alpha(flash > 0.01f ? M3.primary() : M3.outlineVariant(), (int) (alpha * 110)));

        float iconR = 13f;
        float iconCx = d.x + 8f + iconR;
        float iconCy = d.y + 19f;
        if (flash > 0.01f)
            DrawUtils.drawCircle(iconCx, iconCy, iconR + 2f, M3.alpha(M3.primary(), (int) (alpha * 35 * flash)));
        HudStyle.iconBadge(M3Icons.CUBE, iconCx, iconCy, iconR,
                M3.alpha(M3.primary(), (int) (alpha * 235)), M3.alpha(M3.onPrimary(), (int) (alpha * 255)));

        FontRendererExtension<?> label = HudStyle.label();
        FontRendererExtension<?> title = HudStyle.title();
        float textX = iconCx + iconR + 10f;
        label.drawString("Blocks", textX, d.y + 16f,
                M3.alpha(M3.onSurfaceVariant(), (int) (alpha * 240)), label.CENTREY);

        String count = String.valueOf(Math.round(displayCount));
        title.drawString(count, d.x + d.w - 9f - title.getWidth(count), d.y + 16f,
                M3.alpha(M3.primary(), (int) (alpha * 255)), title.CENTREY);

        float barX1 = textX;
        float barX2 = d.x + d.w - 9f;
        HudStyle.gradientProgress(barX1, d.y + 30f, barX2, d.y + 33.5f, 1.75f, animatedRingFill,
                M3.alpha(M3.surfaceContainerHighest(), (int) (alpha * 220)),
                M3.alpha(M3.secondary(), (int) (alpha * 255)),
                M3.alpha(M3.primary(), (int) (alpha * 255)));

        HudStyle.resetColor();
        GL11.glPopMatrix();
    }

    private int interpolateColor(int color1, int color2, float t) {
        int r1 = (color1 >> 16) & 0xFF, g1 = (color1 >> 8) & 0xFF, b1 = color1 & 0xFF;
        int r2 = (color2 >> 16) & 0xFF, g2 = (color2 >> 8) & 0xFF, b2 = color2 & 0xFF;
        int r  = (int)(r1 + (r2 - r1) * t);
        int g  = (int)(g1 + (g2 - g1) * t);
        int b  = (int)(b1 + (b2 - b1) * t);
        return (r << 16) | (g << 8) | b;
    }


    private static class HudDimensions {
        int x, y, w, h;
        float cx, cy;
        float ringRadius, ringPad;
    }

    private HudDimensions computeHudDimensions() {
        if (HudStyle.title() == null)
            return null;

        FontRendererExtension<?> label = HudStyle.label();
        FontRendererExtension<?> title = HudStyle.title();
        int blockCount = getBlockCount();
        String count = String.valueOf(blockCount);
        float icon = 26f;
        float pad = 8f;
        float textW = Math.max(title.getWidth("Blocks") + 24f + title.getWidth(count), 82f);
        int w = (int) Math.ceil(pad + icon + 10f + textW + pad);
        int h = 44;

        ScaledResolution sr = new ScaledResolution(mc);
        int x = blockCounterX != -1 ? blockCounterX : (sr.getScaledWidth() - w) / 2;
        int y = blockCounterY != -1 ? blockCounterY : sr.getScaledHeight() - 40 - h;

        HudDimensions d = new HudDimensions();
        d.x = x; d.y = y; d.w = w; d.h = h;
        d.cx = x + w / 2f; d.cy = y + h / 2f;
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
        float a = ((color >> 24) & 0xFF) / 255f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8)  & 0xFF) / 255f;
        float b = ( color        & 0xFF) / 255f;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(lineWidth);
        GL11.glColor4f(r, g, b, a);

        int segments = 64;
        int start = (int)(startFraction * segments);
        int end   = (int)(endFraction   * segments);

        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int i = start; i <= end; i++) {
            double angle = -Math.PI / 2.0 + (i / (double) segments) * 2.0 * Math.PI;
            GL11.glVertex2f(cx + (float)(Math.cos(angle) * radius),
                    cy + (float)(Math.sin(angle) * radius));
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
        placementTimestamps[placementHead % placementTimestamps.length] = now;
        placementHead++;
        placementCount = Math.min(placementCount + 1, placementTimestamps.length);
        blocksPlacedInSession = Math.min(blocksPlacedInSession + 1, 2304);
        blockFlashIntensity = 1.0f;
    }

    private float computeBps() {
        if (placementCount == 0) return 0f;
        long now     = System.currentTimeMillis();
        long cutoff  = now - BPS_SAMPLE_WINDOW_MS;
        int  inWindow = 0;
        int  total    = Math.min(placementCount, placementTimestamps.length);
        int  start    = placementHead - total;
        if (start < 0) start += placementTimestamps.length;
        for (int i = 0; i < total; i++) {
            int idx = (start + i) % placementTimestamps.length;
            if (placementTimestamps[idx] > cutoff) inWindow++;
        }
        float windowSecs = BPS_SAMPLE_WINDOW_MS / 1000f;
        return inWindow / windowSecs;
    }


    private int getThemeColor() {
        return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
    }

    private float interpolate(float current, float target, float speed) {
        return current + (target - current) * speed;
    }

    private Item keyBlock() {
        if (!ScaffoldUtil.isUsable(mc.thePlayer.inventory.getCurrentItem())) {
            mc.thePlayer.inventory.currentItem = ScaffoldUtil.getBlockSlot();
        }
        if(mc.thePlayer.inventory.getCurrentItem() == null)
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
