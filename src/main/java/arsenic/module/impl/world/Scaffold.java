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
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.minecraft.ScaffoldUtil;
import arsenic.utils.render.DrawUtils;
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
import net.minecraft.util.*;
import org.lwjgl.opengl.GL11;

import static arsenic.utils.minecraft.ScaffoldUtil.willFallNextTick;
import static arsenic.utils.rotations.RotationUtils.patchGCD;

@ModuleInfo(name = "Scaffold", category = ModuleCategory.PLAYER)
public class Scaffold extends Module {


    public BooleanProperty sprint = new BooleanProperty("Sprint", false) {
        @Override
        public Boolean getValue() {
            return super.getValue() || telly.getValue();
        }

        @Override
        public void setValue(Boolean value) {
            if(!value) {
                telly.setValue(false);
            }
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
            if(!value) {
                telly.setValue(false);
            }
            super.setValue(value);
        }
    };

    public BooleanProperty telly = new  BooleanProperty("Telly", false);
    @PropertyInfo(reliesOn = "Telly", value = "true")
    public DoubleProperty mY = new DoubleProperty("", new DoubleValue(-0.5, 0.5, 0, 0.01) {
        @Override
        public double getInput() {
            return telly.getValue() ? super.getInput() : 0.3;
        }
    });


    private BlockData blockData;
    private float[] rots = new float[2];
    private boolean solvedRots;
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
    private static final ItemBlock placeholderBlock = new ItemBlock(Blocks.tnt);


    @Override
    protected void onEnable() {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        blockData = null;
        animatedScale = 0f;
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        animatedScale = 0f;
        super.onDisable();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> eventSilentRotationListener = event -> {
        boolean wilLFall = ScaffoldUtil.willFallNextTick() && mc.thePlayer.motionY < mY.getValue().getInput();
        blockData = findBestPlacement();
        Item item = keyBlock();
        event.setSpeed(360f);
        event.setPreventDuplicateLook(true);

        if(telly.getValue()){
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), mc.gameSettings.keyBindJump.isKeyDown());
        }

        float delta = sprint.getValue() ? 0f : 180f;
        event.setYaw(mc.thePlayer.rotationYaw + delta);
        event.setPitch(rots[1]);

        if(item == null)
            return;

        if(wilLFall && (!telly.getValue() || !mc.thePlayer.onGround)) {
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
        if(keyBlock() == null || blockData == null)
            return;

        Item item = keyBlock();
        MovingObjectPosition movingObjectPosition = event.getRayTrace();

        if(item instanceof ItemBlock) {
            ItemBlock itemBlock = (ItemBlock) item;
            if (movingObjectPosition.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                    && (movingObjectPosition.sideHit != EnumFacing.DOWN)
                    && (!keepY.getValue() || movingObjectPosition.sideHit != EnumFacing.UP)
                    && itemBlock.canPlaceBlockOnSide(mc.theWorld, movingObjectPosition.getBlockPos(), movingObjectPosition.sideHit, mc.thePlayer, mc.thePlayer.getHeldItem())) {
                blockData = new BlockData(movingObjectPosition.getBlockPos(), movingObjectPosition.sideHit);
                placePost(event);
            }
        }
    };



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

    @EventLink
    public final Listener<EventRender2D> onRender2D = event -> {
        int blockCount = getBlockCount();
        if (isEnabled() && blockCount > 0) {
            animatedScale = interpolate(animatedScale, 1.0f, 0.1f);
        } else if (!isEnabled() || blockCount == 0) {
            animatedScale = interpolate(animatedScale, 0.0f, 0.15f);
        }

        if (animatedScale <= 0.01f) return;

        drawBlockCounter();
    };

    @EventLink
    public final Listener<EventShader.Blur> blurListener = event -> {
        if (animatedScale <= 0.01f) return;

        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();
        if (fr == null) return;

        int blockCount = getBlockCount();
        String text = String.valueOf(blockCount);
        int iconSize = 16;
        int padding = 4;
        int textWidth = (int) fr.getWidth(text);
        int bw = iconSize + padding + textWidth + padding * 2;
        int bh = iconSize + padding * 2;

        ScaledResolution sr = new ScaledResolution(mc);
        int x = blockCounterX;
        int y = blockCounterY;
        if (x == -1) x = (sr.getScaledWidth() - bw) / 2;
        if (y == -1) y = sr.getScaledHeight() - 40 - bh;

        GL11.glPushMatrix();
        GL11.glTranslated(x + bw / 2.0, y + bh / 2.0, 0);
        GL11.glScalef(animatedScale, animatedScale, 1.0f);
        GL11.glTranslated(-(x + bw / 2.0), -(y + bh / 2.0), 0);

        Gui.drawRect(x, y, x + bw, y + bh, -1);

        GL11.glPopMatrix();
    };

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

