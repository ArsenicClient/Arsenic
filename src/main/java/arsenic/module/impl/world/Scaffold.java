package arsenic.module.impl.world;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
import arsenic.injection.accessor.IMixinEntity;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.minecraft.ScaffoldUtil;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.lag.LagManager;
import arsenic.utils.render.RenderUtils;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.*;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import static arsenic.utils.minecraft.ScaffoldUtil.willFallNextTick;
import static arsenic.utils.rotations.RotationUtils.patchGCD;

@ModuleInfo(name = "Scaffold", category = ModuleCategory.PLAYER)
public class Scaffold extends Module {

    public enum EagleMode { Off, Normal, Silent }
    public enum RotationMode { Normal, Stabilized, ReverseYaw }
    public enum ZitterMode { Off, Teleport, Smooth }

    // ── Properties ────────────────────────────────────────────────────────────

    public BooleanProperty sprint = new BooleanProperty("Sprint", false) {
        @Override
        public Boolean getValue() {
            return super.getValue() || telly.getValue();
        }
        @Override
        public void setValue(Boolean value) {
            if (!value) telly.setValue(false);
            super.setValue(value);
        }
    };

    public BooleanProperty keepY = new BooleanProperty("KeepY", false) {
        @Override
        public Boolean getValue() {
            return super.getValue() || telly.getValue();
        }
        @Override
        public void setValue(Boolean value) {
            if (!value) telly.setValue(false);
            super.setValue(value);
        }
    };

    public BooleanProperty telly = new BooleanProperty("Telly", false);

    @PropertyInfo(reliesOn = "Telly", value = "true")
    public DoubleProperty mY = new DoubleProperty("", new DoubleValue(-0.5, 0.5, 0, 0.01) {
        @Override
        public double getInput() {
            return telly.getValue() ? super.getInput() : 0.3;
        }
    });

    // Eagle
    public final EnumProperty<EagleMode> eagle = new EnumProperty<>("Eagle", EagleMode.Off);
    public final DoubleProperty edgeDistance = new DoubleProperty("EdgeDistance", new DoubleValue(0, 0.5, 0, 0.05) {
        @Override
        public double getInput() { return super.getInput(); }
    });

    // Rotation mode
    public final EnumProperty<RotationMode> rotationMode = new EnumProperty<>("RotationMode", RotationMode.Normal);

    // Zitter
    public final EnumProperty<ZitterMode> zitterMode = new EnumProperty<>("Zitter", ZitterMode.Off);
    public final DoubleProperty zitterSpeed = new DoubleProperty("ZitterSpeed", new DoubleValue(0.1, 0.3, 0.13, 0.01) {
        @Override
        public double getInput() { return zitterMode.getValue() == ZitterMode.Teleport ? super.getInput() : 0.13; }
    });
    public final DoubleProperty zitterStrength = new DoubleProperty("ZitterStrength", new DoubleValue(0, 0.2, 0.05, 0.01) {
        @Override
        public double getInput() { return zitterMode.getValue() == ZitterMode.Teleport ? super.getInput() : 0.05; }
    });

    // ── State ─────────────────────────────────────────────────────────────────

    private BlockData blockData;
    private float[] rots = new float[2];
    private boolean solvedRots;
    private float animatedScale;

    // Block counter HUD position (draggable via HUD editor)
    public static int blockCounterX = -1;
    public static int blockCounterY = -1;

    // Ring animation
    private float animatedRingFill = 0f;

    // Smoothed block count for buttery interpolation
    private float animatedBlockCount = 0f;

    // Highest block count held this session (used as 100% for ring/bar fill)
    private int maxBlockCount = 0;

    // Total blocks placed this session
    private int blocksPlacedInSession = 0;

    // BPS / BPM tracking
    private static final int BPS_SAMPLE_WINDOW_MS = 3000;
    private final long[] placementTimestamps = new long[512];
    private int placementHead = 0;
    private int placementCount = 0;

    // Block placement glow flash
    private float blockFlashIntensity = 0f;
    private long lastPlacementTime = 0L;
    private static final float FLASH_DECAY_MS = 300f;

    private static final ItemBlock PLACEHOLDER_BLOCK = new ItemBlock(Blocks.tnt);

    private static final int MAX_BLOCK_DISPLAY = 2304;

    // Eagle state
    private boolean eagleSneaking = false;

