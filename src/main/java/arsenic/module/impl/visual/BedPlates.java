package arsenic.module.impl.visual;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventTick;
import arsenic.gui.design.M3;
import arsenic.gui.hud.HudStyle;
import arsenic.injection.accessor.IMixinRenderManager;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.render.DrawUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ModuleInfo(name = "BedPlates", category = ModuleCategory.RENDER, hidden = true, tier = ModuleTier.VISUAL)
public class BedPlates extends Module {

    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(10, 100, 50, 5));
    public final DoubleProperty layers = new DoubleProperty("Max Layers", new DoubleValue(1, 5, 3, 1));
    public final DoubleProperty scale = new DoubleProperty("Scale", new DoubleValue(0.5, 3.0, 1.0, 0.1));

    private final Map<BlockPos, List<ItemStack>> bedCache = new HashMap<>();
    private int ticks = 0;

    private static final Set<Block> WHITELIST = new HashSet<>(Arrays.asList(
            Blocks.wool,
            Blocks.planks,
            Blocks.log,
            Blocks.log2,
            Blocks.end_stone,
            Blocks.obsidian,
            Blocks.stained_hardened_clay,
            Blocks.hardened_clay,
            Blocks.stained_glass,
            Blocks.glass,
            Blocks.ladder,
            Blocks.water,
            Blocks.flowing_water
    ));

    @Override
    protected void onEnable() {
        bedCache.clear();
        ticks = 0;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        ticks++;
        if (ticks % 20 != 0) return;

        int r = (int) range.getValue().getInput();
        BlockPos playerPos = mc.thePlayer.getPosition();

        Iterator<Map.Entry<BlockPos, List<ItemStack>>> it = bedCache.entrySet().iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next().getKey();
            if (pos.distanceSq(playerPos) > r * r || mc.theWorld.getBlockState(pos).getBlock() != Blocks.bed) {
                it.remove();
            }
        }

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    IBlockState state = mc.theWorld.getBlockState(pos);
                    if (state.getBlock() == Blocks.bed && state.getValue(BlockBed.PART) == BlockBed.EnumPartType.HEAD) {
                        bedCache.put(pos, getBedDefense(pos));
                    }
                }
            }
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRender = event -> {
        if (bedCache.isEmpty()) return;

        IMixinRenderManager rm = (IMixinRenderManager) mc.getRenderManager();
        double rx = rm.getRenderPosX();
        double ry = rm.getRenderPosY();
        double rz = rm.getRenderPosZ();

        for (Map.Entry<BlockPos, List<ItemStack>> entry : bedCache.entrySet()) {
            BlockPos pos = entry.getKey();
            List<ItemStack> blocks = entry.getValue();

            IBlockState state = mc.theWorld.getBlockState(pos);
            double x = pos.getX() + 0.5 - rx;
            double y = pos.getY() + 1.5 - ry;
            double z = pos.getZ() + 0.5 - rz;

            if (state.getBlock() == Blocks.bed && state.getProperties().containsKey(BlockBed.FACING)) {
                EnumFacing facing = state.getValue(BlockBed.FACING);
                EnumFacing footDir = facing.getOpposite();
                x += footDir.getFrontOffsetX() * 0.5;
                z += footDir.getFrontOffsetZ() * 0.5;
            }

            renderPlate(blocks, x, y, z);
        }
    };

    private List<ItemStack> getBedDefense(BlockPos headPos) {
        Set<String> seenTypes = new LinkedHashSet<>();
        List<ItemStack> defense = new ArrayList<>();
        int maxLayers = (int) layers.getValue().getInput();

        for (int l = 1; l <= maxLayers; l++) {
            BlockPos checkPos = headPos.up(l);
            IBlockState state = mc.theWorld.getBlockState(checkPos);
            Block block = state.getBlock();

            if (WHITELIST.contains(block)) {
                int meta = block.getDamageValue(mc.theWorld, checkPos);
                String key = Block.getIdFromBlock(block) + ":" + meta;
                if (!seenTypes.contains(key)) {
                    seenTypes.add(key);
                    defense.add(new ItemStack(block, 1, meta));
                }
            }
        }
        return defense;
    }

    private void renderPlate(List<ItemStack> blocks, double x, double y, double z) {
        FontRendererExtension<?> labelFr = HudStyle.label();
        if (labelFr == null) return;

        double dist = mc.thePlayer.getDistance(
                x + mc.getRenderManager().viewerPosX,
                y + mc.getRenderManager().viewerPosY,
                z + mc.getRenderManager().viewerPosZ);
        float s = (float) (Math.max(1.25, dist * 0.14) * scale.getValue().getInput() * 0.025);

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GL11.glNormal3f(0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-s, -s, s);

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        if (blocks.isEmpty()) {
            String text = "Uncovered";
            float padX = 7f;
            float dotGap = 6.5f;
            float textW = labelFr.getWidth(text);
            float cardW = padX + dotGap + textW + padX;
            float cardH = 18f;
            float x1 = -cardW / 2f;
            float y1 = -cardH / 2f;

            DrawUtils.drawRoundedRect(x1, y1, x1 + cardW, y1 + cardH, 5.5f,
                    M3.alpha(M3.surfaceContainerLow(), 220));
            DrawUtils.drawRoundedOutline(x1, y1, x1 + cardW, y1 + cardH, 5.5f, 1f,
                    M3.alpha(M3.error(), 175));
            DrawUtils.drawCircle(x1 + padX + 1.5f, 0f, 2f, M3.error());
            labelFr.drawString(text, x1 + padX + dotGap, 0f, M3.error(), labelFr.CENTREY);
        } else {
            float slotSize = 18f;
            float slotGap = 3f;
            float pad = 4f;
            float totalSlotsW = blocks.size() * slotSize + (blocks.size() - 1) * slotGap;
            float cardW = totalSlotsW + pad * 2f;
            float cardH = slotSize + pad * 2f;
            float x1 = -cardW / 2f;
            float y1 = -cardH / 2f;

            DrawUtils.drawRoundedRect(x1, y1, x1 + cardW, y1 + cardH, 6f,
                    M3.alpha(M3.surfaceContainerLow(), 225));
            DrawUtils.drawRoundedOutline(x1, y1, x1 + cardW, y1 + cardH, 6f, 1f,
                    M3.alpha(M3.outlineVariant(), 145));

            float startX = -totalSlotsW / 2f;
            float slotY = -slotSize / 2f;
            for (int i = 0; i < blocks.size(); i++) {
                float sx = startX + i * (slotSize + slotGap);
                DrawUtils.drawRoundedRect(sx, slotY, sx + slotSize, slotY + slotSize, 4f,
                        M3.alpha(M3.surfaceContainerHigh(), 205));
            }

            mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.01f);
            GlStateManager.color(1f, 1f, 1f, 1f);

            for (int i = 0; i < blocks.size(); i++) {
                ItemStack stack = blocks.get(i);
                TextureAtlasSprite sprite = resolveSprite(stack);
                float sx = startX + i * (slotSize + slotGap) + (slotSize - 14f) / 2f;
                float sy = slotY + (slotSize - 14f) / 2f;
                if (sprite != null) {
                    drawSprite(sprite, sx, sy, 14f);
                } else {
                    GlStateManager.pushMatrix();
                    RenderHelper.enableGUIStandardItemLighting();
                    mc.getRenderItem().zLevel = -100.0F;
                    mc.getRenderItem().renderItemAndEffectIntoGUI(stack, Math.round(sx), Math.round(sy));
                    mc.getRenderItem().zLevel = 0.0F;
                    RenderHelper.disableStandardItemLighting();
                    GlStateManager.popMatrix();
                }
            }
        }

        GlStateManager.enableCull();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private TextureAtlasSprite resolveSprite(ItemStack stack) {
        try {
            IBakedModel model = mc.getRenderItem().getItemModelMesher().getItemModel(stack);
            if (model != null) {
                TextureAtlasSprite sprite = model.getParticleTexture();
                if (sprite != null) return sprite;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void drawSprite(TextureAtlasSprite sprite, float left, float top, float size) {
        float minU = sprite.getMinU();
        float maxU = sprite.getMaxU();
        float minV = sprite.getMinV();
        float maxV = sprite.getMaxV();
        float right = left + size;
        float bottom = top + size;

        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(left, bottom, 0).tex(minU, maxV).endVertex();
        wr.pos(right, bottom, 0).tex(maxU, maxV).endVertex();
        wr.pos(right, top, 0).tex(maxU, minV).endVertex();
        wr.pos(left, top, 0).tex(minU, minV).endVertex();
        tess.draw();
    }
}
