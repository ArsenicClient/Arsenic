package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.gui.themes.ThemeManager;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.render.RenderUtils;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.utils.bot.BotDriver;
import arsenic.utils.botcore.Goal;
import arsenic.utils.botcore.Step;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import arsenic.utils.minecraft.ContainerUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ModuleInfo(name = "AutoSewerChest", category = ModuleCategory.PLAYER, tier = ModuleTier.DEV)
public class AutoSewerChest extends Module {


    public final BooleanProperty renderPath = new BooleanProperty("Render Path", true);
    public final BooleanProperty renderTarget = new BooleanProperty("Render Chests", true);
    public final BooleanProperty renderSearch = new BooleanProperty("Render Search", true);
    public final BooleanProperty sprintJump = new BooleanProperty("Sprint Jump", true);

    private static final int HOTBAR_START = 36;

    private static final double REACH = 4.4;
    private static final int MAX_CHEST_Y = 70;
    private static final int MAX_REPATHS = 3;
    private static final long WALK_TIMEOUT_MS = 30000;
    private static final long WALK_TIMEOUT_PER_BLOCK_MS = 500;
    private static final long OPEN_TIMEOUT_MS = 4000;
    private static final long RETRY_DELAY_MS = 30000;
    private static final long SPAWN_MEMORY_MS = 5 * 60 * 1000;
    private static final long DROP_DELAY_MS = 150;
    private static final long DROP_TIMEOUT_MS = 5000;
    private static final long TIDY_BACKOFF_USER_MS = 60000;
    private static final long TIDY_BACKOFF_FAIL_MS = 30000;
    private static final int RED = 0xFFFF0000;


    private enum State { IDLE, WALKING, OPENING, DROPPING }

    private final Map<BlockPos, Long> spawned = new ConcurrentHashMap<>();
    private final Map<BlockPos, Long> tried = new HashMap<>();
    private State state = State.IDLE;
    private BlockPos target;
    private long stateSince;
    private long lastClick;
    private int repaths;
    private long tidyAfter;
    private int[] lastFix;
    private int fixRepeats;
    private long walkTimeout = WALK_TIMEOUT_MS;
    private boolean savedPause;
    private boolean pauseSaved;

    @Override
    protected void onEnable() {
        state = State.IDLE;
        target = null;
        spawned.clear();
        if (mc.options != null) {
            savedPause = mc.options.pauseOnLostFocus;
            pauseSaved = true;
            mc.options.pauseOnLostFocus = false;
        }
    }

    @Override
    protected void onDisable() {
        if (mc.options != null && pauseSaved) {
            mc.options.pauseOnLostFocus = savedPause;
        }
        pauseSaved = false;
        BotDriver.stop();
        BotDriver.forgetOwnBlocks();
        state = State.IDLE;
        target = null;
    }

    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onPacket = event -> {
        if (mc.level == null) return;
        Object packet = event.getPacket();
        if (packet instanceof ClientboundBlockUpdatePacket p) {
            noteChange(p.getPos(), p.getBlockState());
        } else if (packet instanceof ClientboundSectionBlocksUpdatePacket p) {
            p.runUpdates(this::noteChange);
        }
    };

    private void noteChange(BlockPos pos, BlockState now) {
        Block block = now.getBlock();
        if (block != Blocks.CHEST && block != Blocks.TRAPPED_CHEST) return;
        if (mc.level.getBlockState(pos).getBlock() != Blocks.AIR) return;
        spawned.put(new BlockPos(pos.getX(), pos.getY(), pos.getZ()), System.currentTimeMillis());
    }


    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        mc.options.pauseOnLostFocus = false;

        long now = System.currentTimeMillis();
        BotDriver.bot.sprintJump = sprintJump.getValue();
        prune(now);

