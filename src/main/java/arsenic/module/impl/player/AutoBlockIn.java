package arsenic.module.impl.player;
import arsenic.main.Arsenic;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import arsenic.utils.timer.MSTimer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import java.util.*;
@ModuleInfo(name = "AutoBlockIn", category = ModuleCategory.PLAYER)
public class AutoBlockIn extends Module {
    public final EnumProperty<AutoBlockInMode> mode = new EnumProperty<>("Mode", AutoBlockInMode.SILENT);
    public final DoubleProperty speed = new DoubleProperty("Speed", new DoubleValue(1, 20, 15, 1));
    public final DoubleProperty turnSpeed = new DoubleProperty("Turn Speed", new DoubleValue(5, 180, 140, 1));
    public final BooleanProperty showPreview = new BooleanProperty("Show Preview", true);
    private static final int[][] CARDINAL_DIRS = {{0, -1}, {-1, 0}, {1, 0}, {0, 1}};
    
    private static final int MAX_SUPPORT_DEPTH = 5;
    private static final int MAX_AIRBORNE_TICKS = 10;
    private static final int MAX_DISPLACEMENT_TICKS = 10;
    private static final double PLACE_REACH = 4.5;
    private static final double PLACE_REACH_SQ = PLACE_REACH * PLACE_REACH;
    private static final int STATE_RANGE = 6;
    private static final int STATE_DIM = STATE_RANGE * 2 + 1;
    private static final int STATE_SIZE = STATE_DIM * STATE_DIM * STATE_DIM;
    
    private static final int MAX_BFS_NODES = 12000;
    
    private static final int MAX_FACE_SOLVES_PER_NODE = 48;
    
    private static final int BFS_BEAM_MIN = 6;
    
    private static final int BFS_BEAM_MAX = 20;
    private static final long BFS_BUDGET_NS = 8_000_000L;
    
    private static final double FACE_EPS = 1e-4;
    
    private static final double PLANE_EPS = 1e-4;
    private static final double CLIP_EPS = 1e-12;
    private static final double MIN_PIECE_AREA = 1e-10;
    private static final int MAX_PIECES = 128;
    
    private static final int RESOLVE_AFTER_MISSES = 4;
    
    private static final int MAX_TARGET_RECOVERIES = 3;
    
    private static final long MIN_STEP_TIMEOUT_MS = 750L;
    
    private static final double STEP_TIMEOUT_MARGIN = 3.0;
    
    private static final double[][] DITHER = {
        {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}
    };
    // NORMAL mode (disabled)
    // private float savedYaw;
    // private float savedPitch;
    private int savedSlot;
    
    private int lastSetSlot;
    private BlockPos lockedPos;
    private int airborneTicks;
    private List<PlanStep> plan;
    private int planIndex;
    private BlockPos currentTarget;
    
    private FaceTarget currentAim;
    
    private int aimPlanIndex = -1;
    
    private int missTicks;
    
    private int ticksOnStep;
    
    private long stepDeadlineMs;
    
    private int stepRecoveries;
    private final MSTimer placeTimer = new MSTimer();
    private int verifyTicks;
    private BlockPos verifyPos;
    private int displacementTicks;
    private boolean pausedSlot;
    private double initPosX;
    private double initPosZ;
    private boolean needsReplan;
    
    private List<AxisAlignedBB> cachedEntityBoxes;
    
    private Set<BlockPos> cachedSolids;
    
    private int remainingFinalCount;
    
    private int remainingSupportCount;
    // NORMAL mode (disabled)
    // private float yawDelta;
    // private float pitchDelta;
    // private float prevPartialTicks;
    @Override
    protected void onEnable() {
        if (mc.thePlayer == null || mc.theWorld == null
                || mc.thePlayer.isDead || mc.thePlayer.getHealth() <= 0.0f) {
            setEnabled(false);
            return;
        }
        savedSlot = mc.thePlayer.inventory.currentItem;
        // NORMAL mode (disabled)
        // savedYaw = mc.thePlayer.rotationYaw;
        // savedPitch = mc.thePlayer.rotationPitch;
        lockedPos = null;
        airborneTicks = 0;
        lastSetSlot = -1;
        plan = null;
        planIndex = 0;
        currentTarget = null;
        currentAim = null;
        aimPlanIndex = -1;
        missTicks = 0;
        ticksOnStep = 0;
        stepDeadlineMs = 0;
        stepRecoveries = 0;
        verifyTicks = 0;
        verifyPos = null;
        displacementTicks = 0;
        pausedSlot = false;
        initPosX = 0;
        initPosZ = 0;
        needsReplan = false;
        cachedEntityBoxes = null;
        cachedSolids = null;
        remainingFinalCount = 0;
        remainingSupportCount = 0;
        // NORMAL mode (disabled)
        // yawDelta = 0;
        // pitchDelta = 0;
        // prevPartialTicks = 0;
        placeTimer.reset();
    }
    @Override
    protected void onDisable() {
        if (mc.thePlayer != null) {
            // NORMAL mode (disabled)
            // if (mode.getValue() == AutoBlockInMode.NORMAL) {
            //     mc.thePlayer.rotationYaw = savedYaw;
            //     mc.thePlayer.rotationPitch = savedPitch;
            // }
            mc.thePlayer.inventory.currentItem = savedSlot;
        }
        plan = null;
        currentTarget = null;
        currentAim = null;
        aimPlanIndex = -1;
        verifyPos = null;
        // NORMAL mode (disabled)
        // yawDelta = 0;
        // pitchDelta = 0;
    }

    private boolean shouldAutoDisable() {
        return mc.thePlayer == null || mc.theWorld == null
                || mc.thePlayer.isDead || mc.thePlayer.getHealth() <= 0.0f;
    }
    
    private boolean buildPlan() {
        int px = lockedPos.getX(), py = lockedPos.getY(), pz = lockedPos.getZ();
        BlockPos under = new BlockPos(px, py - 1, pz);
        Block underBlock = mc.theWorld.getBlockState(under).getBlock();
        if (underBlock instanceof BlockAir || underBlock.getMaterial().isReplaceable()) {
            PlayerUtils.addWaterMarkedMessageToChat("§c[AutoBlockIn] No ground beneath player.");
            return false;
        }
        cacheEntityBoxes();
        cacheSolids();
        List<BlockPos> targets = new ArrayList<>(9);
        targets.add(new BlockPos(px, py + 2, pz));
        List<Pillar> pillars = new ArrayList<>(4);
        for (int[] d : CARDINAL_DIRS) {
            pillars.add(new Pillar(new BlockPos(px + d[0], py, pz + d[1]),
                                   new BlockPos(px + d[0], py + 1, pz + d[1])));
        }
        final float yaw = mc.thePlayer.rotationYaw;
        pillars.sort(Comparator.comparingDouble(p ->
            Math.abs(MathHelper.wrapAngleTo180_float(yaw - pillarAngle(p, px, pz)))));
        for (Pillar p : pillars) {
            targets.add(p.feet);
            targets.add(p.head);
        }
        Set<BlockPos> virtualWorld = new HashSet<>();
        List<PlanStep> fullPlan = new ArrayList<>();
        for (BlockPos target : targets) {
            if (!isAirOrReplaceable(target, virtualWorld) || intersectsAnyEntity(target)) continue;
            List<PlanStep> chain = tryPlan(target, virtualWorld, true, MAX_SUPPORT_DEPTH);
            if (chain == null || chain.isEmpty()) continue;
            for (int i = 0; i < chain.size(); i++) {
                PlanStep s = chain.get(i);
                boolean support = i < chain.size() - 1;
                PlanStep marked = new PlanStep(s.placePos, s.neighbor, s.facing, support);
                virtualWorld.add(marked.placePos);
                fullPlan.add(marked);
            }
        }
        if (fullPlan.isEmpty()) {
            PlayerUtils.addWaterMarkedMessageToChat("§a[AutoBlockIn] Already surrounded!");
            plan = new ArrayList<>();
            remainingFinalCount = 0;
            remainingSupportCount = 0;
            return true;
        }
        int finals = 0, supports = 0;
        for (PlanStep s : fullPlan) {
            if (s.isSupport) supports++; else finals++;
        }
        int totalNeeded = fullPlan.size();
        if (countAllPlaceableBlocks() < totalNeeded) {
            PlayerUtils.addWaterMarkedMessageToChat("§c[AutoBlockIn] Not enough blocks to surround.");
            return false;
        }
        remainingFinalCount = finals;
        remainingSupportCount = supports;
        plan = fullPlan;
        planIndex = 0;
        initPosX = mc.thePlayer.posX;
        initPosZ = mc.thePlayer.posZ;
        ensureSlotForCurrentStep(fullPlan.get(0));
        return true;
    }
    
