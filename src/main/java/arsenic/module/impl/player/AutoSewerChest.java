package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.gui.themes.ThemeManager;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.render.RenderUtils;
import org.lwjgl.opengl.GL11;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.utils.bot.BotDriver;
import arsenic.utils.botcore.Goal;
import arsenic.utils.botcore.Step;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.item.ItemArmor;
import arsenic.utils.minecraft.ContainerUtils;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Walks (with the bot core) to the nearest chest the server just placed where there was air and
 * opens it, closing the chest screen as soon as it appears. With no chest to go to, it opens the
 * inventory and drops any diamond armour that is not being worn. Chests that were already there are ignored. Keeps running with the window
 * unfocused by switching off vanilla "pause on lost focus" while enabled.
 */
@ModuleInfo(name = "AutoSewerChest", category = ModuleCategory.PLAYER)
public class AutoSewerChest extends Module {


    public final BooleanProperty renderPath = new BooleanProperty("Render Path", true);
    public final BooleanProperty renderTarget = new BooleanProperty("Render Chests", true);
    /** Shows the best route found so far while a search is running. */
    public final BooleanProperty renderSearch = new BooleanProperty("Render Search", true);
    /** Hop while sprinting along straight, flat stretches that have room overhead. */
    public final BooleanProperty sprintJump = new BooleanProperty("Sprint Jump", true);

    /** First hotbar slot in the player inventory container (slots 36-44 are the hotbar). */
    private static final int HOTBAR_START = 36;

    /**
     * Open a chest as soon as it is this close (eye to chest centre) and in sight, moving or not.
     * Survival reach is 4.5; the click goes straight to the server, which allows that.
     */
    private static final double REACH = 4.4;
    /** Only chests below this height are gone for. */
    private static final int MAX_CHEST_Y = 70;
    /** Fresh paths to try when one ends short of the chest, before giving up on it. */
    private static final int MAX_REPATHS = 3;
    /** Time allowed to reach a chest: a base amount plus more per block of distance, so far chests get longer. */
    private static final long WALK_TIMEOUT_MS = 30000;
    private static final long WALK_TIMEOUT_PER_BLOCK_MS = 500;
    private static final long OPEN_TIMEOUT_MS = 4000;
    /** After a chest was tried, ignore it for this long (it may respawn with loot). */
    private static final long RETRY_DELAY_MS = 30000;
    /** A spawned chest nobody looted is forgotten after this long. */
    private static final long SPAWN_MEMORY_MS = 5 * 60 * 1000;
    /** Gap between inventory clicks while dropping spare diamond armour. */
    private static final long DROP_DELAY_MS = 150;
    private static final long DROP_TIMEOUT_MS = 5000;
    /** No inventory tidying for this long after the player closes it themselves, or after a fix keeps failing. */
    private static final long TIDY_BACKOFF_USER_MS = 60000;
    private static final long TIDY_BACKOFF_FAIL_MS = 30000;
    private static final int RED = 0xFFFF0000;


    private enum State { IDLE, WALKING, OPENING, DROPPING }

