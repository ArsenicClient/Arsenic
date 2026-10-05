package arsenic.module.impl.visual;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.event.impl.EventRender2D;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.WorldToScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;

import java.util.*;

@ModuleInfo(name = "BedPlates", category = ModuleCategory.RENDER, hidden = true)
public class BedPlates extends Module {


    private static final int FALLBACK_INTERVAL = 240;
    private static final int CHUNKS_PER_TICK = 2;

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
        if (mc.level == null || mc.player == null) {
            resetCache();
            return;
        }

        removeBrokenBeds();

        int chunkRadius = getChunkRadius();
        int playerCX = mc.player.getBlockX() >> 4;
        int playerCZ = mc.player.getBlockZ() >> 4;

        for (int cx = playerCX - chunkRadius; cx <= playerCX + chunkRadius; cx++) {
            for (int cz = playerCZ - chunkRadius; cz <= playerCZ + chunkRadius; cz++) {
                long ck = chunkKey(cx, cz);
                if (scannedChunks.contains(ck)) continue;
                if (mc.level.getChunkSource().getChunkNow(cx, cz) == null) continue;
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
    public final Listener<EventRender2D> onRender = event -> {
        if (bedCache.isEmpty()) return;

        double maxDistSq = 64 * 64;
        List<BedRender> list = new ArrayList<>();

        for (CachedBed cb : bedCache.values()) {
            double dsq = distSq(cb.first, cb.second);
            if (dsq <= maxDistSq) {
                list.add(new BedRender(cb.first, cb.second, cb.defenses, dsq));
            }
        }

        // far first, so nearer labels draw on top
        list.sort(Comparator.comparingDouble(a -> -a.distanceSq));

        for (BedRender br : list) {
            renderLabel(br, event);
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
        int pcx = mc.player.getBlockX() >> 4;
        int pcz = mc.player.getBlockZ() >> 4;
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
            if (mc.level.getChunkSource().getChunkNow(cx, cz) != null) {
                scanChunk(cx, cz);
            } else {
                scannedChunks.remove(ck);
                chunkBeds.remove(ck);
            }
            done++;
        }
    }

    private void scanChunk(int chunkX, int chunkZ) {
        LevelChunk chunk = mc.level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) return;

        long ck = chunkKey(chunkX, chunkZ);
        Set<String> found = new HashSet<>();

        // Only sections whose palette can contain a bed are walked, so the world's full height
        // (no longer 0..255) costs next to nothing.
        LevelChunkSection[] sections = chunk.getSections();
        for (int s = 0; s < sections.length; s++) {
            LevelChunkSection section = sections[s];
            if (section.hasOnlyAir() || !section.maybeHas(state -> state.getBlock() instanceof AbstractBedBlock)) continue;
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(s));
            for (int lx = 0; lx < 16; lx++) {
                for (int ly = 0; ly < 16; ly++) {
                    for (int lz = 0; lz < 16; lz++) {
                        if (!(section.getBlockState(lx, ly, lz).getBlock() instanceof AbstractBedBlock)) continue;
                        BlockPos pos = new BlockPos((chunkX << 4) + lx, baseY + ly, (chunkZ << 4) + lz);

                        BlockPos[] pair = resolveBedPair(pos);
                        if (pair == null) continue;
                        if (chunkKey(pair[0].getX() >> 4, pair[0].getZ() >> 4) != ck) continue;

                        String key = bedKey(pair[0], pair[1]);
                        if (!found.add(key)) continue;

                        bedCache.put(key, new CachedBed(pair[0], pair[1], collectBlocks(pair[0], pair[1])));
                    }
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
        BlockState state = mc.level.getBlockState(pos);
        if (!(state.getBlock() instanceof AbstractBedBlock)) return null;
        BlockPos other = pos.relative(AbstractBedBlock.getConnectedDirection(state));
        if (!isBed(other)) return null;
        if (comparePos(pos, other) <= 0) {
            return new BlockPos[]{pos, other};
        }
        return new BlockPos[]{other, pos};
    }

    private List<ItemStack> collectBlocks(BlockPos first, BlockPos second) {
        Set<Item> seen = new LinkedHashSet<>();
        List<ItemStack> stacks = new ArrayList<>();
        int r = 2;

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = 0; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    addBlock(first.offset(dx, dy, dz), seen, stacks);
                    addBlock(second.offset(dx, dy, dz), seen, stacks);
                }
            }
        }
        return stacks;
    }