    private List<PlanStep> tryPlan(BlockPos target, Set<BlockPos> world, boolean silentFail, int maxDepth) {
        try {
            return bfsPlan(target, world, maxDepth);
        } catch (Exception e) {
            if (!silentFail) {
                PlayerUtils.addWaterMarkedMessageToChat("§e[AutoBlockIn] Pathfinder error, skipping block at "
                    + target.getX() + "," + target.getY() + "," + target.getZ() + ".");
            }
            return null;
        }
    }
    private List<PlanStep> bfsPlan(BlockPos target, Set<BlockPos> world, int maxDepth) {
        Vec3 eyes = actualEye();
        long deadline = System.nanoTime() + BFS_BUDGET_NS;
        float curYaw = mc.thePlayer.rotationYaw;
        float curPitch = mc.thePlayer.rotationPitch;
        FaceTarget direct = solveFaceTarget(target, world, eyes, curYaw, curPitch);
        if (direct != null) {
            return Collections.singletonList(new PlanStep(target, direct, false));
        }
        if (maxDepth <= 0) return null;
        Set<BitSet> visited = new HashSet<>();
        visited.add(encodeState(world));
        Queue<BfsNode> queue = new LinkedList<>();
        queue.add(new BfsNode(new ArrayList<>(), new HashSet<>(world)));
        int totalNodes = 0;
        for (int depth = 1; depth <= maxDepth && totalNodes < MAX_BFS_NODES; depth++) {
            if (System.nanoTime() > deadline) return null;
            List<BfsNode> level = new ArrayList<>(queue);
            queue.clear();
            for (BfsNode node : level) {
                if (totalNodes >= MAX_BFS_NODES) break;
                if (System.nanoTime() > deadline) return null;
                List<PlacementCandidate> candidates = getValidPlacements(
                    node.state, eyes, target, curYaw, curPitch, depth, deadline);
                for (PlacementCandidate pc : candidates) {
                    if (totalNodes++ >= MAX_BFS_NODES) break;
                    if (System.nanoTime() > deadline) return null;
                    Set<BlockPos> newState = new HashSet<>(node.state);
                    newState.add(pc.placePos);
                    if (!visited.add(encodeState(newState))) continue;
                    List<PlanStep> newChain = new ArrayList<>(node.chain);
                    newChain.add(new PlanStep(pc.placePos, pc.neighbor, pc.facing, true));
                    FaceTarget targetInfo = solveFaceTarget(target, newState, eyes, curYaw, curPitch);
                    if (targetInfo != null) {
                        newChain.add(new PlanStep(target, targetInfo, false));
                        return newChain;
                    }
                    queue.add(new BfsNode(newChain, newState));
                }
            }
        }
        return null;
    }
    
    private List<PlacementCandidate> getValidPlacements(Set<BlockPos> virtualState, Vec3 eyes, BlockPos target,
                                                        float curYaw, float curPitch, int depth, long deadline) {
        int expand = Math.min(MAX_SUPPORT_DEPTH + 1, depth + 2);
        List<ScoredPos> scored = new ArrayList<>();
        for (int dx = -expand; dx <= expand; dx++) {
            for (int dy = -expand; dy <= expand; dy++) {
                for (int dz = -expand; dz <= expand; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos pos = target.add(dx, dy, dz);
                    if (eyes.yCoord < target.getY() && pos.getY() < target.getY()) continue;
                    if (virtualState.contains(pos)) continue;
                    if (lockedPos != null && (pos.equals(lockedPos) || pos.equals(lockedPos.up()))) continue;
                    if (!isAirOrReplaceable(pos)) continue;
                    if (intersectsAnyEntity(pos)) continue;
                    if (minDistSqToBlock(eyes, pos) > PLACE_REACH_SQ) continue;
                    if (!hasSolidNeighbor(pos, virtualState)) continue;
                    int manh = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    boolean adjTarget = manh == 1;
                    boolean fillsTargetNeighbor = adjTarget;
                    double towardEye = 0;
                    if (adjTarget) {
                        double cx = pos.getX() + 0.5 - eyes.xCoord;
                        double cy = pos.getY() + 0.5 - eyes.yCoord;
                        double cz = pos.getZ() + 0.5 - eyes.zCoord;
                        towardEye = -(cx * cx + cy * cy + cz * cz);
                    }
                    double score = (fillsTargetNeighbor ? 1_000_000.0 : 0.0)
                        - manh * 1_000.0
                        + towardEye
                        - Math.sqrt(pos.distanceSq(eyes.xCoord, eyes.yCoord, eyes.zCoord));
                    scored.add(new ScoredPos(pos, score, adjTarget));
                }
            }
        }
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        List<PlacementCandidate> valid = new ArrayList<>();
        int solves = 0;
        int nonAdjAccepted = 0;
        for (ScoredPos sp : scored) {
            if (System.nanoTime() > deadline) break;
            if (solves >= MAX_FACE_SOLVES_PER_NODE) break;
            if (!sp.adjTarget && valid.size() >= BFS_BEAM_MIN && nonAdjAccepted >= BFS_BEAM_MAX) break;
            solves++;
            FaceTarget info = solveFaceTarget(sp.pos, virtualState, eyes, curYaw, curPitch);
            if (info == null) continue;
            valid.add(new PlacementCandidate(sp.pos, info));
            if (!sp.adjTarget) nonAdjAccepted++;
        }
        return valid;
    }
    private boolean hasSolidNeighbor(BlockPos pos, Set<BlockPos> virtualState) {
        for (EnumFacing f : EnumFacing.values()) {
            BlockPos n = pos.offset(f);
            if (virtualState.contains(n) || (cachedSolids != null && cachedSolids.contains(n)) || isRealSolid(n))
                return true;
        }
        return false;
    }
    private void cacheEntityBoxes() {
        cachedEntityBoxes = new ArrayList<>();
        if (lockedPos == null || mc.theWorld == null) return;
        AxisAlignedBB area = new AxisAlignedBB(
            lockedPos.getX() - 6, lockedPos.getY() - 2, lockedPos.getZ() - 6,
            lockedPos.getX() + 7, lockedPos.getY() + 5, lockedPos.getZ() + 7
        );
        @SuppressWarnings("unchecked")
        List<Entity> entities = mc.theWorld.getEntitiesWithinAABB(Entity.class, area);
        for (Entity e : entities) {
            if (e == null || e == mc.thePlayer || !e.canBeCollidedWith()) continue;
            cachedEntityBoxes.add(e.getEntityBoundingBox());
        }
    }
    private void cacheSolids() {
        cachedSolids = new HashSet<>();
        if (lockedPos == null || mc.theWorld == null) return;
        for (int x = lockedPos.getX() - 6; x <= lockedPos.getX() + 6; x++) {
            for (int y = lockedPos.getY() - 2; y <= lockedPos.getY() + 4; y++) {
                for (int z = lockedPos.getZ() - 6; z <= lockedPos.getZ() + 6; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (pos.equals(lockedPos) || pos.equals(lockedPos.up())) continue;
                    Block b = mc.theWorld.getBlockState(pos).getBlock();
                    if (!(b instanceof BlockAir) && !b.getMaterial().isReplaceable())
                        cachedSolids.add(pos);
                }
            }
        }
    }
    private boolean intersectsAnyEntity(BlockPos pos) {
        AxisAlignedBB blockBB = new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(),
                                                   pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        if (mc.thePlayer.getEntityBoundingBox().intersectsWith(blockBB)) return true;
        if (cachedEntityBoxes != null) {
            for (AxisAlignedBB bb : cachedEntityBoxes) {
                if (bb.intersectsWith(blockBB)) return true;
            }
            return false;
        }
        @SuppressWarnings("unchecked")
        List<Entity> entities = mc.theWorld.getEntitiesWithinAABBExcludingEntity(
            mc.thePlayer, blockBB.expand(0.05, 0.05, 0.05));
        for (Entity e : entities) {
            if (e != null && e.canBeCollidedWith()) return true;
        }
        return false;
    }
    private boolean isRealSolid(BlockPos pos) {
        if (lockedPos != null && (pos.equals(lockedPos) || pos.equals(lockedPos.up()))) return false;
        Block b = mc.theWorld.getBlockState(pos).getBlock();
        return !(b instanceof BlockAir) && !b.getMaterial().isReplaceable();
    }
    private Vec3 actualEye() {
        return mc.thePlayer.getPositionEyes(1f);
    }
    private float pillarAngle(Pillar p, int px, int pz) {
        int dx = p.feet.getX() - px, dz = p.feet.getZ() - pz;
        if (dx == 0 && dz == -1) return 180;
        if (dx == 0 && dz == 1) return 0;
        if (dx == 1 && dz == 0) return -90;
        if (dx == -1 && dz == 0) return 90;
        return 0;
    }
    private int stateIndex(BlockPos pos) {
        if (lockedPos == null) return -1;
        int dx = pos.getX() - lockedPos.getX();
        int dy = pos.getY() - lockedPos.getY();
        int dz = pos.getZ() - lockedPos.getZ();
        if (Math.abs(dx) > STATE_RANGE || Math.abs(dy) > STATE_RANGE || Math.abs(dz) > STATE_RANGE) return -1;
        return (dx + STATE_RANGE)
            + STATE_DIM * ((dy + STATE_RANGE)
            + STATE_DIM * (dz + STATE_RANGE));
    }
    private BitSet encodeState(Set<BlockPos> state) {
        BitSet bits = new BitSet(STATE_SIZE);
        for (BlockPos p : state) {
            int i = stateIndex(p);
            if (i >= 0) bits.set(i);
        }
        return bits;
    }
    