    private int interpolateColor(int color1, int color2, float t) {
        int r1 = (color1 >> 16) & 0xFF, g1 = (color1 >> 8) & 0xFF, b1 = color1 & 0xFF;
        int r2 = (color2 >> 16) & 0xFF, g2 = (color2 >> 8) & 0xFF, b2 = color2 & 0xFF;
        int r  = (int)(r1 + (r2 - r1) * t);
        int g  = (int)(g1 + (g2 - g1) * t);
        int b  = (int)(b1 + (b2 - b1) * t);
        return (r << 16) | (g << 8) | b;
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

    public BlockData findBestPlacement() {
        EntityPlayerSP player = mc.thePlayer;
        BlockPos playerPos = new BlockPos(player);
        BlockPos scanY = playerPos.down();

        BlockData best = null;
        double bestScore = Double.MAX_VALUE;

        // The cell the player is about to occupy — this is what the scaffold actually needs to
        // support, not whatever face happens to be closest to the eyes. Centre it on the
        // predicted bounding box so selection follows the direction of travel.
        AxisAlignedBB predicted = ScaffoldUtil.getPredictedBoundingBox(1.0);
        double targetX = (predicted.minX + predicted.maxX) * 0.5;
        double targetZ = (predicted.minZ + predicted.maxZ) * 0.5;
        double targetY = scanY.getY() + 0.5;

        // Score of the best block ALREADY supporting the target cell. If no candidate beats this,
        // a placement would be redundant — the player is already standing on something at least as
        // good, so we return null rather than spend a block.
        double existingScore = Double.MAX_VALUE;

        // When KeepY is off and the player is airborne (jumping / falling), also scan the layer
        // below so the block directly under the player can be towered: its UP face creates a block
        // at the player's feet. On ground we only need the regular support layer.
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

                // This block already exists on the support layer. Record how well it fills the
                // target cell so we can compare any new placement against what's already there.
                double exDx = (pos.getX() + 0.5) - targetX;
                double exDz = (pos.getZ() + 0.5) - targetZ;
                double exDy = (pos.getY() + 0.5) - targetY;
                double exScore = exDx * exDx + exDz * exDz + exDy * exDy * 0.25;
                if (exScore < existingScore)
                    existingScore = exScore;

                List<EnumFacing> facings = new ArrayList<>(Arrays.asList(EnumFacing.HORIZONTALS));
                // Allow towering whenever airborne with KeepY off — whether rising or falling — so a
                // block under the player's feet is a valid candidate. The listener's wilLFall check
                // still governs WHEN a block is actually spent.
                if (tower) {
                    facings.add(EnumFacing.UP);
                }

                for (EnumFacing facing : facings) {
                    if (!placeholderBlock.canPlaceBlockOnSide(mc.theWorld, pos, facing, mc.thePlayer, mc.thePlayer.getHeldItem()))
                        continue;

                    BlockPos neighbor = pos.offset(facing);
                    IBlockState neighborState = mc.theWorld.getBlockState(neighbor);

                    if (neighborState.getBlock() != Blocks.air)
                        continue;

                    // The block we'd actually create occupies `neighbor`. Score it by how well it
                    // fills the cell the player is heading into, so we never return a face whose
                    // resulting block sits further from the player's path than one already placed.
                    double nbCenterX = neighbor.getX() + 0.5;
                    double nbCenterY = neighbor.getY() + 0.5;
                    double nbCenterZ = neighbor.getZ() + 0.5;

                    double dx = nbCenterX - targetX;
                    double dz = nbCenterZ - targetZ;
                    double dy = nbCenterY - targetY;
                    // Horizontal alignment with the path dominates; vertical offset is a soft tiebreak.
                    double score = dx * dx + dz * dz + dy * dy * 0.25;

                    if (score >= bestScore)
                        continue;

                    float[] rots = getRotationsForFace(pos, facing);
                    if (rots == null) {
                        rots = getFreeRotationsForFace(pos, facing);
                    }

                    Vec3 eyeVec = player.getPositionEyes(1.0f);
                    Vec3 lookDir = ((IMixinEntity) player).invokeGetVectorForRotation(rots[1], rots[0]);
                    Vec3 traceEnd = eyeVec.addVector(
                            lookDir.xCoord * 4.5,
                            lookDir.yCoord * 4.5,
                            lookDir.zCoord * 4.5
                    );
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


        // A block is already supporting the target cell at least as well as anything we could
        // place — placing now would just waste a block, so signal "nothing to do".
        if (best != null && existingScore <= bestScore) {
            return null;
        }

        return best;
    }



    private Item keyBlock() {
        if (mc.thePlayer.inventory.getCurrentItem() == null
                || !(mc.thePlayer.inventory.getCurrentItem().getItem() instanceof ItemBlock) || mc.thePlayer.inventory.getCurrentItem().stackSize <= 1) {
            mc.thePlayer.inventory.currentItem = ScaffoldUtil.getBlockSlot();
        }
        if(mc.thePlayer.inventory.getCurrentItem() == null)
            return null;
        return mc.thePlayer.inventory.getCurrentItem().getItem();
    }



    private void placePost(EventSilentRotation.Post event) {
        if (blockData == null) {
            return;
        }

        MovingObjectPosition objectOver = event.getRayTrace();
        BlockPos blockpos = objectOver.getBlockPos();
        if (objectOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || mc.theWorld.getBlockState(blockpos).getBlock().getMaterial() == Material.air) {
            return;
        }

        mc.playerController.onPlayerRightClick(
                mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem(),
                blockData.position, blockData.facing, ScaffoldUtil.getNewVector(blockData)
        );

        mc.thePlayer.swingItem();
        recordPlacement();
    }

    public static float[] getRotationsForFace(BlockPos blockPos, EnumFacing facing) {
        EntityPlayerSP player = mc.thePlayer;

        double eyeX = player.posX;
        double eyeY = player.posY + player.getEyeHeight();
        double eyeZ = player.posZ;

        float lockedYaw = player.rotationYaw + 180f;
        float yawRad = (float) Math.toRadians(lockedYaw);

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
                float[] candidates = pitchesToHitZPlane(eyeX, eyeY, eyeZ, hx, hz,
                        bz0, bx0, bx1, by0, by1);
                for (float p : candidates) {
                    if (!Float.isNaN(p)) {
                        float diff = Math.abs(MathHelper.wrapAngleTo180_float(p - currentPitch));
                        if (diff < bestDiff) { bestDiff = diff; bestPitch = p; }
                    }
                }
                break;
            }
            case SOUTH: {
                float[] candidates = pitchesToHitZPlane(eyeX, eyeY, eyeZ, hx, hz,
                        bz1, bx0, bx1, by0, by1);
                for (float p : candidates) {
                    if (!Float.isNaN(p)) {
                        float diff = Math.abs(MathHelper.wrapAngleTo180_float(p - currentPitch));
                        if (diff < bestDiff) { bestDiff = diff; bestPitch = p; }
                    }
                }
                break;
            }
            case WEST: {
                float[] candidates = pitchesToHitXPlane(eyeX, eyeY, eyeZ, hx, hz,
                        bx0, by0, by1, bz0, bz1);
                for (float p : candidates) {
                    if (!Float.isNaN(p)) {
                        float diff = Math.abs(MathHelper.wrapAngleTo180_float(p - currentPitch));
                        if (diff < bestDiff) { bestDiff = diff; bestPitch = p; }
                    }
                }
                break;
            }
            case EAST: {
                float[] candidates = pitchesToHitXPlane(eyeX, eyeY, eyeZ, hx, hz,
                        bx1, by0, by1, bz0, bz1);
                for (float p : candidates) {
                    if (!Float.isNaN(p)) {
                        float diff = Math.abs(MathHelper.wrapAngleTo180_float(p - currentPitch));
                        if (diff < bestDiff) { bestDiff = diff; bestPitch = p; }
                    }
                }
                break;
            }
        }

        if (bestPitch == Float.MAX_VALUE) {
            return null;  // locked yaw can't hit this face
        }

        bestPitch = MathHelper.clamp_float(bestPitch, -90f, 90f);

        float[] lastRots = new float[]{lockedYaw, currentPitch};
        float[] targetRots = new float[]{lockedYaw, bestPitch};
        float[] fixedRots = patchGCD(lastRots, targetRots);
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

        double dx = faceCX - eyeX;
        double dy = faceCY - eyeY;
        double dz = faceCZ - eyeZ;

        float yaw   = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));

        pitch = MathHelper.clamp_float(pitch, -90f, 90f);

        float currentYaw   = Arsenic.getArsenic().getSilentRotationManager().yaw;
        float currentPitch = Arsenic.getArsenic().getSilentRotationManager().pitch;

        float[] lastRots   = new float[]{currentYaw, currentPitch};
        float[] targetRots = new float[]{yaw, pitch};
        return patchGCD(lastRots, targetRots);
    }


    private static float pitchToHitPoint(double eyeX, double eyeY, double eyeZ,
                                         double hx, double hz,
                                         double targetX, double targetY, double targetZ) {
        double dx = targetX - eyeX;
        double dy = targetY - eyeY;
        double dz = targetZ - eyeZ;

        double tCosp;
        if (Math.abs(hx) > Math.abs(hz)) {
            if (Math.abs(hx) < 1e-6) return Float.NaN;
            tCosp = dx / hx;
        } else {
            if (Math.abs(hz) < 1e-6) return Float.NaN;
            tCosp = dz / hz;
        }

        if (tCosp <= 0) return Float.NaN;

        double tanPitch = -dy / tCosp;
        return (float) Math.toDegrees(Math.atan(tanPitch));
    }

    private static float[] pitchesToHitZPlane(double eyeX, double eyeY, double eyeZ,
                                              double hx, double hz,
                                              double faceZ,
                                              double xMin, double xMax,
                                              double yMin, double yMax) {
        if (Math.abs(hz) < 1e-6) return new float[0];

        double tCosp = (faceZ - eyeZ) / hz;
        if (tCosp <= 0) return new float[0];

        double[] sampleY = {
                yMin + 0.1,
                yMin + (yMax - yMin) * 0.2,
                yMin + (yMax - yMin) * 0.3,
                yMin + (yMax - yMin) * 0.4,
                (yMin + yMax) * 0.5,
                yMin + (yMax - yMin) * 0.6,
                yMin + (yMax - yMin) * 0.7,
                yMin + (yMax - yMin) * 0.8,
                yMin + (yMax - yMin) * 0.9,
                yMax - 0.1
        };

        float[] results = new float[sampleY.length];
        int count = 0;
        for (double sy : sampleY) {
            double dy = sy - eyeY;
            double hitX = eyeX + hx * tCosp;
            if (hitX < xMin || hitX > xMax) continue;

            double tanPitch = -dy / tCosp;
            results[count++] = (float) Math.toDegrees(Math.atan(tanPitch));
        }

        float[] trimmed = new float[count];
        System.arraycopy(results, 0, trimmed, 0, count);
        return trimmed;
    }

    private static float[] pitchesToHitXPlane(double eyeX, double eyeY, double eyeZ,
                                              double hx, double hz,
                                              double faceX,
                                              double yMin, double yMax,
                                              double zMin, double zMax) {
        if (Math.abs(hx) < 1e-6) return new float[0];

        double tCosp = (faceX - eyeX) / hx;
        if (tCosp <= 0) return new float[0];

        double[] sampleY = {
                yMin + 0.1,
                yMin + (yMax - yMin) * 0.2,
                yMin + (yMax - yMin) * 0.3,
                yMin + (yMax - yMin) * 0.4,
                (yMin + yMax) * 0.5,
                yMin + (yMax - yMin) * 0.6,
                yMin + (yMax - yMin) * 0.7,
                yMin + (yMax - yMin) * 0.8,
                yMin + (yMax - yMin) * 0.9,
                yMax - 0.1
        };

        float[] results = new float[sampleY.length];
        int count = 0;
        for (double sy : sampleY) {
            double dy = sy - eyeY;
            double hitZ = eyeZ + hz * tCosp;
            if (hitZ < zMin || hitZ > zMax) continue;

            double tanPitch = -dy / tCosp;
            results[count++] = (float) Math.toDegrees(Math.atan(tanPitch));
        }

        float[] trimmed = new float[count];
        System.arraycopy(results, 0, trimmed, 0, count);
        return trimmed;
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