    private void addBlock(BlockPos pos, Set<Item> seen, List<ItemStack> stacks) {
        BlockState state = mc.level.getBlockState(pos);
        Block block = state.getBlock();
        if (state.isAir() || block instanceof AbstractBedBlock) return;

        Item item = block.asItem();
        if (item == Items.AIR) return;

        if (seen.add(item)) {
            stacks.add(new ItemStack(item));
        }
    }

    private boolean isBed(BlockPos pos) {
        return mc.level.getBlockState(pos).getBlock() instanceof AbstractBedBlock;
    }

    private int getChunkRadius() {
        return Math.max(1, ((int) 64 + 15) >> 4);
    }

    // ── Rendering ───────────────────────────────────────────────────────

    /**
     * The label floats over the bed. 1.8 drew it as a world-space billboard; here the point is
     * projected onto the screen and drawn with the HUD, which lets the defence row use the game's
     * real item rendering.
     */
    private void renderLabel(BedRender br, EventRender2D event) {
        double x = (br.first.getX() + br.second.getX()) / 2.0 + 0.5;
        double y = Math.max(br.first.getY(), br.second.getY()) + 1.35;
        double z = (br.first.getZ() + br.second.getZ()) / 2.0 + 0.5;

        WorldToScreen.Point point = WorldToScreen.project(new Vec3(x, y, z));
        if (point == null) return;

        float scale = labelScale(br.distanceSq);
        Matrix3x2fStack pose = event.getGraphics().pose();
        pose.pushMatrix();
        pose.translate(point.x(), point.y());
        pose.scale(scale, scale);
        try {
            if (br.defenses.isEmpty()) {
                String text = "Uncovered";
                int w = mc.font.width(text) / 2;
                DrawUtils.drawRect(-w - 2, -2, w + 2, mc.font.lineHeight + 2, 0x73000000);
                event.getGraphics().text(mc.font, text, -w, 0, 0xFFFFFFFF, false);
            } else {
                drawIcons(br.defenses, event);
            }
        } finally {
            pose.popMatrix();
        }
    }

    private void drawIcons(List<ItemStack> stacks, EventRender2D event) {
        float iconSize = 16;
        float spacing = 18;
        int count = stacks.size();
        float totalW = count * spacing;
        float startX = -totalW / 2;

        DrawUtils.drawRect(startX - 2, -iconSize - 2, startX + totalW + 2, 2, 0x73000000);

        for (int i = 0; i < count; i++) {
            float left = startX + i * spacing + (spacing - iconSize) / 2;
            event.getGraphics().pose().pushMatrix();
            event.getGraphics().pose().translate(left, -iconSize);
            event.getGraphics().item(stacks.get(i), 0, 0);
            event.getGraphics().pose().popMatrix();
        }
    }

    /** Labels shrink with distance like a world-space label would, but never below readable. */
    private float labelScale(double distSq) {
        float dist = (float) Math.sqrt(distSq);
        return Math.max(0.5f, Math.min(1.5f, 12f / Math.max(1f, dist)));
    }

    // ── Math helpers ────────────────────────────────────────────────────

    private double distSq(BlockPos a, BlockPos b) {
        double cx = (a.getX() + b.getX()) / 2.0 + 0.5;
        double cy = Math.max(a.getY(), b.getY()) + 0.5;
        double cz = (a.getZ() + b.getZ()) / 2.0 + 0.5;
        double dx = mc.player.getX() - cx;
        double dy = mc.player.getY() - cy;
        double dz = mc.player.getZ() - cz;
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