    private static Vec3 lookVector(float yaw, float pitch) {
        float f  = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float f1 = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float f2 = -MathHelper.cos(-pitch * 0.017453292F);
        float f3 = MathHelper.sin(-pitch * 0.017453292F);
        return new Vec3((double) (f1 * f2), (double) f3, (double) (f * f2));
    }
    
    private static float[] rotationsTo(Vec3 eyes, Vec3 p) {
        double dx = p.xCoord - eyes.xCoord;
        double dy = p.yCoord - eyes.yCoord;
        double dz = p.zCoord - eyes.zCoord;
        double dist = MathHelper.sqrt_double(dx * dx + dz * dz);
        return new float[]{
            (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0),
            (float) (-Math.toDegrees(Math.atan2(dy, dist)))
        };
    }
    
    private static double angularDist(float y1, float p1, float y2, float p2) {
        Vec3 a = lookVector(y1, p1);
        Vec3 b = lookVector(y2, p2);
        double dot = a.xCoord * b.xCoord + a.yCoord * b.yCoord + a.zCoord * b.zCoord;
        return Math.acos(MathHelper.clamp_double(dot, -1.0, 1.0));
    }
    
    private MovingObjectPosition rotationRayTrace(Vec3 eyes, float yaw, float pitch, Set<BlockPos> virtualSolids) {
        Vec3 dir = lookVector(yaw, pitch);
        Vec3 end = eyes.addVector(dir.xCoord * PLACE_REACH, dir.yCoord * PLACE_REACH, dir.zCoord * PLACE_REACH);
        if (virtualSolids == null || virtualSolids.isEmpty()) {
            if (mc.theWorld == null) return null;
            return mc.theWorld.rayTraceBlocks(eyes, end, false, false, true);
        }
        return rayTraceVirtual(eyes, end, virtualSolids);
    }
    
    private static double settledFloorRad() {
        return Math.toRadians(RotationUtils.getGCD()) * 3.0 + 0.0015;
    }
    
    private static double minStableRad() {
        return settledFloorRad() * 2.0;
    }
    
    private FaceTarget solveFaceTarget(BlockPos target, Set<BlockPos> virtualSolids, Vec3 eyes,
                                       float curYaw, float curPitch) {
        FaceTarget bestStable = null;
        double bestStableAng = Double.MAX_VALUE;
        FaceTarget bestFallback = null;
        for (EnumFacing facing : EnumFacing.values()) {
            BlockPos neighbor = target.offset(facing.getOpposite());
            if (!isSolid(neighbor, virtualSolids)) continue;
            if (minDistSqToBlock(eyes, neighbor) > PLACE_REACH_SQ) continue;
            FaceTarget ft = solveFace(neighbor, facing, virtualSolids, eyes);
            if (ft == null) continue;
            if (ft.clearanceRad >= minStableRad()) {
                double ang = angularDist(curYaw, curPitch, ft.yaw, ft.pitch);
                if (ang < bestStableAng) {
                    bestStableAng = ang;
                    bestStable = ft;
                }
            }
            if (bestFallback == null || ft.clearanceRad > bestFallback.clearanceRad) {
                bestFallback = ft;
            }
        }
        return bestStable != null ? bestStable : bestFallback;
    }
    