    /** Chests the server placed where there was air, with the time the update arrived. */
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
        if (mc.gameSettings != null) {
            savedPause = mc.gameSettings.pauseOnLostFocus;
            pauseSaved = true;
            mc.gameSettings.pauseOnLostFocus = false;
        }
    }

    @Override
    protected void onDisable() {
        if (mc.gameSettings != null && pauseSaved) {
            mc.gameSettings.pauseOnLostFocus = savedPause;
        }
        pauseSaved = false;
        BotDriver.stop();
        BotDriver.forgetOwnBlocks();
        state = State.IDLE;
        target = null;
    }

    /**
     * A chest only counts when the server puts it there: a block update that turns what the client
     * still has as air into a chest. This runs before the client applies the update, so the world
     * still holds the old block.
     */
    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onPacket = event -> {
        if (mc.theWorld == null) return;
        Object packet = event.getPacket();
        if (packet instanceof S23PacketBlockChange) {
            S23PacketBlockChange p = (S23PacketBlockChange) packet;
            noteChange(p.getBlockPosition(), p.getBlockState());
        } else if (packet instanceof S22PacketMultiBlockChange) {
            for (S22PacketMultiBlockChange.BlockUpdateData d : ((S22PacketMultiBlockChange) packet).getChangedBlocks()) {
                noteChange(d.getPos(), d.getBlockState());
            }
        }
    };

    private void noteChange(BlockPos pos, IBlockState now) {
        Block block = now.getBlock();
        if (block != Blocks.chest && block != Blocks.trapped_chest) return;
        if (mc.theWorld.getBlockState(pos).getBlock() != Blocks.air) return;
        spawned.put(new BlockPos(pos.getX(), pos.getY(), pos.getZ()), System.currentTimeMillis());
    }


    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        // keeps running in the background (losing focus no longer pauses); the Escape menu is left
        // alone so it can still be used
        mc.gameSettings.pauseOnLostFocus = false;

        long now = System.currentTimeMillis();
        BotDriver.bot.sprintJump = sprintJump.getValue();
        prune(now);

        switch (state) {
            case IDLE:
                BlockPos next = findChest();
                if (next == null) {
                    // Nothing to loot: use the downtime to tidy the inventory (not while some other
                    // screen is open, and not straight after the player closed it or a fix kept failing)
                    if (mc.currentScreen == null && now >= tidyAfter && nextInventoryFix() != null) {
                        mc.displayGuiScreen(new GuiInventory(mc.thePlayer));
                        lastFix = null;
                        fixRepeats = 0;
                        setState(State.DROPPING);
                    }
                    return;
                }
                target = next;
                repaths = 0;
                walkTimeout = WALK_TIMEOUT_MS + (long) (Math.sqrt(mc.thePlayer.getDistanceSq(target)) * WALK_TIMEOUT_PER_BLOCK_MS);
                BotDriver.goTo(goalFor(target));
                setState(State.WALKING);
                break;

            case DROPPING:
                if (!(mc.currentScreen instanceof GuiInventory)) {
                    // the player closed it (or opened something else): leave the inventory alone a while
                    tidyAfter = now + TIDY_BACKOFF_USER_MS;
                    setState(State.IDLE);
                    return;
                }
                if (now - stateSince > DROP_TIMEOUT_MS) {
                    mc.thePlayer.closeScreen();
                    tidyAfter = now + TIDY_BACKOFF_FAIL_MS;
                    setState(State.IDLE);
                    return;
                }
                if (now - stateSince < DROP_DELAY_MS || now - lastClick < DROP_DELAY_MS) return;
                int[] fix = nextInventoryFix();
                if (fix == null) {
                    mc.thePlayer.closeScreen();
                    setState(State.IDLE);
                    return;
                }
                // the same fix again means the last one didn't take (e.g. the server refused it):
                // give up for a while rather than reopening the inventory forever
                if (lastFix != null && lastFix[0] == fix[0] && lastFix[1] == fix[1] && ++fixRepeats >= 3) {
                    mc.thePlayer.closeScreen();
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
                // open it the moment it is in reach and in sight, without walking right up to it
                if (eyeDistanceTo(target) <= REACH && traceChest(target) != null) {
                    BotDriver.stop();
                    setState(State.OPENING);
                    return;
                }
                if (!BotDriver.isActive()) {
                    // The path ended (or never found one) short of the chest: path again from here.
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
                if (mc.currentScreen instanceof GuiChest) {
                    // Opening it is what counts; shut the screen straight away and move on.
                    mc.thePlayer.closeScreen();
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
                if (now - lastClick > 1000) { // click straight away; retry once a second
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

    /** A line through the steps of a route, at foot height. */
    private void drawPath(List<Step> steps, int color) {
        if (steps == null || steps.size() < 2) return;
        List<Step> copy = new ArrayList<>(steps); // the planner thread may be swapping paths
        double vx = mc.getRenderManager().viewerPosX;
        double vy = mc.getRenderManager().viewerPosY;
        double vz = mc.getRenderManager().viewerPosZ;
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glLineWidth(2f);
        GL11.glColor4f((color >> 16 & 0xFF) / 255f, (color >> 8 & 0xFF) / 255f, (color & 0xFF) / 255f, 0.9f);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (Step s : copy) {
            GL11.glVertex3d(s.x + 0.5 - vx, s.feet + 0.1 - vy, s.z + 0.5 - vz);
        }
        GL11.glEnd();
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    /** Stand somewhere the chest is in reach and in sight. */
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

    /** Nearest server-spawned chest in range that has not been tried recently, or null. */
    private BlockPos findChest() {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE; // no range limit: any chest seen spawning, however far
        for (BlockPos pos : spawned.keySet()) {
            if (tried.containsKey(pos) || pos.getY() >= MAX_CHEST_Y) continue;
            double dist = mc.thePlayer.getDistanceSq(pos);
            if (dist < bestDist) {
                bestDist = dist;
                best = pos;
            }
        }
        return best;
    }

    /**
     * The next inventory clean-up step, or null when there is nothing to do: {slot, -1} drops that
     * container slot, {slot, hotbarIndex} swaps it into the hotbar. Spare diamond armour goes; of the
     * dark prismarine (the building block) only the biggest stack stays, and it is kept in the hotbar
     * so the pathfinder can build with it.
     */
    private int[] nextInventoryFix() {
        int armor = spareDiamondArmor();
        if (armor != -1) {
            return new int[]{armor, -1};
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
                if (mc.thePlayer.inventory.mainInventory[i] == null) {
                    hotbar = i;
                    break;
                }
            }
            return new int[]{keep.slot, hotbar};
        }
        return null;
    }

    /** Prefer a stack already in the hotbar, then the bigger one. */
    private static int keepScore(ContainerUtils.SlotItem si) {
        return (si.slot >= HOTBAR_START ? 1000 : 0) + si.item.stackSize;
    }

    /** Inventory-container slot of a diamond armour piece that is not being worn, or -1. */
    private int spareDiamondArmor() {
        for (ContainerUtils.SlotItem si : ContainerUtils.getInventoryItems()) { // slots 9-44: the armour slots are not included
            if (si.item.getItem() instanceof ItemArmor
                    && ((ItemArmor) si.item.getItem()).getArmorMaterial() == ItemArmor.ArmorMaterial.DIAMOND) {
                return si.slot;
            }
        }
        return -1;
    }

    private boolean isChest(BlockPos pos) {
        return pos != null && mc.theWorld.getTileEntity(pos) instanceof TileEntityChest;
    }

    private double eyeDistanceTo(BlockPos pos) {
        return mc.thePlayer.getPositionEyes(1f).distanceTo(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
    }


private void lookAt(BlockPos pos) {        float[] rots = RotationUtils.getRotations(mc.thePlayer.getPositionEyes(1f), new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));        mc.thePlayer.rotationYaw = rots[0];        mc.thePlayer.rotationPitch = rots[1];    }
    /** What a ray from the eyes to the chest's centre actually hits, if that is a chest (either half of a double). */
    private MovingObjectPosition traceChest(BlockPos pos) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1f);
        Vec3 centre = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eyes, centre, false, false, false);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return null;
        Block hitBlock = mc.theWorld.getBlockState(hit.getBlockPos()).getBlock();
        if (hitBlock != Blocks.chest && hitBlock != Blocks.trapped_chest) return null;
        return hit;
    }

    /** Right-clicks the chest on the face the ray hits, like a player aiming at it. */
    private void openChest(BlockPos pos) {
        MovingObjectPosition hit = traceChest(pos);
        if (hit == null) return;
        mc.thePlayer.swingItem();
        mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(), hit.getBlockPos(), hit.sideHit, hit.hitVec);
    }

    @Override
    public String getHudInfo() {
        return state.name().toLowerCase();
    }
}
