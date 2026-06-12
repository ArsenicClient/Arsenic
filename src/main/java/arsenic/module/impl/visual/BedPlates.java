package arsenic.module.impl.visual;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.world.chunk.Chunk;
import org.lwjgl.opengl.GL11;

import java.util.*;

@ModuleInfo(name = "BedPlates", category = ModuleCategory.WORLD)
public class BedPlates extends Module {

    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(5, 128, 64, 1));
    public final DoubleProperty layers = new DoubleProperty("Layers", new DoubleValue(1, 6, 2, 1));
    public final BooleanProperty showDistance = new BooleanProperty("Show Distance", true);

    private static final int FALLBACK_INTERVAL = 240;
    private static final int CHUNKS_PER_TICK = 2;
    private static final float BASE_SCALE = 0.026F;
    private static final float MAX_SCALE = 0.30F;

    private final Map<String, CachedBed> bedCache = new HashMap<>();
    private final Map<Long, Set<String>> chunkBeds = new HashMap<>();
    private final Set<Long> scannedChunks = new HashSet<>();
    private final Deque<Long> rescanQueue = new ArrayDeque<>();
    private final Set<Long> queuedChunks = new HashSet<>();

    private int ticksSinceFallback;

    @Override
    protected void onEnable() {
        resetCache();
    }

    @Override
    protected void onDisable() {
        resetCache();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.theWorld == null || mc.thePlayer == null) {
            resetCache();
            return;
        }

        removeBrokenBeds();

        int chunkRadius = getChunkRadius();
        int playerCX = mc.thePlayer.getPosition().getX() >> 4;
        int playerCZ = mc.thePlayer.getPosition().getZ() >> 4;

        for (int cx = playerCX - chunkRadius; cx <= playerCX + chunkRadius; cx++) {
            for (int cz = playerCZ - chunkRadius; cz <= playerCZ + chunkRadius; cz++) {
                long ck = chunkKey(cx, cz);
                if (scannedChunks.contains(ck)) continue;
                if (!mc.theWorld.getChunkProvider().chunkExists(cx, cz)) continue;
                scanChunk(cx, cz);
            }
        }

        ticksSinceFallback++;
        if (ticksSinceFallback >= FALLBACK_INTERVAL) {
            ticksSinceFallback = 0;
            queueNearbyChunks(true);
        }

        processQueue();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRender = event -> {
        if (bedCache.isEmpty()) return;

        double maxDistSq = range.getValue().getInput() * range.getValue().getInput();
        List<BedRender> list = new ArrayList<>();

        for (CachedBed cb : bedCache.values()) {
            double dsq = distSq(cb.first, cb.second);
            if (dsq <= maxDistSq) {
                list.add(new BedRender(cb.first, cb.second, cb.defenses, dsq));
            }
        }

        list.sort(Comparator.comparingDouble(a -> a.distanceSq));

        for (BedRender br : list) {
            renderLabel(br);
        }
    };

    // ── Scanning ────────────────────────────────────────────────────────

    private void removeBrokenBeds() {
        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, CachedBed> e : bedCache.entrySet()) {
            CachedBed cb = e.getValue();
            if (!isBed(cb.first) || !isBed(cb.second)) {
                stale.add(e.getKey());
            }
        }
        for (String k : stale) {
            CachedBed removed = bedCache.remove(k);
            if (removed == null) continue;
            long ck = chunkKey(removed.first.getX() >> 4, removed.first.getZ() >> 4);
            Set<String> s = chunkBeds.get(ck);
            if (s != null) {
                s.remove(k);
                if (s.isEmpty()) chunkBeds.remove(ck);
            }
        }
    }

    private void queueNearbyChunks(boolean resetScanned) {
        int r = getChunkRadius();
        int pcx = mc.thePlayer.getPosition().getX() >> 4;
        int pcz = mc.thePlayer.getPosition().getZ() >> 4;
        for (int cx = pcx - r; cx <= pcx + r; cx++) {
            for (int cz = pcz - r; cz <= pcz + r; cz++) {
                long ck = chunkKey(cx, cz);
                if (resetScanned) scannedChunks.remove(ck);
                if (queuedChunks.add(ck)) {
                    rescanQueue.addLast(ck);
                }
            }
        }
    }

    private void processQueue() {
        int done = 0;
        while (!rescanQueue.isEmpty() && done < CHUNKS_PER_TICK) {
            long ck = rescanQueue.removeFirst();
            queuedChunks.remove(ck);
            int cx = (int) (ck >> 32);
            int cz = (int) (long) ck;
            if (mc.theWorld.getChunkProvider().chunkExists(cx, cz)) {
                scanChunk(cx, cz);
            } else {
                scannedChunks.remove(ck);
                chunkBeds.remove(ck);
            }
            done++;
        }
    }

    private void scanChunk(int chunkX, int chunkZ) {
        if (!mc.theWorld.getChunkProvider().chunkExists(chunkX, chunkZ)) return;

        Chunk chunk = mc.theWorld.getChunkFromChunkCoords(chunkX, chunkZ);
        long ck = chunkKey(chunkX, chunkZ);
        Set<String> found = new HashSet<>();

        int sx = chunkX << 4;
        int sz = chunkZ << 4;

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int y = 0; y < 256; y++) {
                    BlockPos pos = new BlockPos(sx + lx, y, sz + lz);
                    if (!(chunk.getBlock(pos) instanceof BlockBed)) continue;

                    BlockPos[] pair = resolveBedPair(pos);
                    if (pair == null) continue;
                    if (chunkKey(pair[0].getX() >> 4, pair[0].getZ() >> 4) != ck) continue;

                    String key = bedKey(pair[0], pair[1]);
                    if (!found.add(key)) continue;

                    bedCache.put(key, new CachedBed(pair[0], pair[1], collectBlocks(pair[0], pair[1])));
                }
            }
        }

        Set<String> prev = chunkBeds.put(ck, found);
        if (prev != null) {
            for (String pk : prev) {
                if (!found.contains(pk)) bedCache.remove(pk);
            }
        }
        if (found.isEmpty()) chunkBeds.remove(ck);
        scannedChunks.add(ck);
    }

    private BlockPos[] resolveBedPair(BlockPos pos) {
        BlockPos other = null;
        for (BlockPos n : new BlockPos[]{pos.north(), pos.south(), pos.east(), pos.west()}) {
            if (isBed(n)) { other = n; break; }
        }
        if (other == null) return null;
        if (comparePos(pos, other) <= 0) {
            return new BlockPos[]{pos, other};
        }
        return new BlockPos[]{other, pos};
    }

    private List<ItemStack> collectBlocks(BlockPos first, BlockPos second) {
        Set<String> seen = new LinkedHashSet<>();
        List<ItemStack> stacks = new ArrayList<>();
        int r = (int) layers.getValue().getInput();

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = 0; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    addBlock(first.add(dx, dy, dz), seen, stacks);
                    addBlock(second.add(dx, dy, dz), seen, stacks);
                }
            }
        }
        return stacks;
    }

    private void addBlock(BlockPos pos, Set<String> seen, List<ItemStack> stacks) {
        IBlockState state = mc.theWorld.getBlockState(pos);
        Block block = state.getBlock();
        if (block == null || block == Blocks.air || block instanceof BlockBed || block.getMaterial() == Material.air) return;

        Item item = Item.getItemFromBlock(block);
        if (item == null) return;

        int meta = block.damageDropped(state);
        String key = Item.itemRegistry.getNameForObject(item) + ":" + meta;
        if (seen.add(key)) {
            stacks.add(new ItemStack(item, 1, meta));
        }
    }

    private boolean isBed(BlockPos pos) {
        return mc.theWorld.getBlockState(pos).getBlock() instanceof BlockBed;
    }

    private int getChunkRadius() {
        return Math.max(1, ((int) range.getValue().getInput() + 15) >> 4);
    }

    // ── Rendering ───────────────────────────────────────────────────────

    private void renderLabel(BedRender br) {
        FontRenderer fr = mc.fontRendererObj;
        if (fr == null) return;

        double vx = mc.getRenderManager().viewerPosX;
        double vy = mc.getRenderManager().viewerPosY;
        double vz = mc.getRenderManager().viewerPosZ;

        double x = (br.first.getX() + br.second.getX()) / 2.0 + 0.5 - vx;
        double y = Math.max(br.first.getY(), br.second.getY()) + 1.35 - vy;
        double z = (br.first.getZ() + br.second.getZ()) / 2.0 + 0.5 - vz;

        float scale = labelScale(br.distanceSq);

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GL11.glNormal3f(0, 1, 0);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0, 1, 0);
        GlStateManager.rotate(mc.getRenderManager().playerViewX, 1, 0, 0);
        GlStateManager.scale(-scale, -scale, scale);

        if (br.defenses.isEmpty()) {
            GlStateManager.disableLighting();
            GlStateManager.depthMask(false);
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            String text = "Uncovered";
            int w = fr.getStringWidth(text) / 2;
            drawBackground(w, fr.FONT_HEIGHT);
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.enableTexture2D();
            fr.drawString(text, -w, 0, 0xFFFFFFFF);
        } else {
            drawIcons(br.defenses);
        }

        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);
        GL11.glColor4f(1, 1, 1, 1);
        GlStateManager.popMatrix();
    }

    private void drawIcons(List<ItemStack> stacks) {
        float iconSize = 16;
        float spacing = 18;
        int count = stacks.size();
        float totalW = count * spacing;
        float startX = -totalW / 2;

        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1, 1, 1, 1);

        drawBackground(startX - 2, -iconSize - 2, startX + totalW + 2, 2);

        if (showDistance.getValue() && count > 0) {
            GlStateManager.enableTexture2D();
        }

        mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.01f);

        for (int i = 0; i < count; i++) {
            ItemStack stack = stacks.get(i);
            TextureAtlasSprite sprite = resolveSprite(stack);
            if (sprite == null) continue;

            float left = startX + i * spacing + (spacing - iconSize) / 2;
            float top = -iconSize;
            drawSprite(sprite, left, top, iconSize);
        }

        GlStateManager.disableAlpha();
        GlStateManager.color(1, 1, 1, 1);
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

    private void drawBackground(int halfW, int textH) {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        GlStateManager.disableTexture2D();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(-halfW - 2, -2, 0).color(0, 0, 0, 0.45f).endVertex();
        wr.pos(-halfW - 2, textH + 2, 0).color(0, 0, 0, 0.45f).endVertex();
        wr.pos(halfW + 2, textH + 2, 0).color(0, 0, 0, 0.45f).endVertex();
        wr.pos(halfW + 2, -2, 0).color(0, 0, 0, 0.45f).endVertex();
        tess.draw();
        GlStateManager.enableTexture2D();
    }

    private void drawBackground(float minX, float minY, float maxX, float maxY) {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        GlStateManager.disableTexture2D();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(minX, minY, 0).color(0, 0, 0, 0.45f).endVertex();
        wr.pos(minX, maxY, 0).color(0, 0, 0, 0.45f).endVertex();
        wr.pos(maxX, maxY, 0).color(0, 0, 0, 0.45f).endVertex();
        wr.pos(maxX, minY, 0).color(0, 0, 0, 0.45f).endVertex();
        tess.draw();
        GlStateManager.enableTexture2D();
    }

    private float labelScale(double distSq) {
        float dist = (float) Math.sqrt(distSq);
        float s = BASE_SCALE * Math.max(1, (dist / 32) * 6);
        return Math.min(MAX_SCALE, s);
    }

    // ── Math helpers ────────────────────────────────────────────────────

    private double distSq(BlockPos a, BlockPos b) {
        double cx = (a.getX() + b.getX()) / 2.0 + 0.5;
        double cy = Math.max(a.getY(), b.getY()) + 0.5;
        double cz = (a.getZ() + b.getZ()) / 2.0 + 0.5;
        double dx = mc.thePlayer.posX - cx;
        double dy = mc.thePlayer.posY - cy;
        double dz = mc.thePlayer.posZ - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    private long chunkKey(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    private String bedKey(BlockPos a, BlockPos b) {
        return a.getX() + ":" + a.getY() + ":" + a.getZ() + "|" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }

    private int comparePos(BlockPos a, BlockPos b) {
        if (a.getY() != b.getY()) return a.getY() - b.getY();
        if (a.getX() != b.getX()) return a.getX() - b.getX();
        return a.getZ() - b.getZ();
    }

    private void resetCache() {
        ticksSinceFallback = 0;
        bedCache.clear();
        chunkBeds.clear();
        scannedChunks.clear();
        rescanQueue.clear();
        queuedChunks.clear();
    }

    // ── Data classes ────────────────────────────────────────────────────

    private static class CachedBed {
        final BlockPos first, second;
        final List<ItemStack> defenses;

        CachedBed(BlockPos first, BlockPos second, List<ItemStack> defenses) {
            this.first = first;
            this.second = second;
            this.defenses = defenses;
        }
    }

    private static class BedRender {
        final BlockPos first, second;
        final List<ItemStack> defenses;
        final double distanceSq;

        BedRender(BlockPos first, BlockPos second, List<ItemStack> defenses, double distanceSq) {
            this.first = first;
            this.second = second;
            this.defenses = defenses;
            this.distanceSq = distanceSq;
        }
    }
}
