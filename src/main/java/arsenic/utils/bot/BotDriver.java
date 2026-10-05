package arsenic.utils.bot;

import arsenic.utils.timer.MSTimer;
import arsenic.utils.botcore.Bot;
import arsenic.utils.botcore.Controls;
import arsenic.utils.botcore.Goal;
import arsenic.utils.botcore.PlayerView;
import arsenic.utils.botcore.Terrain;
import arsenic.utils.botcore.Tuning;
import net.minecraft.block.BlockPrismarine;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BotDriver {
    private static final Minecraft mc = Minecraft.getMinecraft();

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
        EntityPlayerSP p = mc.thePlayer;
        if (p == null || mc.theWorld == null) {
            stop();
            return;
        }
        if (!bot.isActive()) {
            releaseKeys();
            return;
        }
        Controls c;
        try {
            c = bot.tick(snapshot(p), new McWorldView(mc.theWorld));
        } catch (RuntimeException e) {
            e.printStackTrace();
            c = new Controls();
        }
        apply(p, c);
    }

    private static PlayerView snapshot(EntityPlayerSP p) {
        PlayerView v = new PlayerView();
        v.x = p.posX;
        v.y = p.posY;
        v.z = p.posZ;
        v.motionX = p.motionX;
        v.motionY = p.motionY;
        v.motionZ = p.motionZ;
        v.yaw = p.rotationYaw;
        v.pitch = p.rotationPitch;
        v.onGround = p.onGround;
        v.collidedHorizontally = p.isCollidedHorizontally;
        v.sprinting = p.isSprinting();
        v.speed = speedOf(p);
        v.blocks = hotbarBlocks(p);
        v.pickaxe = pickaxeSlot(p) != -1;
        return v;
    }

    private static double speedOf(EntityPlayerSP p) {
        IAttributeInstance attr = p.getEntityAttribute(SharedMonsterAttributes.movementSpeed);
        double value = attr.getAttributeValue();
        if (p.isSprinting()) value /= 1.3;
        return Math.max(0.5, Math.min(3, value / attr.getBaseValue()));
    }

    private static void apply(EntityPlayerSP p, Controls c) {
        if (c.setYaw) {
            p.rotationYaw += MathHelper.wrapAngleTo180_float(c.yaw - p.rotationYaw);
        }
        if (c.setPitch) {
            p.rotationPitch = Math.max(-90, Math.min(90, c.pitch));
        }
        GameSettings gs = mc.gameSettings;
        KeyBinding.setKeyBindState(gs.keyBindForward.getKeyCode(), c.forward);
        KeyBinding.setKeyBindState(gs.keyBindBack.getKeyCode(), c.back);
        KeyBinding.setKeyBindState(gs.keyBindLeft.getKeyCode(), c.left);
        KeyBinding.setKeyBindState(gs.keyBindRight.getKeyCode(), c.right);
        KeyBinding.setKeyBindState(gs.keyBindJump.getKeyCode(), c.jump);
        KeyBinding.setKeyBindState(gs.keyBindSneak.getKeyCode(), c.sneak);
        KeyBinding.setKeyBindState(gs.keyBindSprint.getKeyCode(), c.sprint && c.forward && !c.sneak);
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
        GameSettings gs = mc.gameSettings;
        KeyBinding.setKeyBindState(gs.keyBindForward.getKeyCode(), false);
        KeyBinding.setKeyBindState(gs.keyBindBack.getKeyCode(), false);
        KeyBinding.setKeyBindState(gs.keyBindLeft.getKeyCode(), false);
        KeyBinding.setKeyBindState(gs.keyBindRight.getKeyCode(), false);
        KeyBinding.setKeyBindState(gs.keyBindJump.getKeyCode(), false);
        KeyBinding.setKeyBindState(gs.keyBindSneak.getKeyCode(), false);
        KeyBinding.setKeyBindState(gs.keyBindSprint.getKeyCode(), false);
        keysHeld = false;
    }


    private static void place(EntityPlayerSP p, Controls c) {
        if (!lastPlace.finished(200)) return;
        int slot = blockSlot(p);
        if (slot == -1) return;
        p.inventory.currentItem = slot;
        BlockPos against = new BlockPos(c.againstX, c.againstY, c.againstZ);
        EnumFacing face = EnumFacing.getFront(c.againstFace);
        Vec3 hit = new Vec3(c.hitX, c.hitY, c.hitZ);
        if (mc.playerController.onPlayerRightClick(p, mc.theWorld, p.getHeldItem(), against, face, hit)) {
            p.swingItem();
            lastPlace.reset();
            ownBlocks.add(Terrain.key(c.placeX, c.placeY, c.placeZ));
        }
    }

    private static void mine(EntityPlayerSP p, Controls c) {
        int slot = pickaxeSlot(p);
        if (slot == -1) return;
        p.inventory.currentItem = slot;
        BlockPos pos = new BlockPos(c.mineX, c.mineY, c.mineZ);
        EnumFacing face = EnumFacing.getFront(c.mineFace);
        if (!pos.equals(mining)) {
            mc.playerController.clickBlock(pos, face);
            mining = pos;
        } else {
            mc.playerController.onPlayerDamageBlock(pos, face);
        }
        p.swingItem();
        if (mc.theWorld.isAirBlock(pos)) {
            ownBlocks.remove(Terrain.key(c.mineX, c.mineY, c.mineZ));
            mining = null;
        }
    }


    public static boolean isDarkPrismarine(ItemStack stack) {
        return stack != null && stack.stackSize > 0
                && stack.getItem() == Item.getItemFromBlock(Blocks.prismarine)
                && stack.getMetadata() == BlockPrismarine.DARK_META;
    }

    static boolean isOwnBlock(int x, int y, int z, IBlockState st) {
        if (!ownBlocks.contains(Terrain.key(x, y, z))) return false;
        if (st.getBlock() != Blocks.prismarine || st.getValue(BlockPrismarine.VARIANT) != BlockPrismarine.EnumType.DARK) {
            ownBlocks.remove(Terrain.key(x, y, z));
            return false;
        }
        return true;
    }

    private static int blockSlot(EntityPlayerSP p) {
        ItemStack[] inv = p.inventory.mainInventory;
        if (isDarkPrismarine(inv[p.inventory.currentItem])) return p.inventory.currentItem;
        int best = -1;
        for (int i = 0; i < 9; i++) {
            if (isDarkPrismarine(inv[i]) && (best == -1 || inv[i].stackSize > inv[best].stackSize)) best = i;
        }
        return best;
    }

    private static int hotbarBlocks(EntityPlayerSP p) {
        int n = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.inventory.mainInventory[i];
            if (isDarkPrismarine(s)) n += s.stackSize;
        }
        return n;
    }

    private static int pickaxeSlot(EntityPlayerSP p) {
        ItemStack[] inv = p.inventory.mainInventory;
        int best = -1;
        for (int i = 0; i < 9; i++) {
            if (inv[i] != null && inv[i].getItem() instanceof ItemPickaxe
                    && (best == -1 || inv[i].getStrVsBlock(Blocks.prismarine) > inv[best].getStrVsBlock(Blocks.prismarine))) {
                best = i;
            }
        }
        return best;
    }
}