        switch (state) {
            case IDLE:
                BlockPos next = findChest();
                if (next == null) {
                    if (mc.gui.screen() == null && now >= tidyAfter && nextInventoryFix() != null) {
                        mc.gui.setScreen(new InventoryScreen(mc.player));
                        lastFix = null;
                        fixRepeats = 0;
                        setState(State.DROPPING);
                    }
                    return;
                }
                target = next;
                repaths = 0;
                walkTimeout = WALK_TIMEOUT_MS + (long) (Math.sqrt(mc.player.distanceToSqr(Vec3.atCenterOf(target))) * WALK_TIMEOUT_PER_BLOCK_MS);
                BotDriver.goTo(goalFor(target));
                setState(State.WALKING);
                break;

            case DROPPING:
                if (!(mc.gui.screen() instanceof InventoryScreen)) {
                    tidyAfter = now + TIDY_BACKOFF_USER_MS;
                    setState(State.IDLE);
                    return;
                }
                if (now - stateSince > DROP_TIMEOUT_MS) {
                    mc.player.closeContainer();
                    tidyAfter = now + TIDY_BACKOFF_FAIL_MS;
                    setState(State.IDLE);
                    return;
                }
                if (now - stateSince < DROP_DELAY_MS || now - lastClick < DROP_DELAY_MS) return;
                int[] fix = nextInventoryFix();
                if (fix == null) {
                    mc.player.closeContainer();
                    setState(State.IDLE);
                    return;
                }
                if (lastFix != null && lastFix[0] == fix[0] && lastFix[1] == fix[1] && ++fixRepeats >= 3) {
                    mc.player.closeContainer();
                    tidyAfter = now + TIDY_BACKOFF_FAIL_MS;
                    setState(State.IDLE);
                    return;
                }
                if (lastFix == null || lastFix[0] != fix[0] || lastFix[1] != fix[1]) fixRepeats = 0;
                lastFix = fix;
                lastClick = now;
                if (fix[1] == -1) {
                    ContainerUtils.drop(fix[0]);
                } else {
                    ContainerUtils.swap(fix[0], fix[1]);
                }
                break;

            case WALKING:
                if (!isChest(target) || now - stateSince > walkTimeout) {
                    giveUp(now);
                    return;
                }
                if (eyeDistanceTo(target) <= REACH && traceChest(target) != null) {
                    BotDriver.stop();
                    setState(State.OPENING);
                    return;
                }
                if (!BotDriver.isActive()) {
                    if (repaths++ >= MAX_REPATHS) {
                        giveUp(now);
                        return;
                    }
                    BotDriver.goTo(goalFor(target));
                    return;
                }
                BotDriver.onTick();
                break;

            case OPENING:
                if (mc.gui.screen() instanceof ContainerScreen) {
                    mc.player.closeContainer();
                    tried.put(target, now);
                    target = null;
                    setState(State.IDLE);
                    return;
                }
                if (!isChest(target) || now - stateSince > OPEN_TIMEOUT_MS) {
                    giveUp(now);
                    return;
                }
                lookAt(target);
                if (now - lastClick > 1000) {
                    lastClick = now;
                    openChest(target);
                }
                break;
        }
    };


    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRender = event -> {
        int main = ThemeManager.getMainColor();
        if (renderPath.getValue()) {
            drawPath(BotDriver.bot.path(), main);
            drawPath(BotDriver.bot.nextPath(), ThemeManager.getDarkerColor());
        }
        if (renderSearch.getValue()) {
            drawPath(BotDriver.bot.searchPreview, 0xFFFFD700);
        }
        if (renderTarget.getValue()) {
            for (BlockPos pos : spawned.keySet()) {
                RenderUtils.renderBlock(pos, RED, true, true);
            }
        }
    };

    private void drawPath(List<Step> steps, int color) {
        if (steps == null || steps.size() < 2) return;
        List<Step> copy = new ArrayList<>(steps);
        for (int i = 1; i < copy.size(); i++) {
            Step a = copy.get(i - 1), b = copy.get(i);
            RenderUtils.drawLine(new Vec3(a.x + 0.5, a.feet + 0.1, a.z + 0.5), new Vec3(b.x + 0.5, b.feet + 0.1, b.z + 0.5),
                    RenderUtils.withAlpha(color, 230), 2f);
        }
    }

    private static Goal goalFor(BlockPos chest) {
        return new Goal.NearBlock(chest.getX(), chest.getY(), chest.getZ(), BotDriver.bot.tun.goalReach);
    }

    private void setState(State s) {
        state = s;
        stateSince = System.currentTimeMillis();
    }

    private void giveUp(long now) {
        BotDriver.stop();
        if (target != null) tried.put(target, now);
        target = null;
        setState(State.IDLE);
    }

    private void prune(long now) {
        tried.values().removeIf(t -> now - t > RETRY_DELAY_MS);
        spawned.entrySet().removeIf(e -> now - e.getValue() > SPAWN_MEMORY_MS || !isChest(e.getKey()));
    }

    private BlockPos findChest() {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : spawned.keySet()) {
            if (tried.containsKey(pos) || pos.getY() >= MAX_CHEST_Y) continue;
            double dist = mc.player.distanceToSqr(Vec3.atCenterOf(pos));
            if (dist < bestDist) {
                bestDist = dist;
                best = pos;
            }
        }
        return best;
    }

    private int[] nextInventoryFix() {
        int junk = junkSlot();
        if (junk != -1) {
            return new int[]{junk, -1};
        }
        ContainerUtils.SlotItem keep = null;
        List<ContainerUtils.SlotItem> blocks = new ArrayList<>();
        for (ContainerUtils.SlotItem si : ContainerUtils.getInventoryItems()) {
            if (!BotDriver.isDarkPrismarine(si.item)) continue;
            blocks.add(si);
            if (keep == null || keepScore(si) > keepScore(keep)) keep = si;
        }
        for (ContainerUtils.SlotItem si : blocks) {
            if (si != keep) return new int[]{si.slot, -1};
        }
        if (keep != null && keep.slot < HOTBAR_START) {
            int hotbar = 8;
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getItem(i) == null) {
                    hotbar = i;
                    break;
                }
            }
            return new int[]{keep.slot, hotbar};
        }
        return null;
    }

    private static int keepScore(ContainerUtils.SlotItem si) {
        return (si.slot >= HOTBAR_START ? 1000 : 0) + si.item.getCount();
    }

    /**
     * A slot holding something to throw out: unworn diamond armour, soul sand or leather boots.
     * Worn armour is never in these slots.
     */
    private int junkSlot() {
        for (ContainerUtils.SlotItem si : ContainerUtils.getInventoryItems()) {
            Item item = si.item.getItem();
            if (item == Items.DIAMOND_HELMET || item == Items.DIAMOND_CHESTPLATE
                    || item == Items.DIAMOND_LEGGINGS || item == Items.DIAMOND_BOOTS) {
                return si.slot;
            }
            if (item == Items.LEATHER_BOOTS || item == Items.SOUL_SAND) {
                return si.slot;
            }
        }
        return -1;
    }

    private boolean isChest(BlockPos pos) {
        return pos != null && mc.level.getBlockEntity(pos) instanceof ChestBlockEntity;
    }

    private double eyeDistanceTo(BlockPos pos) {
        return mc.player.getEyePosition(1f).distanceTo(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
    }


    private void lookAt(BlockPos pos) {
        float[] rots = RotationUtils.getRotations(mc.player.getEyePosition(1f), new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        mc.player.setYRot(rots[0]);
        mc.player.setXRot(rots[1]);
    }

    private BlockHitResult traceChest(BlockPos pos) {
        Vec3 eyes = mc.player.getEyePosition(1f);
        Vec3 centre = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        BlockHitResult hit = PlayerUtils.rayTraceBlocks(eyes, centre);
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        Block hitBlock = mc.level.getBlockState(hit.getBlockPos()).getBlock();
        if (hitBlock != Blocks.CHEST && hitBlock != Blocks.TRAPPED_CHEST) return null;
        return hit;
    }

    private void openChest(BlockPos pos) {
        BlockHitResult hit = traceChest(pos);
        if (hit == null) return;
        PlayerUtils.swingItem();
        arsenic.utils.minecraft.ScaffoldUtil.placeBlock(hit.getBlockPos(), hit.getDirection(), hit.getLocation());
    }

    @Override
    public String getHudInfo() {
        return state.name().toLowerCase();
    }
}
