
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
import arsenic.module.ModuleTier;
import arsenic.utils.bot.BotDriver;
import arsenic.utils.botcore.Goal;
import arsenic.utils.botcore.Step;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
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

@ModuleInfo(name = "AutoSewerChest", category = ModuleCategory.PLAYER, tier = ModuleTier.LEGIT)
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
        mc.gameSettings.pauseOnLostFocus = false;

        long now = System.currentTimeMillis();
        BotDriver.bot.sprintJump = sprintJump.getValue();
        prune(now);

        switch (state) {
            case IDLE:
                BlockPos next = findChest();
                if (next == null) {
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
                if (mc.currentScreen instanceof GuiChest) {
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
        RenderUtils.color2(color, 0.9f);
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
            double dist = mc.thePlayer.getDistanceSq(pos);
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
                if (mc.thePlayer.inventory.mainInventory[i] == null) {
                    hotbar = i;
                    break;
                }
            }
            return new int[]{keep.slot, hotbar};
        }
        return null;
    }

    private static int keepScore(ContainerUtils.SlotItem si) {
        return (si.slot >= HOTBAR_START ? 1000 : 0) + si.item.stackSize;
    }

    /**
     * A slot holding something to throw out: unworn diamond armour, soul sand or leather boots.
     * Worn armour is never in these slots.
     */
    private int junkSlot() {
        for (ContainerUtils.SlotItem si : ContainerUtils.getInventoryItems()) {
            Item item = si.item.getItem();
            if (item instanceof ItemArmor
                    && ((ItemArmor) item).getArmorMaterial() == ItemArmor.ArmorMaterial.DIAMOND) {
                return si.slot;
            }
            if (item == Items.leather_boots || item == Item.getItemFromBlock(Blocks.soul_sand)) {
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
    private MovingObjectPosition traceChest(BlockPos pos) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1f);
        Vec3 centre = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eyes, centre, false, false, false);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return null;
        Block hitBlock = mc.theWorld.getBlockState(hit.getBlockPos()).getBlock();
        if (hitBlock != Blocks.chest && hitBlock != Blocks.trapped_chest) return null;
        return hit;
    }

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