    // Zitter state
    private boolean zitterDirection = false;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onEnable() {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        blockData = null;
        animatedScale = 0f;
        animatedRingFill = 0f;
        animatedBlockCount = 0f;
        blockFlashIntensity = 0f;
        placementCount = 0;
        blocksPlacedInSession = 0;
        maxBlockCount = 0;
        eagleSneaking = false;
        zitterDirection = false;
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        super.onDisable();
        if (eagleSneaking) {
            LagManager.sendPacket(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SNEAKING));
            eagleSneaking = false;
        }
        if (!Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
        }
    }

    // ── Movement input (Eagle + Zitter) ───────────────────────────────────────

    @RequiresPlayer
    @EventLink
    public final Listener<EventMovementInput> eventMovementInputListener = event -> {
        handleEagle(event);
        handleZitter(event);
    };

    private void handleEagle(EventMovementInput event) {
        if (eagle.getValue() == EagleMode.Off) return;

        EntityPlayerSP player = mc.thePlayer;
        BlockPos below = new BlockPos(player.posX, player.posY - 1, player.posZ);
        boolean isReplaceable = mc.theWorld.getBlockState(below).getBlock() instanceof BlockAir;

        float dif = 0.5f;
        for (EnumFacing side : EnumFacing.HORIZONTALS) {
            BlockPos neighbor = below.offset(side);
            if (mc.theWorld.getBlockState(neighbor).getBlock() instanceof BlockAir) {
                float calcDif = (float)(Math.abs((side.getAxis() == EnumFacing.Axis.Z ? neighbor.getZ() + 0.5 - player.posZ : neighbor.getX() + 0.5 - player.posX)) - 0.5);
                if (calcDif < dif) dif = calcDif;
            }
        }

        boolean shouldEagle = isReplaceable || dif < edgeDistance.getValue().getInput();

        if (eagle.getValue() == EagleMode.Silent) {
            if (eagleSneaking != shouldEagle) {
                LagManager.sendPacket(new C0BPacketEntityAction(player, shouldEagle
                        ? C0BPacketEntityAction.Action.START_SNEAKING
                        : C0BPacketEntityAction.Action.STOP_SNEAKING));
                eagleSneaking = shouldEagle;
            }
        } else {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), shouldEagle);
            eagleSneaking = shouldEagle;
        }
    }

    private void handleZitter(EventMovementInput event) {
        if (zitterMode.getValue() == ZitterMode.Off) return;

        EntityPlayerSP player = mc.thePlayer;

        switch (zitterMode.getValue()) {
            case Smooth: {
                if (player.onGround) {
                    if (event.isJumping() || mc.gameSettings.keyBindJump.isKeyDown()) {
                        event.setStrafe(zitterDirection ? 1f : -1f);
                        zitterDirection = !zitterDirection;
                    } else {
                        event.setStrafe(zitterDirection ? -1f : 1f);
                        zitterDirection = !zitterDirection;
                    }
                }
                break;
            }
            case Teleport: {
                float yawRad = (float) Math.toRadians(player.rotationYaw + (zitterDirection ? 90 : -90));
                double sin = Math.sin(yawRad);
                double cos = Math.cos(yawRad);
                player.motionX -= sin * zitterStrength.getValue().getInput();
                player.motionZ += cos * zitterStrength.getValue().getInput();
                zitterDirection = !zitterDirection;
                break;
            }
        }
    }

    // ── Rotation / placement logic ──────────────────────────────────────────

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> eventSilentRotationListener = event -> {
        boolean wilLFall = ScaffoldUtil.willFallNextTick() && mc.thePlayer.motionY < mY.getValue().getInput();
        blockData = findBestPlacement();
        Item item = keyBlock();
        event.setSpeed(360f);
        event.setPreventDuplicateLook(true);

        if (telly.getValue()) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), mc.gameSettings.keyBindJump.isKeyDown());
        }

        float delta = sprint.getValue() ? 0f : 180f;
        event.setYaw(mc.thePlayer.rotationYaw + delta);
        event.setPitch(rots[1]);

        if (item == null) return;

        if (wilLFall && (!telly.getValue() || !mc.thePlayer.onGround)) {
            if (blockData != null) {
                float[] solved = getRotationsForFace(blockData.getPosition(), blockData.getFacing());
                if (solved != null) {
                    rots = solved;
                    solvedRots = true;
                } else {
                    solvedRots = false;
                    rots = getFreeRotationsForFace(blockData.getPosition(), blockData.getFacing());
                }
            }
            event.setYaw(solvedRots ? mc.thePlayer.rotationYaw + 180f : rots[0]);
            event.setPitch(rots[1]);
        } else if (wilLFall && telly.getValue() && mc.thePlayer.onGround) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
        } else {
            event.setYaw(mc.thePlayer.rotationYaw + delta);
            event.setPitch(rots[1]);
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> eventSilentRotationPostListener = event -> {
        if (keyBlock() == null || blockData == null) return;

        Item item = keyBlock();
        MovingObjectPosition mop = rayTracePost(event);

        if (item instanceof ItemBlock) {
            ItemBlock itemBlock = (ItemBlock) item;
            if (mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                    && (mop.sideHit != EnumFacing.DOWN)
                    && (!keepY.getValue() || mop.sideHit != EnumFacing.UP)
                    && itemBlock.canPlaceBlockOnSide(mc.theWorld, mop.getBlockPos(), mop.sideHit, mc.thePlayer, mc.thePlayer.getHeldItem())) {
                blockData = new BlockData(mop.getBlockPos(), mop.sideHit);
                placePost(event);
            }
        }
    };

    // ── World render ───────────────────────────────────────────────────────────

    @EventLink
    public final Listener<EventRenderWorldLast> renderWorldLast = event -> {
        if (blockData == null) return;
        RenderUtils.renderBlock(blockData.getPosition(),
                Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor(),
                true, false);
        RenderUtils.renderBlockFace(blockData.getPosition(), blockData.facing,
                Arsenic.getArsenic().getThemeManager().getCurrentTheme().getBlack(),
                true, true);
    };

    // ── 2D HUD render ─────────────────────────────────────────────────────────

    @EventLink
    public final Listener<EventRender2D> onRender2D = event -> {
        int blockCount = getBlockCount();

        if (isEnabled() && blockCount > 0) {
            animatedScale = interpolate(animatedScale, 1.0f, 0.10f);
        } else {
            animatedScale = interpolate(animatedScale, 0.0f, 0.15f);
        }

        if (animatedScale <= 0.01f) return;

        long now = System.currentTimeMillis();
        long timeSincePlace = now - lastPlacementTime;
        blockFlashIntensity = Math.max(0f, 1.0f - (timeSincePlace / FLASH_DECAY_MS));

        drawBlockCounter();
    };

    @EventLink
    public final Listener<EventShader.Blur> blurListener = event -> {
        if (animatedScale <= 0.01f) return;

        HudDimensions d = computeHudDimensions();
        if (d == null) return;

        GL11.glPushMatrix();
        applyScaleTransform(d.cx, d.cy);
        Gui.drawRect(d.x, d.y, d.x + d.w, d.y + d.h, -1);
        GL11.glPopMatrix();
    };

    // ── HUD drawing ───────────────────────────────────────────────────────────

    private void drawBlockCounter() {
        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();
        if (fr == null) return;

        int blockCount = getBlockCount();
        float alpha = Math.min(1f, animatedScale);

        HudDimensions d = computeHudDimensions();
        if (d == null) return;

        if (blockCount > maxBlockCount) {
            maxBlockCount = blockCount;
        }
        animatedBlockCount = interpolate(animatedBlockCount, blockCount, 0.15f);
        float displayCount = Math.max(0, Math.min(maxBlockCount, animatedBlockCount));
        animatedRingFill = maxBlockCount > 0 ? displayCount / maxBlockCount : 0f;

        GL11.glPushMatrix();
        applyScaleTransform(d.cx, d.cy);

        float flashI = blockFlashIntensity;
        int bgBase = 26;
        int bgR = bgBase, bgG = bgBase, bgB = bgBase;
        if (flashI > 0f) {
            int theme = getThemeColor();
            int tr = (theme >> 16) & 0xFF;
            int tg = (theme >> 8)  & 0xFF;
            int tb =  theme        & 0xFF;
            bgR = (int)(bgBase + (tr - bgBase) * flashI * 0.55f);
            bgG = (int)(bgBase + (tg - bgBase) * flashI * 0.55f);
            bgB = (int)(bgBase + (tb - bgBase) * flashI * 0.55f);
        }
        float pillR = d.h / 2f;
        int bgColor = new Color(bgR, bgG, bgB, (int)(alpha * 140)).getRGB();
        DrawUtils.drawRoundedRect(d.x, d.y, d.x + d.w, d.y + d.h, pillR, bgColor);

        int themeColor = getThemeColor();
        int borderColor = flashI > 0f
                ? ((int)(alpha * 0xFF) << 24) | interpolateColor(themeColor, themeColor, flashI)
                : ((int)(alpha * 0xFF) << 24) | themeColor;
        if (flashI > 0f) {
            int br = Math.min(255, ((themeColor >> 16) & 0xFF) + (int)(flashI * 80));
            int bg = Math.min(255, ((themeColor >> 8)  & 0xFF) + (int)(flashI * 80));
            int bb = Math.min(255, ( themeColor        & 0xFF) + (int)(flashI * 80));
            borderColor = ((int)(alpha * 0xFF) << 24) | (br << 16) | (bg << 8) | bb;
        }
        DrawUtils.drawRoundedOutline(d.x, d.y, d.x + d.w, d.y + d.h, pillR, 1.5f, borderColor);

        float ringCX = d.x + d.ringRadius + d.ringPad;
        float ringCY = d.cy;
        float ringR   = d.ringRadius;

        int ringTrackColor = new Color(255, 255, 255, (int)(alpha * 25)).getRGB();
        drawArc(ringCX, ringCY, ringR, 2.5f, 0f, 1f, ringTrackColor);

        float fill = animatedRingFill;
        int arcColor;
        if (fill > 0.5f) {
            arcColor = borderColor;
        } else if (fill > 0.25f) {
            float t = (0.5f - fill) / 0.25f;
            arcColor = ((int)(alpha * 0xFF) << 24) | interpolateColor(themeColor, 0xFFBE50, t);
        } else {
            float t = Math.min(1f, (0.25f - fill) / 0.25f);
            arcColor = ((int)(alpha * 0xFF) << 24) | interpolateColor(0xFFBE50, 0xFF6E64, t);
        }
        if (flashI > 0f) {
            int ar = Math.min(255, ((arcColor >> 16) & 0xFF) + (int)(flashI * 60));
            int ag = Math.min(255, ((arcColor >> 8)  & 0xFF) + (int)(flashI * 60));
            int ab = Math.min(255, ( arcColor        & 0xFF) + (int)(flashI * 60));
            arcColor = ((int)(alpha * 0xFF) << 24) | (ar << 16) | (ag << 8) | ab;
        }
        if (fill > 0.0001f) {
            drawArc(ringCX, ringCY, ringR, 2.5f, 0f, fill, arcColor);
        }

        String countStr = String.valueOf(blockCount);

        int textColor = ((int)(alpha * 0xFF) << 24) | 0xFFFFFF;
        if (flashI > 0.01f) {
            float popScale = 1f + flashI * 0.20f;
            GL11.glPushMatrix();
            GL11.glTranslatef(ringCX, ringCY, 0f);
            GL11.glScalef(popScale, popScale, 1f);
            GL11.glTranslatef(-ringCX, -ringCY, 0f);
            fr.drawStringWithShadow(countStr, ringCX, ringCY, textColor, fr.CENTREX, fr.CENTREY);
            GL11.glPopMatrix();
        } else {
            fr.drawStringWithShadow(countStr, ringCX, ringCY, textColor, fr.CENTREX, fr.CENTREY);
        }

        float textX  = ringCX + ringR + d.ringPad + 3f;
        float labelY = d.y + 5f;

        String label = "Blocks";
        float labelH = (float) fr.getHeight(label);
        int labelColor = ((int)(alpha * 0xFF) << 24) | 0x999999;
        fr.drawStringWithShadow(label, textX, labelY, labelColor);

        float bps = computeBps();
        String bpsStr  = String.format("%.1f", bps);
        String bpsUnit = " BPS";
        float bpsY = labelY + labelH + 2f;
        int whiteColor = ((int)(alpha * 0xFF) << 24) | 0xFFFFFF;
        int unitColor  = ((int)(alpha * 0xFF) << 24) | themeColor;
        fr.drawStringWithShadow(bpsStr, textX, bpsY, whiteColor);
        fr.drawStringWithShadow(bpsUnit, textX + (float) fr.getWidth(bpsStr), bpsY, unitColor);

        float dividerX = textX + (float) fr.getWidth(bpsStr + bpsUnit) + 4f;
        fr.drawStringWithShadow(" | ", dividerX, bpsY, labelColor);
        float afterDiv = dividerX + (float) fr.getWidth(" | ");

        int bpm = Math.round(bps * 60f);
        String bpmStr  = String.valueOf(bpm);
        String bpmUnit = " BPM";
        fr.drawStringWithShadow(bpmStr, afterDiv, bpsY, new Color(
                255, 255, 255, (int)(alpha * 180)).getRGB());
        fr.drawStringWithShadow(bpmUnit, afterDiv + (float) fr.getWidth(bpmStr), bpsY,
                new Color((themeColor >> 16) & 0xFF, (themeColor >> 8) & 0xFF, themeColor & 0xFF,
                        (int)(alpha * 120)).getRGB());

        float barY  = bpsY + (float) fr.getHeight(bpsStr) + 3f;
        float barW  = d.w - (textX - d.x) - d.ringPad;
        float barH  = 2.5f;
        int barBg   = new Color(255, 255, 255, (int)(alpha * 20)).getRGB();
        int barFill = new Color(
                (arcColor >> 16) & 0xFF,
                (arcColor >> 8)  & 0xFF,
                 arcColor        & 0xFF,
                (int)(alpha * 180)).getRGB();
        DrawUtils.drawRoundedRect(textX, barY, textX + barW, barY + barH, barH / 2f, barBg);
        if (fill > 0.0001f) {
            DrawUtils.drawRoundedRect(textX, barY, textX + barW * fill, barY + barH, barH / 2f, barFill);
        }

        String pctStr = Math.round(fill * 100f) + "%";
        fr.drawStringWithShadow(pctStr, textX + barW - (float) fr.getWidth(pctStr),
                barY + barH + 4f, labelColor);

        GL11.glPopMatrix();
    }

    // ── HUD geometry helpers ──────────────────────────────────────────────────

    private static class HudDimensions {
        int x, y, w, h;
        float cx, cy;
        float ringRadius, ringPad;
    }

    private HudDimensions computeHudDimensions() {
        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();
        if (fr == null) return null;

        int blockCount = getBlockCount();
        String countStr  = String.valueOf(blockCount);
        float bps        = computeBps();
        int   bpm        = Math.round(bps * 60f);
        String bpsLine   = String.format("%.1f BPS | %d BPM", bps, bpm);

        float ringRadius = 14f;
        float ringPad    = 6f;
        float ringDiam   = ringRadius * 2f;
        float textW      = (float) Math.max(fr.getWidth("Blocks"), fr.getWidth(bpsLine));
        float rightPad   = 13f;

        int w = (int)(ringPad + ringDiam + ringPad + textW + rightPad);
        int h = 42;

        ScaledResolution sr = new ScaledResolution(mc);
        int x = blockCounterX != -1 ? blockCounterX : (sr.getScaledWidth() - w) / 2;
        int y = blockCounterY != -1 ? blockCounterY : sr.getScaledHeight() - 42 - h;

        HudDimensions d = new HudDimensions();
        d.x = x; d.y = y; d.w = w; d.h = h;
        d.cx = x + w / 2f; d.cy = y + h / 2f;
        d.ringRadius = ringRadius; d.ringPad = ringPad;
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

    // ── BPS / BPM tracking ────────────────────────────────────────────────────

    private void recordPlacement() {
        long now = System.currentTimeMillis();
        placementTimestamps[placementHead % placementTimestamps.length] = now;
        placementHead++;
        placementCount = Math.min(placementCount + 1, placementTimestamps.length);
        blocksPlacedInSession = Math.min(blocksPlacedInSession + 1, MAX_BLOCK_DISPLAY);

        blockFlashIntensity = 1.0f;
        lastPlacementTime   = now;
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

    // ── Block inventory count ─────────────────────────────────────────────────

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

    // ── Theme / color helpers ─────────────────────────────────────────────────

    private int getThemeColor() {
        return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
    }

    private int interpolateColor(int color1, int color2, float t) {
        int r1 = (color1 >> 16) & 0xFF, g1 = (color1 >> 8) & 0xFF, b1 = color1 & 0xFF;
        int r2 = (color2 >> 16) & 0xFF, g2 = (color2 >> 8) & 0xFF, b2 = color2 & 0xFF;
        int r  = (int)(r1 + (r2 - r1) * t);
        int g  = (int)(g1 + (g2 - g1) * t);
        int b  = (int)(b1 + (b2 - b1) * t);
        return (r << 16) | (g << 8) | b;
    }

    private float interpolate(float current, float target, float speed) {
        return current + (target - current) * speed;
    }

    // ── Placement / raytrace helpers ───────────────────────────────────────────────

    private MovingObjectPosition rayTracePost(EventSilentRotation.Post event) {
        Vec3 vec3  = mc.thePlayer.getPositionEyes(1);
        Vec3 vec31 = ((IMixinEntity) mc.thePlayer).invokeGetVectorForRotation(event.getPitch(), event.getYaw());
        Vec3 vec32 = vec3.addVector(vec31.xCoord * 4.5, vec31.yCoord * 4.5, vec31.zCoord * 4.5);
        return mc.thePlayer.worldObj.rayTraceBlocks(vec3, vec32, false, false, true);
    }

    private Item keyBlock() {
        if (mc.thePlayer.inventory.getCurrentItem() == null
                || !(mc.thePlayer.inventory.getCurrentItem().getItem() instanceof ItemBlock)) {
            mc.thePlayer.inventory.currentItem = ScaffoldUtil.getBlockSlot();
        }
        if (mc.thePlayer.inventory.getCurrentItem() == null) return null;
        return mc.thePlayer.inventory.getCurrentItem().getItem();
    }

    private void placePost(EventSilentRotation.Post event) {
        if (blockData == null) return;

        MovingObjectPosition objectOver = rayTracePost(event);
        BlockPos blockpos = objectOver.getBlockPos();
        if (objectOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || mc.theWorld.getBlockState(blockpos).getBlock().getMaterial() == Material.air) {
            return;
        }

        mc.playerController.onPlayerRightClick(
                mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem(),
                blockData.position, blockData.facing, ScaffoldUtil.getNewVector(blockData));

        mc.thePlayer.swingItem();

        recordPlacement();
    }

    // ── Placement solving ───────────────────────────────────────────

    public BlockData findBestPlacement() {
        EntityPlayerSP player = mc.thePlayer;
        BlockPos playerPos = new BlockPos(player);
        BlockPos scanY = playerPos.down();

        BlockData best = null;
        double bestScore = Double.MAX_VALUE;

        AxisAlignedBB predicted = ScaffoldUtil.getPredictedBoundingBox(1.0);
        double targetX = (predicted.minX + predicted.maxX) * 0.5;
        double targetZ = (predicted.minZ + predicted.maxZ) * 0.5;
        double targetY = scanY.getY() + 0.5;

        double existingScore = Double.MAX_VALUE;

        boolean tower = !player.onGround && !keepY.getValue();
        int lowestLayer = tower ? -1 : 0;

        for (int layer = 0; layer >= lowestLayer; layer--) {
            BlockPos layerPos = scanY.add(0, layer, 0);
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos pos = layerPos.add(x, 0, z);
                    IBlockState state = mc.theWorld.getBlockState(pos);

                    if (state.getBlock() == Blocks.air) continue;
                    if (!state.getBlock().isFullCube()) continue;

                    double exDx = (pos.getX() + 0.5) - targetX;
                    double exDz = (pos.getZ() + 0.5) - targetZ;
                    double exDy = (pos.getY() + 0.5) - targetY;
                    double exScore = exDx * exDx + exDz * exDz + exDy * exDy * 0.25;
                    if (exScore < existingScore) existingScore = exScore;

                    List<EnumFacing> facings = new ArrayList<>(Arrays.asList(EnumFacing.HORIZONTALS));
                    if (tower) facings.add(EnumFacing.UP);

                    for (EnumFacing facing : facings) {
                        if (!PLACEHOLDER_BLOCK.canPlaceBlockOnSide(mc.theWorld, pos, facing, mc.thePlayer, mc.thePlayer.getHeldItem()))
                            continue;

                        BlockPos neighbor = pos.offset(facing);
                        IBlockState neighborState = mc.theWorld.getBlockState(neighbor);
                        if (neighborState.getBlock() != Blocks.air) continue;

                        double nbCX = neighbor.getX() + 0.5;
                        double nbCY = neighbor.getY() + 0.5;
                        double nbCZ = neighbor.getZ() + 0.5;
                        double dx = nbCX - targetX, dz = nbCZ - targetZ, dy = nbCY - targetY;
                        double score = dx * dx + dz * dz + dy * dy * 0.25;

                        if (score >= bestScore) continue;

                        float[] r = getRotationsForFace(pos, facing);
                        if (r == null) r = getFreeRotationsForFace(pos, facing);

                        Vec3 eyeVec  = player.getPositionEyes(1.0f);
                        Vec3 lookDir = ((IMixinEntity) player).invokeGetVectorForRotation(r[1], r[0]);
                        Vec3 traceEnd = eyeVec.addVector(lookDir.xCoord * 4.5, lookDir.yCoord * 4.5, lookDir.zCoord * 4.5);
                        MovingObjectPosition hit = player.worldObj.rayTraceBlocks(eyeVec, traceEnd, false, false, true);

                        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) continue;
                        if (!hit.getBlockPos().equals(pos)) continue;
                        if (hit.sideHit != facing) continue;

                        bestScore = score;
                        best = new BlockData(pos, facing);
                    }
                }
            }
        }

        if (best != null && existingScore <= bestScore) return null;
        return best;
    }

    public float[] getRotationsForFace(BlockPos blockPos, EnumFacing facing) {
        EntityPlayerSP player = mc.thePlayer;

        double eyeX = player.posX;
        double eyeY = player.posY + player.getEyeHeight();
        double eyeZ = player.posZ;

        float lockedYaw = player.rotationYaw + 180f;
        
        // Apply rotation mode
        switch (rotationMode.getValue()) {
            case Stabilized:
                lockedYaw = Math.round(lockedYaw / 45f) * 45f;
                break;
            case ReverseYaw: {
                float roundYaw90 = Math.round(lockedYaw / 90f) * 90f;
                float roundYaw45 = Math.round(lockedYaw / 45f) * 45f;
                float yawDiff = MathHelper.wrapAngleTo180_float(roundYaw90 - lockedYaw);
                lockedYaw = Math.abs(yawDiff) <= 45f ? roundYaw90 : roundYaw45;
                break;
            }
        }

        float yawRad    = (float) Math.toRadians(lockedYaw);
        double hx = -Math.sin(yawRad);
        double hz =  Math.cos(yawRad);

        double bx0 = blockPos.getX(), bx1 = bx0 + 1.0;
        double by0 = blockPos.getY(), by1 = by0 + 1.0;
        double bz0 = blockPos.getZ(), bz1 = bz0 + 1.0;

        float currentPitch = Arsenic.getArsenic().getSilentRotationManager().pitch;
        float bestPitch = Float.MAX_VALUE;
        float bestDiff  = Float.MAX_VALUE;

        switch (facing) {
            case UP: {
                float pitch = pitchToHitPoint(eyeX, eyeY, eyeZ, hx, hz, bx0 + 0.5, by1, bz0 + 0.5);
                if (!Float.isNaN(pitch)) {
                    float diff = Math.abs(MathHelper.wrapAngleTo180_float(pitch - currentPitch));
                    if (diff < bestDiff) { bestDiff = diff; bestPitch = pitch; }
                }
                for (double cx : new double[]{bx0 + 0.1, bx1 - 0.1}) {
                    for (double cz : new double[]{bz0 + 0.1, bz1 - 0.1}) {
                        float p = pitchToHitPoint(eyeX, eyeY, eyeZ, hx, hz, cx, by1, cz);
                        if (!Float.isNaN(p)) {
                            float diff = Math.abs(MathHelper.wrapAngleTo180_float(p - currentPitch));
                            if (diff < bestDiff) { bestDiff = diff; bestPitch = p; }
                        }
                    }
                }
                break;
            }
            case DOWN: {
                float pitch = pitchToHitPoint(eyeX, eyeY, eyeZ, hx, hz, bx0 + 0.5, by0, bz0 + 0.5);
                if (!Float.isNaN(pitch)) {
                    float diff = Math.abs(MathHelper.wrapAngleTo180_float(pitch - currentPitch));
                    if (diff < bestDiff) { bestDiff = diff; bestPitch = pitch; }
                }
                break;
            }
            case NORTH: {
                float[] c = pitchesToHitZPlane(eyeX, eyeY, eyeZ, hx, hz, bz0, bx0, bx1, by0, by1);
                for (float p : c) {
                    if (!Float.isNaN(p)) {
                        float diff = Math.abs(MathHelper.wrapAngleTo180_float(p - currentPitch));
                        if (diff < bestDiff) { bestDiff = diff; bestPitch = p; }
                    }
                }
                break;
            }
            case SOUTH: {
                float[] c = pitchesToHitZPlane(eyeX, eyeY, eyeZ, hx, hz, bz1, bx0, bx1, by0, by1);
                for (float p : c) {
                    if (!Float.isNaN(p)) {
                        float diff = Math.abs(MathHelper.wrapAngleTo180_float(p - currentPitch));
                        if (diff < bestDiff) { bestDiff = diff; bestPitch = p; }
                    }
                }
                break;
            }
            case WEST: {
                float[] c = pitchesToHitXPlane(eyeX, eyeY, eyeZ, hx, hz, bx0, by0, by1, bz0, bz1);
                for (float p : c) {
                    if (!Float.isNaN(p)) {
                        float diff = Math.abs(MathHelper.wrapAngleTo180_float(p - currentPitch));
                        if (diff < bestDiff) { bestDiff = diff; bestPitch = p; }
                    }
                }
                break;
            }
            case EAST: {
                float[] c = pitchesToHitXPlane(eyeX, eyeY, eyeZ, hx, hz, bx1, by0, by1, bz0, bz1);
                for (float p : c) {
                    if (!Float.isNaN(p)) {
                        float diff = Math.abs(MathHelper.wrapAngleTo180_float(p - currentPitch));
                        if (diff < bestDiff) { bestDiff = diff; bestPitch = p; }
                    }
                }
                break;
            }
        }

        if (bestPitch == Float.MAX_VALUE) return null;

        bestPitch = MathHelper.clamp_float(bestPitch, -90f, 90f);
        float[] lastRots   = {lockedYaw, currentPitch};
        float[] targetRots = {lockedYaw, bestPitch};
        float[] fixedRots  = patchGCD(lastRots, targetRots);
        fixedRots[0] = lockedYaw;
        return fixedRots;
    }

    public static float[] getFreeRotationsForFace(BlockPos blockPos, EnumFacing facing) {
        EntityPlayerSP player = mc.thePlayer;

        double eyeX = player.posX;
        double eyeY = player.posY + player.getEyeHeight();
        double eyeZ = player.posZ;

        double faceCX = blockPos.getX() + 0.5 + facing.getFrontOffsetX() * 0.5;
        double faceCY = blockPos.getY() + 0.5 + facing.getFrontOffsetY() * 0.5;
        double faceCZ = blockPos.getZ() + 0.5 + facing.getFrontOffsetZ() * 0.5;

        double dx = faceCX - eyeX, dy = faceCY - eyeY, dz = faceCZ - eyeZ;

        float yaw   = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        pitch = MathHelper.clamp_float(pitch, -90f, 90f);

        float currentYaw   = Arsenic.getArsenic().getSilentRotationManager().yaw;
        float currentPitch = Arsenic.getArsenic().getSilentRotationManager().pitch;
        return patchGCD(new float[]{currentYaw, currentPitch}, new float[]{yaw, pitch});
    }

    // ── Math helpers ───────────────────────────────────────────────

    private static float pitchToHitPoint(double eyeX, double eyeY, double eyeZ,
                                          double hx, double hz,
                                          double targetX, double targetY, double targetZ) {
        double dx = targetX - eyeX, dy = targetY - eyeY, dz = targetZ - eyeZ;
        double tCosp;
        if (Math.abs(hx) > Math.abs(hz)) {
            if (Math.abs(hx) < 1e-6) return Float.NaN;
            tCosp = dx / hx;
        } else {
            if (Math.abs(hz) < 1e-6) return Float.NaN;
            tCosp = dz / hz;
        }
        if (tCosp <= 0) return Float.NaN;
        return (float) Math.toDegrees(Math.atan(-dy / tCosp));
    }

    private static float[] pitchesToHitZPlane(double eyeX, double eyeY, double eyeZ,
                                               double hx, double hz, double faceZ,
                                               double xMin, double xMax,
                                               double yMin, double yMax) {
        if (Math.abs(hz) < 1e-6) return new float[0];
        double tCosp = (faceZ - eyeZ) / hz;
        if (tCosp <= 0) return new float[0];

        double[] sY = {yMin+0.1, yMin+(yMax-yMin)*0.2, yMin+(yMax-yMin)*0.3,
                        yMin+(yMax-yMin)*0.4, (yMin+yMax)*0.5, yMin+(yMax-yMin)*0.6,
                        yMin+(yMax-yMin)*0.7, yMin+(yMax-yMin)*0.8,
                        yMin+(yMax-yMin)*0.9, yMax-0.1};
        float[] results = new float[sY.length];
        int count = 0;
        for (double sy : sY) {
            double hitX = eyeX + hx * tCosp;
            if (hitX < xMin || hitX > xMax) continue;
            results[count++] = (float) Math.toDegrees(Math.atan(-(sy - eyeY) / tCosp));
        }
        float[] trimmed = new float[count];
        System.arraycopy(results, 0, trimmed, 0, count);
        return trimmed;
    }

    private static float[] pitchesToHitXPlane(double eyeX, double eyeY, double eyeZ,
                                               double hx, double hz, double faceX,
                                               double yMin, double yMax,
                                               double zMin, double zMax) {
        if (Math.abs(hx) < 1e-6) return new float[0];
        double tCosp = (faceX - eyeX) / hx;
        if (tCosp <= 0) return new float[0];

        double[] sY = {yMin+0.1, yMin+(yMax-yMin)*0.2, yMin+(yMax-yMin)*0.3,
                        yMin+(yMax-yMin)*0.4, (yMin+yMax)*0.5, yMin+(yMax-yMin)*0.6,
                        yMin+(yMax-yMin)*0.7, yMin+(yMax-yMin)*0.8,
                        yMin+(yMax-yMin)*0.9, yMax-0.1};
        float[] results = new float[sY.length];
        int count = 0;
        for (double sy : sY) {
            double hitZ = eyeZ + hz * tCosp;
            if (hitZ < zMin || hitZ > zMax) continue;
            results[count++] = (float) Math.toDegrees(Math.atan(-(sy - eyeY) / tCosp));
        }
        float[] trimmed = new float[count];
        System.arraycopy(results, 0, trimmed, 0, count);
        return trimmed;
    }

    // ── BlockData ─────────────────────────────────────────────────

    public static class BlockData {
        private BlockPos position;
        private EnumFacing facing;

        public BlockData(BlockPos position, EnumFacing facing) {
            this.position = position;
            this.facing   = facing;
        }

        public EnumFacing getFacing() { return facing; }
        public BlockPos   getPosition() { return position; }
    }
}
