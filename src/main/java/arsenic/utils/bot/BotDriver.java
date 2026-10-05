package arsenic.utils.bot;

import arsenic.utils.timer.MSTimer;
import arsenic.utils.botcore.Bot;
import arsenic.utils.botcore.Controls;
import arsenic.utils.botcore.Goal;
import arsenic.utils.botcore.PlayerView;
import arsenic.utils.botcore.Terrain;
import arsenic.utils.botcore.Tuning;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.Options;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BotDriver {
    private static final Minecraft mc = Minecraft.getInstance();

    public static final Bot bot = new Bot(new Tuning(), true);

    private static final Set<Long> ownBlocks = ConcurrentHashMap.newKeySet();

    private static boolean keysHeld;
    private static final MSTimer lastPlace = MSTimer.expired();
    private static BlockPos mining;

    private BotDriver() {
    }

    public static void goTo(Goal goal) {
        bot.setGoal(goal);
    }

    public static boolean isActive() {
        return bot.isActive();
    }

    public static void stop() {
        bot.stop();
        mining = null;
        releaseKeys();
    }

    public static void forgetOwnBlocks() {
        ownBlocks.clear();
    }

    public static void onTick() {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            stop();
            return;
        }
        if (!bot.isActive()) {
            releaseKeys();
            return;
        }
        Controls c;
        try {
            c = bot.tick(snapshot(p), new McWorldView(mc.level));
        } catch (RuntimeException e) {
            e.printStackTrace();
            c = new Controls();
        }
        apply(p, c);
    }

    private static PlayerView snapshot(LocalPlayer p) {
        PlayerView v = new PlayerView();
        v.x = p.getX();
        v.y = p.getY();
        v.z = p.getZ();
        Vec3 m = p.getDeltaMovement();
        v.motionX = m.x;
        v.motionY = m.y;
        v.motionZ = m.z;
        v.yaw = p.getYRot();
        v.pitch = p.getXRot();
        v.onGround = p.onGround();
        v.collidedHorizontally = p.horizontalCollision;
        v.sprinting = p.isSprinting();
        v.speed = speedOf(p);
        v.blocks = hotbarBlocks(p);
        v.pickaxe = pickaxeSlot(p) != -1;
        return v;
    }

    private static double speedOf(LocalPlayer p) {
        AttributeInstance attr = p.getAttribute(Attributes.MOVEMENT_SPEED);
        double value = attr.getValue();
        if (p.isSprinting()) value /= 1.3;
        return Math.max(0.5, Math.min(3, value / attr.getBaseValue()));
    }

    private static void apply(LocalPlayer p, Controls c) {
        if (c.setYaw) {
            p.setYRot(p.getYRot() + (Mth.wrapDegrees(c.yaw - p.getYRot())));
        }
        if (c.setPitch) {
            p.setXRot(Math.max(-90, Math.min(90, c.pitch)));
        }
        Options gs = mc.options;
        gs.keyUp.setDown(c.forward);
        gs.keyDown.setDown(c.back);
        gs.keyLeft.setDown(c.left);
        gs.keyRight.setDown(c.right);
        gs.keyJump.setDown(c.jump);
        gs.keyShift.setDown(c.sneak);
        gs.keySprint.setDown(c.sprint && c.forward && !c.sneak);
        keysHeld = true;
        if (c.place) {
            place(p, c);
        }
        if (c.mine) {
            mine(p, c);
        } else {
            mining = null;
        }
    }

    private static void releaseKeys() {
        if (!keysHeld) return;
        Options gs = mc.options;
        gs.keyUp.setDown(false);
        gs.keyDown.setDown(false);
        gs.keyLeft.setDown(false);
        gs.keyRight.setDown(false);
        gs.keyJump.setDown(false);
        gs.keyShift.setDown(false);
        gs.keySprint.setDown(false);
        keysHeld = false;
    }


    private static void place(LocalPlayer p, Controls c) {
        if (!lastPlace.finished(200)) return;
        int slot = blockSlot(p);
        if (slot == -1) return;
        p.getInventory().setSelectedSlot(slot);
        BlockPos against = new BlockPos(c.againstX, c.againstY, c.againstZ);
        Direction face = Direction.from3DDataValue(c.againstFace);
        Vec3 hit = new Vec3(c.hitX, c.hitY, c.hitZ);
        if (mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, against, false)).consumesAction()) {
            arsenic.utils.minecraft.PlayerUtils.swingItem();
            lastPlace.reset();
            ownBlocks.add(Terrain.key(c.placeX, c.placeY, c.placeZ));
        }
    }

    private static void mine(LocalPlayer p, Controls c) {
        int slot = pickaxeSlot(p);
        if (slot == -1) return;
        p.getInventory().setSelectedSlot(slot);
        BlockPos pos = new BlockPos(c.mineX, c.mineY, c.mineZ);
        Direction face = Direction.from3DDataValue(c.mineFace);
        if (!pos.equals(mining)) {
            mc.gameMode.startDestroyBlock(pos, face);
            mining = pos;
        } else {
            mc.gameMode.continueDestroyBlock(pos, face);
        }
        arsenic.utils.minecraft.PlayerUtils.swingItem();
        if (mc.level.isEmptyBlock(pos)) {
            ownBlocks.remove(Terrain.key(c.mineX, c.mineY, c.mineZ));
            mining = null;
        }
    }


    public static boolean isDarkPrismarine(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(Items.DARK_PRISMARINE);
    }

    static boolean isOwnBlock(int x, int y, int z, BlockState st) {
        if (!ownBlocks.contains(Terrain.key(x, y, z))) return false;
        if (!st.is(Blocks.DARK_PRISMARINE)) {
            ownBlocks.remove(Terrain.key(x, y, z));
            return false;
        }
        return true;
    }

    private static int blockSlot(LocalPlayer p) {
        int selected = p.getInventory().getSelectedSlot();
        if (isDarkPrismarine(p.getInventory().getItem(selected))) return selected;
        int best = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (isDarkPrismarine(s) && (best == -1 || s.getCount() > p.getInventory().getItem(best).getCount())) best = i;
        }
        return best;
    }

    private static int hotbarBlocks(LocalPlayer p) {
        int n = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (isDarkPrismarine(s)) n += s.getCount();
        }
        return n;
    }

    private static int pickaxeSlot(LocalPlayer p) {
        BlockState target = Blocks.DARK_PRISMARINE.defaultBlockState();
        int best = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(ItemTags.PICKAXES)
                    && (best == -1 || s.getDestroySpeed(target) > p.getInventory().getItem(best).getDestroySpeed(target))) {
                best = i;
            }
        }
        return best;
    }
}