    private FaceTarget solveFace(BlockPos neighbor, EnumFacing facing,
                                 Set<BlockPos> virtualSolids, Vec3 eyes) {
        FaceFrame f = FaceFrame.of(neighbor, facing);
        double nE = f.normal.xCoord * eyes.xCoord + f.normal.yCoord * eyes.yCoord + f.normal.zCoord * eyes.zCoord;
        double h = nE - f.d;
        if (h < 1e-3 || h >= PLACE_REACH) return null;
        double reachR = Math.sqrt(PLACE_REACH * PLACE_REACH - h * h);
        double[] footUV = f.toUV(eyes.xCoord - f.normal.xCoord * h,
                                 eyes.yCoord - f.normal.yCoord * h,
                                 eyes.zCoord - f.normal.zCoord * h);
        List<double[]> region = new ArrayList<>(4);
        region.add(new double[]{FACE_EPS, FACE_EPS});
        region.add(new double[]{1.0 - FACE_EPS, FACE_EPS});
        region.add(new double[]{1.0 - FACE_EPS, 1.0 - FACE_EPS});
        region.add(new double[]{FACE_EPS, 1.0 - FACE_EPS});
        for (int k = 0; k < 12 && region.size() >= 3; k++) {
            double a0 = k * Math.PI / 6.0, a1 = (k + 1) * Math.PI / 6.0;
            region = clipHalfplane(region,
                new double[]{footUV[0] + reachR * Math.cos(a0), footUV[1] + reachR * Math.sin(a0)},
                new double[]{footUV[0] + reachR * Math.cos(a1), footUV[1] + reachR * Math.sin(a1)},
                true);
        }
        if (region.size() < 3) return null;
        List<List<double[]>> pieces = new ArrayList<>();
        pieces.add(region);
        AxisAlignedBB scanMask = scanMaskAround(eyes, neighbor);
        int x0 = MathHelper.floor_double(scanMask.minX), x1 = MathHelper.floor_double(scanMask.maxX);
        int y0 = Math.max(0, MathHelper.floor_double(scanMask.minY));
        int y1 = Math.min(255, MathHelper.floor_double(scanMask.maxY));
        int z0 = MathHelper.floor_double(scanMask.minZ), z1 = MathHelper.floor_double(scanMask.maxZ);
        for (int x = x0; x <= x1 && !pieces.isEmpty(); x++) {
            for (int y = y0; y <= y1 && !pieces.isEmpty(); y++) {
                for (int z = z0; z <= z1 && !pieces.isEmpty(); z++) {
                    List<AxisAlignedBB> boxes = collisionBoxesAt(new BlockPos(x, y, z), virtualSolids, scanMask);
                    for (AxisAlignedBB box : boxes) {
                        List<double[]> projected = projectBoxToUV(box, f, nE, eyes);
                        if (projected.size() < 3) continue;
                        List<double[]> hull = convexHull(projected);
                        if (hull.size() < 3 || polygonArea(hull) < MIN_PIECE_AREA) continue;
                        pieces = subtractHull(pieces, hull);
                        if (pieces.isEmpty()) return null;
                        if (pieces.size() > MAX_PIECES) {
                            pieces.sort((a, b) -> Double.compare(polygonArea(b), polygonArea(a)));
                            pieces = new ArrayList<>(pieces.subList(0, MAX_PIECES));
                        }
                    }
                }
            }
        }
        if (pieces.isEmpty()) return null;
        List<PieceCandidate> candidates = new ArrayList<>(pieces.size());
        for (List<double[]> piece : pieces) {
            double[] cc = chebyshevCenter(piece);
            if (cc != null) candidates.add(new PieceCandidate(piece, cc));
        }
        if (candidates.isEmpty()) return null;
        candidates.sort((a, b) -> Double.compare(b.cc[2], a.cc[2]));
        int tried = 0;
        for (PieceCandidate pc : candidates) {
            if (++tried > 4) break;
            double[][] points = { {pc.cc[0], pc.cc[1]}, centroid(pc.poly) };
            for (double[] uv : points) {
                Vec3 w = f.toWorld(uv[0], uv[1]);
                float[] rots = rotationsTo(eyes, w);
                MovingObjectPosition mop = rotationRayTrace(eyes, rots[0], rots[1], virtualSolids);
                if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) continue;
                if (!mop.getBlockPos().equals(neighbor) || mop.sideHit != facing) continue;
                double[] nb = nearestBoundary(pc.poly, uv[0], uv[1]);
                Vec3 wb = f.toWorld(nb[1], nb[2]);
                float[] rotsB = rotationsTo(eyes, wb);
                double clearance = angularDist(rots[0], rots[1], rotsB[0], rotsB[1]);
                return new FaceTarget(neighbor, facing, rots[0], rots[1], mop.hitVec, clearance);
            }
        }
        return null;
    }
    private AxisAlignedBB scanMaskAround(Vec3 eyes, BlockPos neighbor) {
        double mnx = Math.min(eyes.xCoord, neighbor.getX()) - 1.0;
        double mny = Math.min(eyes.yCoord, neighbor.getY()) - 1.0;
        double mnz = Math.min(eyes.zCoord, neighbor.getZ()) - 1.0;
        double mxx = Math.max(eyes.xCoord, neighbor.getX() + 1.0) + 1.0;
        double mxy = Math.max(eyes.yCoord, neighbor.getY() + 1.0) + 1.0;
        double mxz = Math.max(eyes.zCoord, neighbor.getZ() + 1.0) + 1.0;
        return new AxisAlignedBB(mnx, mny, mnz, mxx, mxy, mxz);
    }
    
    private List<AxisAlignedBB> collisionBoxesAt(BlockPos pos, Set<BlockPos> virtualSolids, AxisAlignedBB mask) {
        if (lockedPos != null && (pos.equals(lockedPos) || pos.equals(lockedPos.up())))
            return Collections.emptyList();
        if (virtualSolids != null && virtualSolids.contains(pos)) {
            return Collections.singletonList(new AxisAlignedBB(
                pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1));
        }
        IBlockState state = mc.theWorld.getBlockState(pos);
        Block block = state.getBlock();
        if (block == null || block instanceof BlockAir || block.getMaterial().isReplaceable())
            return Collections.emptyList();
        if (!block.canCollideCheck(state, false)) return Collections.emptyList();
        try {
            if (block.getCollisionBoundingBox(mc.theWorld, pos, state) == null)
                return Collections.emptyList();
            List<AxisAlignedBB> list = new ArrayList<>();
            block.addCollisionBoxesToList(mc.theWorld, pos, state, mask, list, mc.thePlayer);
            return list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
    
    private List<double[]> projectBoxToUV(AxisAlignedBB box, FaceFrame f, double nE, Vec3 eyes) {
        double lo = f.d + PLANE_EPS;
        double hi = nE - PLANE_EPS;
        if (hi <= lo) return Collections.emptyList();
        double[] xs = {box.minX, box.maxX};
        double[] ys = {box.minY, box.maxY};
        double[] zs = {box.minZ, box.maxZ};
        double[][] c = new double[8][3];
        double[] s = new double[8];
        for (int i = 0; i < 8; i++) {
            c[i][0] = xs[i & 1];
            c[i][1] = ys[(i >> 1) & 1];
            c[i][2] = zs[(i >> 2) & 1];
            s[i] = f.normal.xCoord * c[i][0] + f.normal.yCoord * c[i][1] + f.normal.zCoord * c[i][2];
        }
        List<double[]> out = new ArrayList<>(16);
        for (int i = 0; i < 8; i++) {
            if (s[i] > lo && s[i] < hi) addProjection(out, c[i], s[i], f, nE, eyes);
        }
        for (int i = 0; i < 8; i++) {
            for (int bit = 1; bit <= 4; bit <<= 1) {
                int j = i ^ bit;
                if (j <= i) continue;
                for (int k = 0; k < 2; k++) {
                    double boundary = k == 0 ? lo : hi;
                    double d1 = s[i] - boundary, d2 = s[j] - boundary;
                    if (d1 * d2 < 0.0) {
                        double t = d1 / (d1 - d2);
                        double[] p = {
                            c[i][0] + (c[j][0] - c[i][0]) * t,
                            c[i][1] + (c[j][1] - c[i][1]) * t,
                            c[i][2] + (c[j][2] - c[i][2]) * t
                        };
                        addProjection(out, p, boundary, f, nE, eyes);
                    }
                }
            }
        }
        return out;
    }
    private void addProjection(List<double[]> out, double[] c, double s, FaceFrame f, double nE, Vec3 eyes) {
        double t = (f.d - nE) / (s - nE);
        out.add(f.toUV(eyes.xCoord + (c[0] - eyes.xCoord) * t,
                       eyes.yCoord + (c[1] - eyes.yCoord) * t,
                       eyes.zCoord + (c[2] - eyes.zCoord) * t));
    }
    private static double cross2(double ax, double ay, double bx, double by) {
        return ax * by - ay * bx;
    }
    
    private static List<double[]> clipHalfplane(List<double[]> poly, double[] a, double[] b, boolean keepLeft) {
        List<double[]> out = new ArrayList<>(poly.size() + 1);
        double ex = b[0] - a[0], ey = b[1] - a[1];
        int n = poly.size();
        for (int i = 0; i < n; i++) {
            double[] p = poly.get(i);
            double[] q = poly.get((i + 1) % n);
            double cp = cross2(ex, ey, p[0] - a[0], p[1] - a[1]);
            double cq = cross2(ex, ey, q[0] - a[0], q[1] - a[1]);
            boolean inP = keepLeft ? cp >= -CLIP_EPS : cp <= CLIP_EPS;
            boolean inQ = keepLeft ? cq >= -CLIP_EPS : cq <= CLIP_EPS;
            if (inP) out.add(p);
            if (inP != inQ && Math.abs(cp - cq) > 1e-18) {
                double t = cp / (cp - cq);
                out.add(new double[]{p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t});
            }
        }
        return out;
    }
    
    private static List<List<double[]>> subtractHull(List<List<double[]>> pieces, List<double[]> hull) {
        List<List<double[]>> next = new ArrayList<>();
        for (List<double[]> piece : pieces) {
            List<double[]> remainder = piece;
            for (int i = 0; i < hull.size(); i++) {
                double[] a = hull.get(i);
                double[] b = hull.get((i + 1) % hull.size());
                List<double[]> outside = clipHalfplane(remainder, a, b, false);
                if (outside.size() >= 3 && polygonArea(outside) > MIN_PIECE_AREA) next.add(outside);
                remainder = clipHalfplane(remainder, a, b, true);
                if (remainder.size() < 3) break;
            }
        }
        return next;
    }
    
    private static List<double[]> convexHull(List<double[]> pts) {
        List<double[]> p = new ArrayList<>(pts);
        p.sort((a, b) -> a[0] != b[0] ? Double.compare(a[0], b[0]) : Double.compare(a[1], b[1]));
        List<double[]> hull = new ArrayList<>(p.size() + 1);
        for (double[] pt : p) {
            while (hull.size() >= 2) {
                double[] b = hull.get(hull.size() - 1), a = hull.get(hull.size() - 2);
                if (cross2(b[0] - a[0], b[1] - a[1], pt[0] - a[0], pt[1] - a[1]) <= CLIP_EPS)
                    hull.remove(hull.size() - 1);
                else break;
            }
            hull.add(pt);
        }
        int lowerSize = hull.size();
        for (int i = p.size() - 2; i >= 0; i--) {
            double[] pt = p.get(i);
            while (hull.size() > lowerSize) {
                double[] b = hull.get(hull.size() - 1), a = hull.get(hull.size() - 2);
                if (cross2(b[0] - a[0], b[1] - a[1], pt[0] - a[0], pt[1] - a[1]) <= CLIP_EPS)
                    hull.remove(hull.size() - 1);
                else break;
            }
            hull.add(pt);
        }
        if (hull.size() > 1) hull.remove(hull.size() - 1);
        return hull;
    }
    private static double polygonArea(List<double[]> poly) {
        double a = 0;
        for (int i = 0, n = poly.size(); i < n; i++) {
            double[] p = poly.get(i), q = poly.get((i + 1) % n);
            a += p[0] * q[1] - q[0] * p[1];
        }
        return a * 0.5;
    }
    private static double[] centroid(List<double[]> poly) {
        double u = 0, v = 0;
        for (double[] p : poly) { u += p[0]; v += p[1]; }
        return new double[]{u / poly.size(), v / poly.size()};
    }
    
    private static List<double[]> shrink(List<double[]> poly, double r) {
        List<double[]> out = poly;
        int n = poly.size();
        for (int i = 0; i < n && out.size() >= 3; i++) {
            double[] a = poly.get(i), b = poly.get((i + 1) % n);
            double ex = b[0] - a[0], ey = b[1] - a[1];
            double len = Math.sqrt(ex * ex + ey * ey);
            if (len < 1e-12) continue;
            List<double[]> clipped = new ArrayList<>(out.size() + 1);
            int m = out.size();
            for (int j = 0; j < m; j++) {
                double[] p = out.get(j), q = out.get((j + 1) % m);
                double sp = cross2(ex, ey, p[0] - a[0], p[1] - a[1]) / len - r;
                double sq = cross2(ex, ey, q[0] - a[0], q[1] - a[1]) / len - r;
                boolean inP = sp >= -CLIP_EPS, inQ = sq >= -CLIP_EPS;
                if (inP) clipped.add(p);
                if (inP != inQ && Math.abs(sp - sq) > 1e-18) {
                    double t = sp / (sp - sq);
                    clipped.add(new double[]{p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t});
                }
            }
            out = clipped;
        }
        return out;
    }
    
    private static double[] chebyshevCenter(List<double[]> poly) {
        double lo = 0.0, hi = 0.5;
        List<double[]> best = poly;
        for (int it = 0; it < 12; it++) {
            double mid = (lo + hi) * 0.5;
            List<double[]> s = shrink(poly, mid);
            if (s.size() >= 3) { lo = mid; best = s; } else { hi = mid; }
        }
        if (best.isEmpty()) return null;
        double[] c = centroid(best);
        return new double[]{c[0], c[1], lo};
    }
    
    private static double[] nearestBoundary(List<double[]> poly, double u, double v) {
        double best = Double.MAX_VALUE, fu = u, fv = v;
        int n = poly.size();
        for (int i = 0; i < n; i++) {
            double[] a = poly.get(i), b = poly.get((i + 1) % n);
            double ex = b[0] - a[0], ey = b[1] - a[1];
            double lenSq = ex * ex + ey * ey;
            double t = lenSq < 1e-18 ? 0.0 : ((u - a[0]) * ex + (v - a[1]) * ey) / lenSq;
            t = Math.max(0.0, Math.min(1.0, t));
            double px = a[0] + ex * t, py = a[1] + ey * t;
            double d = Math.hypot(u - px, v - py);
            if (d < best) { best = d; fu = px; fv = py; }
        }
        return new double[]{best, fu, fv};
    }
    private MovingObjectPosition rayTraceVirtual(Vec3 from, Vec3 to, Set<BlockPos> virtualSolids) {
        if (from.equals(to) || mc.theWorld == null) return null;
        int x = MathHelper.floor_double(from.xCoord);
        int y = MathHelper.floor_double(from.yCoord);
        int z = MathHelper.floor_double(from.zCoord);
        int endX = MathHelper.floor_double(to.xCoord);
        int endY = MathHelper.floor_double(to.yCoord);
        int endZ = MathHelper.floor_double(to.zCoord);
        for (int budget = STATE_SIZE + 8; budget-- > 0; ) {
            MovingObjectPosition hit = traceCell(from, to, x, y, z, virtualSolids);
            if (hit != null) return hit;
            if (x == endX && y == endY && z == endZ) return null;
            double dx = to.xCoord - from.xCoord;
            double dy = to.yCoord - from.yCoord;
            double dz = to.zCoord - from.zCoord;
            double xt = nextBoundaryT(from.xCoord, dx, x);
            double yt = nextBoundaryT(from.yCoord, dy, y);
            double zt = nextBoundaryT(from.zCoord, dz, z);
            if (xt <= yt && xt <= zt) {
                if (xt > 1.0) return null;
                x += dx > 0 ? 1 : -1;
            } else if (yt <= zt) {
                if (yt > 1.0) return null;
                y += dy > 0 ? 1 : -1;
            } else {
                if (zt > 1.0) return null;
                z += dz > 0 ? 1 : -1;
            }
        }
        return null;
    }
    private double nextBoundaryT(double origin, double delta, int cell) {
        if (delta == 0.0) return Double.POSITIVE_INFINITY;
        double boundary = delta > 0 ? (cell + 1) : cell;
        if (delta < 0 && origin == boundary) boundary -= 1;
        return (boundary - origin) / delta;
    }
    private MovingObjectPosition traceCell(Vec3 from, Vec3 to, int x, int y, int z, Set<BlockPos> virtualSolids) {
        BlockPos pos = new BlockPos(x, y, z);
        if (lockedPos != null && (pos.equals(lockedPos) || pos.equals(lockedPos.up()))) return null;
        if (virtualSolids.contains(pos)) {
            AxisAlignedBB bb = new AxisAlignedBB(x, y, z, x + 1, y + 1, z + 1);
            return bb.calculateIntercept(from, to) == null ? null : mopFromIntercept(bb, from, to, pos);
        }
        IBlockState state = mc.theWorld.getBlockState(pos);
        Block block = state.getBlock();
        if (block == null || block instanceof BlockAir || block.getMaterial().isReplaceable()) return null;
        if (!block.canCollideCheck(state, false)) return null;
        return block.collisionRayTrace(mc.theWorld, pos, from, to);
    }
    private MovingObjectPosition mopFromIntercept(AxisAlignedBB bb, Vec3 from, Vec3 to, BlockPos pos) {
        MovingObjectPosition intercept = bb.calculateIntercept(from, to);
        if (intercept == null) return null;
        return new MovingObjectPosition(intercept.hitVec, intercept.sideHit, pos);
    }
    private boolean isSolid(BlockPos pos, Set<BlockPos> virtualSolids) {
        if (lockedPos != null && (pos.equals(lockedPos) || pos.equals(lockedPos.up()))) return false;
        if (virtualSolids != null && virtualSolids.contains(pos)) return true;
        Block b = mc.theWorld.getBlockState(pos).getBlock();
        return !(b instanceof BlockAir) && !b.getMaterial().isReplaceable();
    }
    private double minDistSqToBlock(Vec3 eyes, BlockPos pos) {
        double cx = MathHelper.clamp_double(eyes.xCoord, pos.getX(), pos.getX() + 1);
        double cy = MathHelper.clamp_double(eyes.yCoord, pos.getY(), pos.getY() + 1);
        double cz = MathHelper.clamp_double(eyes.zCoord, pos.getZ(), pos.getZ() + 1);
        double dx = cx - eyes.xCoord, dy = cy - eyes.yCoord, dz = cz - eyes.zCoord;
        return dx * dx + dy * dy + dz * dz;
    }
    
    private static int materialPriority(Block b) {
        if (b == null) return -1;
        if (b == Blocks.sponge) return 70;
        if (b == Blocks.obsidian) return 60;
        if (b == Blocks.end_stone) return 50;
        if (b == Blocks.glass || b == Blocks.stained_glass) return 40;
        if (b == Blocks.planks || b == Blocks.log || b == Blocks.log2) return 30;
        if (b == Blocks.hardened_clay || b == Blocks.stained_hardened_clay) return 20;
        if (b == Blocks.wool) return 10;
        return 0;
    }
    private static boolean isPlaceableBlockItem(ItemStack s) {
        return s != null && s.stackSize > 0 && s.getItem() instanceof ItemBlock;
    }
    private int countAllPlaceableBlocks() {
        int count = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.thePlayer.inventory.mainInventory[i];
            if (isPlaceableBlockItem(s)) count += s.stackSize;
        }
        return count;
    }
    private int countBlocksWithPriorityAtLeast(int minPriority) {
        int count = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.thePlayer.inventory.mainInventory[i];
            if (!isPlaceableBlockItem(s)) continue;
            Block b = ((ItemBlock) s.getItem()).getBlock();
            if (materialPriority(b) >= minPriority) count += s.stackSize;
        }
        return count;
    }
    
    private int findSlotForStep(boolean support) {
        int bestSlot = -1;
        int bestPri = support ? Integer.MAX_VALUE : Integer.MIN_VALUE;
        int bestSize = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.thePlayer.inventory.mainInventory[i];
            if (!isPlaceableBlockItem(s)) continue;
            Block b = ((ItemBlock) s.getItem()).getBlock();
            int pri = materialPriority(b);
            if (support) {
                if (remainingFinalCount > 0 && pri > 0) {
                    int strongEnough = countBlocksWithPriorityAtLeast(pri);
                    int weaker = countAllPlaceableBlocks() - strongEnough;
                    if (strongEnough - 1 < remainingFinalCount && weaker > 0) continue;
                }
                if (pri < bestPri || (pri == bestPri && s.stackSize > bestSize)) {
                    bestPri = pri;
                    bestSize = s.stackSize;
                    bestSlot = i;
                }
            } else {
                if (pri > bestPri || (pri == bestPri && s.stackSize > bestSize)) {
                    bestPri = pri;
                    bestSize = s.stackSize;
                    bestSlot = i;
                }
            }
        }
        if (bestSlot == -1) {
            for (int i = 0; i < 9; i++) {
                ItemStack s = mc.thePlayer.inventory.mainInventory[i];
                if (!isPlaceableBlockItem(s)) continue;
                Block b = ((ItemBlock) s.getItem()).getBlock();
                int pri = materialPriority(b);
                if (support) {
                    if (pri < bestPri || (pri == bestPri && s.stackSize > bestSize)) {
                        bestPri = pri;
                        bestSize = s.stackSize;
                        bestSlot = i;
                    }
                } else if (pri > bestPri || (pri == bestPri && s.stackSize > bestSize)) {
                    bestPri = pri;
                    bestSize = s.stackSize;
                    bestSlot = i;
                }
            }
        }
        return bestSlot;
    }
    
    private boolean ensureSlotForCurrentStep(PlanStep step) {
        if (step == null) return false;
        if (lastSetSlot != -1 && mc.thePlayer.inventory.currentItem != lastSetSlot) {
            pausedSlot = true;
            return false;
        }
        pausedSlot = false;
        int slot = findSlotForStep(step.isSupport);
        if (slot == -1) return false;
        if (mc.thePlayer.inventory.currentItem != slot) {
            mc.thePlayer.inventory.currentItem = slot;
        }
        lastSetSlot = slot;
        return true;
    }
    private void advancePlan() {
        if (plan != null && planIndex >= 0 && planIndex < plan.size()) {
            PlanStep done = plan.get(planIndex);
            if (done.isSupport) {
                if (remainingSupportCount > 0) remainingSupportCount--;
            } else {
                if (remainingFinalCount > 0) remainingFinalCount--;
            }
        }
        planIndex++;
        currentTarget = null;
        currentAim = null;
        aimPlanIndex = -1;
        missTicks = 0;
        ticksOnStep = 0;
        stepDeadlineMs = 0;
        stepRecoveries = 0;
    }
    
    private void armStepTimeout(FaceTarget aim) {
        if (aim == null) {
            stepDeadlineMs = System.currentTimeMillis() + MIN_STEP_TIMEOUT_MS;
            return;
        }
        float curYaw = mc.thePlayer.rotationYaw;
        float curPitch = mc.thePlayer.rotationPitch;
        double angDeg = Math.toDegrees(angularDist(curYaw, curPitch, aim.yaw, aim.pitch));
        double turn = Math.max(turnSpeed.getValue().getInput(), 1.0);
        double placePerSec = Math.max(speed.getValue().getInput(), 0.25);
        long rotateMs = (long) Math.ceil((angDeg / turn) * 50.0);
        long placeMs = (long) Math.ceil(1000.0 / placePerSec);
        long ditherMs = aim.clearanceRad < settledFloorRad() ? 400L : 0L;
        long predicted = rotateMs + placeMs + ditherMs;
        long timeout = Math.max(MIN_STEP_TIMEOUT_MS, (long) (predicted * STEP_TIMEOUT_MARGIN) + 500L);
        stepDeadlineMs = System.currentTimeMillis() + timeout;
    }
    private boolean stepTimedOut() {
        return stepDeadlineMs > 0 && System.currentTimeMillis() > stepDeadlineMs;
    }
    
    private boolean isStepSoftValid(PlanStep step) {
        if (!isAirOrReplaceable(step.placePos)) return false;
        if (intersectsAnyEntity(step.placePos)) return false;
        if (!isRealSolid(step.neighbor)) return false;
        return minDistSqToBlock(actualEye(), step.placePos) <= PLACE_REACH_SQ;
    }
    
    private FaceTarget faceTargetForStep(PlanStep step) {
        if (aimPlanIndex == planIndex && currentAim != null) return currentAim;
        FaceTarget ft = solveFaceTarget(step.placePos, Collections.<BlockPos>emptySet(), actualEye(),
                                        mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        if (ft != null && (!ft.neighbor.equals(step.neighbor) || ft.facing != step.facing)) {
            plan.set(planIndex, new PlanStep(step.placePos, ft, step.isSupport));
        }
        currentAim = ft;
        aimPlanIndex = planIndex;
        missTicks = 0;
        ticksOnStep = 0;
        armStepTimeout(ft);
        return ft;
    }
    
    private boolean mopHitsTarget(MovingObjectPosition mop) {
        return mop != null
            && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
            && currentTarget != null
            && mop.getBlockPos().offset(mop.sideHit).equals(currentTarget);
    }
    
    private void onLiveRayMiss(float settledYaw, float settledPitch) {
        if (currentAim == null || currentTarget == null) return;
        double ang = angularDist(settledYaw, settledPitch, currentAim.yaw, currentAim.pitch);
        if (ang > Math.max(currentAim.clearanceRad, settledFloorRad())) {
            missTicks = 0;
            return;
        }
        if (++missTicks < RESOLVE_AFTER_MISSES) return;
        missTicks = 0;
        if (++stepRecoveries > MAX_TARGET_RECOVERIES) {
            advancePlan();
            return;
        }
        FaceTarget fresh = solveFaceTarget(currentTarget, Collections.<BlockPos>emptySet(), actualEye(),
                                           settledYaw, settledPitch);
        if (fresh == null) {
            advancePlan();
            return;
        }
        boolean support = plan != null && planIndex < plan.size() && plan.get(planIndex).isSupport;
        plan.set(planIndex, new PlanStep(currentTarget, fresh, support));
        currentAim = fresh;
        aimPlanIndex = planIndex;
        armStepTimeout(fresh);
    }
    private boolean isAirOrReplaceable(BlockPos pos) {
        Block b = mc.theWorld.getBlockState(pos).getBlock();
        return b instanceof BlockAir || b.getMaterial().isReplaceable();
    }
    private boolean isAirOrReplaceable(BlockPos pos, Set<BlockPos> virtualSolids) {
        if (virtualSolids.contains(pos)) return false;
        return isAirOrReplaceable(pos);
    }
    private long getPlaceDelay() {
        return (long) (1000L / speed.getValue().getInput());
    }
    private boolean preTick() {
        if (shouldAutoDisable()) {
            setEnabled(false);
            return true;
        }
        currentTarget = null;
        if (needsReplan && lockedPos != null) {
            needsReplan = false;
            missTicks = 0;
            ticksOnStep = 0;
            stepDeadlineMs = 0;
            stepRecoveries = 0;
            if (!buildPlan()) {
                setEnabled(false);
                return true;
            }
            placeTimer.reset();
            initPosX = mc.thePlayer.posX;
            initPosZ = mc.thePlayer.posZ;
            return true;
        }
        if (verifyPos != null) {
            verifyTicks++;
            if (verifyTicks > 5) {
                verifyPos = null;
                verifyTicks = 0;
            }
        }
        if (lockedPos != null) {
            if (!isPlayerAtLockedPos()) {
                displacementTicks++;
                if (displacementTicks > MAX_DISPLACEMENT_TICKS) {
                    PlayerUtils.addWaterMarkedMessageToChat("§c[AutoBlockIn] Player moved out of position.");
                    setEnabled(false);
                    return true;
                }
                return true;
            }
            displacementTicks = 0;
            if (plan != null && planIndex >= 0) {
                double dx = mc.thePlayer.posX - initPosX;
                double dz = mc.thePlayer.posZ - initPosZ;
                if (Math.abs(dx) > 0.15 || Math.abs(dz) > 0.15) {
                    needsReplan = true;
                    return true;
                }
            }
        }
        if (lockedPos == null) {
            if (mc.thePlayer.onGround) {
                lockedPos = new BlockPos(
                    MathHelper.floor_double(mc.thePlayer.posX),
                    MathHelper.floor_double(mc.thePlayer.getEntityBoundingBox().minY),
                    MathHelper.floor_double(mc.thePlayer.posZ)
                );
                if (!buildPlan()) {
                    setEnabled(false);
                    return true;
                }
                placeTimer.reset();
            } else {
                airborneTicks++;
                if (airborneTicks > MAX_AIRBORNE_TICKS) {
                    PlayerUtils.addWaterMarkedMessageToChat("§c[AutoBlockIn] Failed to find ground.");
                    setEnabled(false);
                    return true;
                }
                return true;
            }
        }
        if (plan == null || planIndex >= plan.size()) {
            setEnabled(false);
            return true;
        }
        if (countAllPlaceableBlocks() <= 0) {
            PlayerUtils.addWaterMarkedMessageToChat("§c[AutoBlockIn] Out of blocks.");
            setEnabled(false);
            return true;
        }
        while (planIndex < plan.size()) {
            PlanStep step = plan.get(planIndex);
            currentTarget = step.placePos;
            if (!isAirOrReplaceable(currentTarget) || intersectsAnyEntity(currentTarget)) {
                advancePlan();
                continue;
            }
            if (!ensureSlotForCurrentStep(step)) {
                if (pausedSlot) return true;
                if (countAllPlaceableBlocks() <= 0) {
                    PlayerUtils.addWaterMarkedMessageToChat("§c[AutoBlockIn] Out of blocks.");
                    setEnabled(false);
                    return true;
                }
                advancePlan();
                continue;
            }
            if (isStepSoftValid(step)) {
                currentAim = faceTargetForStep(step);
                if (currentAim == null) {
                    advancePlan();
                    continue;
                }
                return false;
            }
            if (stepRecoveries >= MAX_TARGET_RECOVERIES) {
                advancePlan();
                continue;
            }
            FaceTarget refreshed = solveFaceTarget(currentTarget, Collections.<BlockPos>emptySet(), actualEye(),
                                                   mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
            if (refreshed != null) {
                PlanStep updated = new PlanStep(currentTarget, refreshed, step.isSupport);
                plan.set(planIndex, updated);
                aimPlanIndex = -1;
                currentAim = null;
                stepRecoveries++;
                currentAim = faceTargetForStep(updated);
                if (currentAim == null) {
                    advancePlan();
                    continue;
                }
                missTicks = 0;
                return false;
            }
            advancePlan();
        }
        setEnabled(false);
        return true;
    }
    
    private void doPlace(MovingObjectPosition mop) {
        if (currentTarget == null || mop == null) return;
        if (!mopHitsTarget(mop)) return;
        if (plan != null && planIndex < plan.size()) {
            if (!ensureSlotForCurrentStep(plan.get(planIndex))) return;
        }
        BlockPos neighbor = mop.getBlockPos();
        EnumFacing facing = mop.sideHit;
        Vec3 hit = mop.hitVec;
        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        if (held == null || !(held.getItem() instanceof ItemBlock) || held.stackSize <= 0) {
            if (plan != null && planIndex < plan.size()) {
                lastSetSlot = -1;
                if (!ensureSlotForCurrentStep(plan.get(planIndex))) return;
                held = mc.thePlayer.inventory.getCurrentItem();
            }
            if (held == null || !(held.getItem() instanceof ItemBlock) || held.stackSize <= 0) return;
        }
        if (!((ItemBlock) held.getItem()).canPlaceBlockOnSide(
                mc.theWorld, neighbor, facing, mc.thePlayer, held)) return;
        if (mc.playerController.onPlayerRightClick(
                mc.thePlayer, mc.theWorld, held, neighbor, facing, hit)) {
            mc.thePlayer.swingItem();
            verifyPos = currentTarget;
            verifyTicks = 0;
            advancePlan();
            placeTimer.reset();
            ItemStack after = mc.thePlayer.inventory.getCurrentItem();
            if (after == null || after.stackSize <= 0) {
                lastSetSlot = -1;
            }
            if (planIndex >= plan.size()) {
                setEnabled(false);
            }
        }
    }
    @EventLink
    public final Listener<EventTick> autoDisableListener = event -> {
        if (shouldAutoDisable()) setEnabled(false);
    };
    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> silentRotationListener = event -> {
        // if (mode.getValue() != AutoBlockInMode.SILENT) return;
        if (preTick()) return;
        if (currentAim == null) return;
        event.setMovementFix(SilentRotationManager.MovementFix.OFF);
        event.setJumpFix(false);
        ticksOnStep++;
        if (stepTimedOut()) {
            advancePlan();
            return;
        }
        float reqYaw = currentAim.yaw;
        float reqPitch = currentAim.pitch;
        if (currentAim.clearanceRad < settledFloorRad()) {
            int i = ticksOnStep % DITHER.length;
            float g = RotationUtils.getGCD();
            reqYaw += (float) (DITHER[i][0] * g);
            reqPitch += (float) (DITHER[i][1] * g);
        }
        event.setYaw(reqYaw);
        event.setPitch(reqPitch);
        event.setSpeed((float) turnSpeed.getValue().getInput());
        event.setPreventDuplicateLook(true);
    };
    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> silentPostListener = event -> {
        // if (mode.getValue() != AutoBlockInMode.SILENT) return;
        if (currentTarget == null || currentAim == null) return;
        MovingObjectPosition mop = event.getRayTrace();
        if (!mopHitsTarget(mop)) {
            onLiveRayMiss(event.getYaw(), event.getPitch());
            return;
        }
        missTicks = 0;
        if (!placeTimer.finished(getPlaceDelay())) return;
        doPlace(mop);
    };
    // NORMAL mode (disabled)
    // @RequiresPlayer
    // @EventLink
    // public final Listener<EventTick> tickListener = event -> {
    //     if (mode.getValue() != AutoBlockInMode.NORMAL) return;
    //     if (preTick()) return;
    //     if (currentTarget == null || currentAim == null) {
    //         yawDelta = 0;
    //         pitchDelta = 0;
    //         return;
    //     }
    //     yawDelta = getYawDelta(currentAim.yaw);
    //     pitchDelta = getPitchDelta(currentAim.pitch);
    //     prevPartialTicks = 0;
    //     ticksOnStep++;
    //     if (stepTimedOut()) {
    //         advancePlan();
    //         return;
    //     }
    //     Vec3 eyes = mc.thePlayer.getPositionEyes(1);
    //     Vec3 look = lookVector(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
    //     Vec3 traceEnd = eyes.addVector(look.xCoord * PLACE_REACH, look.yCoord * PLACE_REACH, look.zCoord * PLACE_REACH);
    //     MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eyes, traceEnd, false, false, true);
    //     if (!mopHitsTarget(mop)) {
    //         onLiveRayMiss(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
    //         return;
    //     }
    //     missTicks = 0;
    //     if (!placeTimer.finished(getPlaceDelay())) return;
    //     doPlace(mop);
    // };
    // @RequiresPlayer
    // @EventLink
    // public final Listener<EventRenderWorldLast> normalRotationListener = event -> {
    //     if (mode.getValue() != AutoBlockInMode.NORMAL) return;
    //     if (yawDelta == 0 && pitchDelta == 0) return;
    //     float t = event.partialTicks - prevPartialTicks;
    //     float newYaw = mc.thePlayer.rotationYaw + yawDelta * t;
    //     float newPitch = mc.thePlayer.rotationPitch + pitchDelta * t;
    //     newPitch = MathHelper.clamp_float(newPitch, -90, 90);
    //     float[] fixed = RotationUtils.patchGCD(
    //         new float[]{mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch},
    //         new float[]{newYaw, newPitch});
    //     mc.thePlayer.rotationYaw = fixed[0];
    //     mc.thePlayer.rotationPitch = fixed[1];
    //     prevPartialTicks = event.partialTicks;
    // };
    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> renderListener = event -> {
        if (!showPreview.getValue() || plan == null || planIndex >= plan.size()) return;
        int main = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
        int darker = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getDarkerColor();
        int support = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getWhite();
        for (int i = planIndex; i < plan.size(); i++) {
            PlanStep step = plan.get(i);
            boolean current = i == planIndex;
            int base = step.isSupport ? support : (current ? main : darker);
            int fill = withAlpha(base, current ? 90 : (step.isSupport ? 35 : 55));
            int outline = withAlpha(base, current ? 230 : 160);
            RenderUtils.renderBlock(step.placePos, fill, false, true);
            RenderUtils.renderBlock(step.placePos, outline, true, false);
            if (current && step.facing != null) {
                RenderUtils.renderBlockFace(step.neighbor, step.facing,
                        withAlpha(main, 200), true, true);
            }
        }
    };
    private static int withAlpha(int rgb, int alpha) {
        return ((alpha & 0xFF) << 24) | (rgb & 0x00FFFFFF);
    }
    // NORMAL mode (disabled)
    // private float getYawDelta(float targetYaw) {
    //     float delta = MathHelper.wrapAngleTo180_float(targetYaw - mc.thePlayer.rotationYaw);
    //     float speedVal = (float) turnSpeed.getValue().getInput();
    //     float speedCurve = (float) (speedVal * (Math.sin(Math.toRadians(Math.abs(delta))) / 2 + 0.5));
    //     return Math.min(speedCurve, Math.abs(delta)) * Math.signum(delta);
    // }
    // private float getPitchDelta(float targetPitch) {
    //     float delta = targetPitch - mc.thePlayer.rotationPitch;
    //     float speedVal = (float) turnSpeed.getValue().getInput();
    //     float speedCurve = (float) (speedVal * (Math.sin(Math.toRadians(Math.abs(delta))) / 2 + 0.5));
    //     return Math.min(speedCurve, Math.abs(delta)) * Math.signum(delta);
    // }
    private boolean isPlayerAtLockedPos() {
        if (lockedPos == null) return false;
        int px = MathHelper.floor_double(mc.thePlayer.posX);
        int pz = MathHelper.floor_double(mc.thePlayer.posZ);
        return px == lockedPos.getX() && pz == lockedPos.getZ();
    }
    public enum AutoBlockInMode {
        // NORMAL,
        SILENT
    }
    
    private static class FaceTarget {
        final BlockPos neighbor;
        final EnumFacing facing;
        final float yaw;
        final float pitch;
        final Vec3 hitVec;
        final double clearanceRad;
        FaceTarget(BlockPos n, EnumFacing f, float yaw, float pitch, Vec3 hitVec, double clearanceRad) {
            this.neighbor = n;
            this.facing = f;
            this.yaw = yaw;
            this.pitch = pitch;
            this.hitVec = hitVec;
            this.clearanceRad = clearanceRad;
        }
    }
    
    private static class FaceFrame {
        final Vec3 origin, uAxis, vAxis, normal;
        final double d;
        private FaceFrame(Vec3 origin, Vec3 uAxis, Vec3 vAxis, Vec3 normal) {
            this.origin = origin;
            this.uAxis = uAxis;
            this.vAxis = vAxis;
            this.normal = normal;
            this.d = normal.xCoord * origin.xCoord + normal.yCoord * origin.yCoord + normal.zCoord * origin.zCoord;
        }
        static FaceFrame of(BlockPos b, EnumFacing f) {
            double x = b.getX(), y = b.getY(), z = b.getZ();
            switch (f) {
                case UP:    return new FaceFrame(new Vec3(x, y + 1, z),     new Vec3(1, 0, 0), new Vec3(0, 0, 1), new Vec3(0, 1, 0));
                case DOWN:  return new FaceFrame(new Vec3(x, y, z),         new Vec3(1, 0, 0), new Vec3(0, 0, 1), new Vec3(0, -1, 0));
                case NORTH: return new FaceFrame(new Vec3(x, y, z),         new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, -1));
                case SOUTH: return new FaceFrame(new Vec3(x, y, z + 1),     new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1));
                case WEST:  return new FaceFrame(new Vec3(x, y, z),         new Vec3(0, 0, 1), new Vec3(0, 1, 0), new Vec3(-1, 0, 0));
                case EAST:  return new FaceFrame(new Vec3(x + 1, y, z),     new Vec3(0, 0, 1), new Vec3(0, 1, 0), new Vec3(1, 0, 0));
                default:    return new FaceFrame(new Vec3(x, y, z),         new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1));
            }
        }
        double[] toUV(double wx, double wy, double wz) {
            double dx = wx - origin.xCoord, dy = wy - origin.yCoord, dz = wz - origin.zCoord;
            return new double[]{
                dx * uAxis.xCoord + dy * uAxis.yCoord + dz * uAxis.zCoord,
                dx * vAxis.xCoord + dy * vAxis.yCoord + dz * vAxis.zCoord
            };
        }
        Vec3 toWorld(double u, double v) {
            return new Vec3(
                origin.xCoord + uAxis.xCoord * u + vAxis.xCoord * v,
                origin.yCoord + uAxis.yCoord * u + vAxis.yCoord * v,
                origin.zCoord + uAxis.zCoord * u + vAxis.zCoord * v
            );
        }
    }
    private static class PieceCandidate {
        final List<double[]> poly;
        final double[] cc;
        PieceCandidate(List<double[]> poly, double[] cc) { this.poly = poly; this.cc = cc; }
    }
    private static class Pillar {
        final BlockPos feet, head;
        Pillar(BlockPos f, BlockPos h) { feet = f; head = h; }
    }
    private static class PlanStep {
        final BlockPos placePos;
        final BlockPos neighbor;
        final EnumFacing facing;
        
        final boolean isSupport;
        PlanStep(BlockPos placePos, FaceTarget info, boolean isSupport) {
            this.placePos = placePos;
            this.neighbor = info.neighbor;
            this.facing = info.facing;
            this.isSupport = isSupport;
        }
        PlanStep(BlockPos placePos, BlockPos neighbor, EnumFacing facing, boolean isSupport) {
            this.placePos = placePos;
            this.neighbor = neighbor;
            this.facing = facing;
            this.isSupport = isSupport;
        }
    }
    private static class BfsNode {
        final List<PlanStep> chain;
        final Set<BlockPos> state;
        BfsNode(List<PlanStep> chain, Set<BlockPos> state) {
            this.chain = chain;
            this.state = state;
        }
    }
    private static class PlacementCandidate {
        final BlockPos placePos;
        final BlockPos neighbor;
        final EnumFacing facing;
        PlacementCandidate(BlockPos placePos, FaceTarget info) {
            this.placePos = placePos;
            this.neighbor = info.neighbor;
            this.facing = info.facing;
        }
    }
    private static class ScoredPos {
        final BlockPos pos;
        final double score;
        final boolean adjTarget;
        ScoredPos(BlockPos pos, double score, boolean adjTarget) {
            this.pos = pos;
            this.score = score;
            this.adjTarget = adjTarget;
        }
    }
}
