package arsenic.module.impl.world;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.ShowWhen;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.TextProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.bot.McWorldView;
import arsenic.utils.botcore.Box;
import arsenic.utils.buildcore.Blueprint;
import arsenic.utils.buildcore.BuildCore;
import arsenic.utils.buildcore.BuildView;
import arsenic.utils.buildcore.Schematic;
import arsenic.utils.java.FileUtils;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a schematic in creative mode (Build Battle): flies over the plot with flight on, placing and breaking a block
 * a tick, mostly bottom-up, as {@link BuildCore} decides. The schematic is a Sponge .schem file in the Arsenic folder's
 * schematics folder, built centred on where the player stands when the module is turned on, its floor at the feet.
 * Blocks come from the creative inventory: each one wanted is put in one of hotbar slots 1-8, least recently used
 * first; slot 9 is never touched.
 * <p>
 * Rotation: {@code Camera} turns the camera itself. {@code Silent} sends the builder's rotation through the silent
 * rotation manager and leaves the camera to the player; {@code Smooth} does the same and lets the camera drift after
 * the builder's aim, so the build stays in view without the camera snapping around. Movement then follows the silent
 * yaw (MovementFix.STRICT), which is what the builder steers with.
 */
@ModuleInfo(name = "BuildBattle", category = ModuleCategory.PLAYER, tier = ModuleTier.BLATANT)
public class BuildBattle extends Module {

    public final TextProperty schematic = new TextProperty("Schematic", "build.schem", 64);
    public final DoubleProperty reach = new DoubleProperty("Reach", new DoubleValue(3, 5, 4.5, 0.1));
    public final DoubleProperty maxTurn = new DoubleProperty("Max Turn", new DoubleValue(20, 180, 60, 1));
    public final EnumProperty<Rotation> rotation = new EnumProperty<>("Rotation", Rotation.Smooth);
    @ShowWhen(property = "Rotation", is = "Smooth")
    public final DoubleProperty follow = new DoubleProperty("Camera Follow", new DoubleValue(0.1, 3, 0.6, 0.1));

    public enum Rotation { Camera, Silent, Smooth }

    /** Hotbar slots the builder may use: 1 to 8. Slot 9 is left alone (the Build Battle menu item sits there). */
    private static final int SLOTS = 8;

    private BuildCore core;
    private Blueprint bp;
    /** Palette id each hotbar slot was given, and the tick it was last used. */
    private final int[] slotId = new int[9];
    private final int[] slotUsed = new int[9];
    private final Map<Integer, Item> items = new HashMap<>();
    private final Map<String, Integer> idByBlock = new HashMap<>();
    private int ticks;
    /** The rotation the builder wants this tick, handed to the silent rotation manager. */
    private float wantYaw, wantPitch;
    private long lastFrame;
    private boolean keysHeld;

    public static File folder() {
        return new File(FileUtils.getArsenicFolderDirAsFile(), "schematics");
    }

    @Override
    protected void onEnable() {
        core = null;
        LocalPlayer p = mc.player;
        if (p == null) {
            setEnabled(false);
            return;
        }
        File dir = folder();
        dir.mkdirs();
        String name = schematic.getValue().trim();
        File f = new File(dir, name.endsWith(".schem") ? name : name + ".schem");
        try (InputStream in = new FileInputStream(f)) {
            bp = Schematic.read(in).trimmed();
        } catch (Exception e) {
            PlayerUtils.addMessageToChat("BuildBattle: could not read " + f.getPath() + " (" + e.getMessage() + ")");
            setEnabled(false);
            return;
        }
        items.clear();
        idByBlock.clear();
        for (int id = 1; id < bp.palette.size(); id++) {
            String block = Blueprint.blockName(bp.palette.get(id));
            Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(block)).orElse(Items.AIR);
            if (item == Items.AIR) {
                item = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(block)).map(b -> b.asItem()).orElse(Items.AIR);
            }
            if (item != Items.AIR) items.put(id, item);
            idByBlock.putIfAbsent(block, id);
        }
        // blocks with no item to place them with (wall torches, fire, ...) are left to chance
        int skipped = 0;
        for (int y = 0; y < bp.sizeY; y++)
            for (int z = 0; z < bp.sizeZ; z++)
                for (int x = 0; x < bp.sizeX; x++) {
                    int id = bp.get(x, y, z);
                    if (id > 0 && !items.containsKey(id)) {
                        bp.set(x, y, z, Blueprint.ANY);
                        skipped++;
                    }
                }
        BuildCore.Tuning tun = BuildCore.Tuning.best();
        tun.reach = reach.getValue().getInput();
        tun.maxTurn = (float) maxTurn.getValue().getInput();
        tun.gcd = RotationUtils.getGCD();
        BlockPos feet = p.blockPosition();
        core = new BuildCore(bp, feet.getX() - bp.sizeX / 2, feet.getY(), feet.getZ() - bp.sizeZ / 2, tun);
        java.util.Arrays.fill(slotId, -1);
        java.util.Arrays.fill(slotUsed, 0);
        ticks = 0;
        lastFrame = 0;
        PlayerUtils.addMessageToChat("BuildBattle: " + bp.solidCount() + " blocks, " + bp.sizeX + "x" + bp.sizeY + "x" + bp.sizeZ
                + (skipped > 0 ? ", " + skipped + " without an item left out" : ""));
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        releaseKeys();
        core = null;
        super.onDisable();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (core == null)
            return;
        LocalPlayer p = mc.player;
        if (!p.getAbilities().instabuild) {
            PlayerUtils.addMessageToChat("BuildBattle: needs creative mode");
            setEnabled(false);
            return;
        }
        ticks++;
        BuildCore.Input in = new BuildCore.Input();
        in.x = p.getX();
        in.y = p.getY();
        in.z = p.getZ();
        Vec3 m = p.getDeltaMovement();
        in.motionX = m.x;
        in.motionY = m.y;
        in.motionZ = m.z;
        // in the silent modes the builder turns from the rotation it last sent, not from the camera
        SilentRotationManager srm = Arsenic.getArsenic().getSilentRotationManager();
        in.yaw = silent() ? srm.yaw : p.getYRot();
        in.pitch = silent() ? srm.pitch : p.getXRot();
        in.flying = p.getAbilities().flying;
        in.onGround = p.onGround();
        in.sprinting = p.isSprinting();
        in.held = heldId(p);
        BuildCore.Output out;
        try {
            out = core.tick(in, new View(mc.level));
        } catch (RuntimeException e) {
            e.printStackTrace();
            PlayerUtils.addMessageToChat("BuildBattle: stopped (" + e + ")");
            setEnabled(false);
            return;
        }
        if (out.done) {
            PlayerUtils.addMessageToChat("BuildBattle: done in " + ticks / 20 + "s");
            setEnabled(false);
            return;
        }
        if (out.startFlying && p.getAbilities().mayfly && !p.getAbilities().flying) {
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
        }
        wantYaw = out.yaw;
        wantPitch = out.pitch;
        if (!silent()) {
            p.setYRot(out.yaw);
            p.setXRot(out.pitch);
        }
        // the click goes out now, from where the player stands before this tick's move
        if (out.action == BuildCore.Output.PLACE) {
            if (hold(p, out.id)) {
                BlockHitResult hit = new BlockHitResult(new Vec3(out.hitX, out.hitY, out.hitZ), Direction.from3DDataValue(out.face),
                        new BlockPos(out.againstX, out.againstY, out.againstZ), false);
                if (mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, hit).consumesAction())
                    PlayerUtils.swingItem();
            }
        } else if (out.action == BuildCore.Output.BREAK) {
            mc.gameMode.startDestroyBlock(new BlockPos(out.x, out.y, out.z), Direction.from3DDataValue(out.face));
            PlayerUtils.swingItem();
        }
        Options o = mc.options;
        o.keyUp.setDown(out.keys.forward);
        o.keyDown.setDown(out.keys.back);
        o.keyLeft.setDown(out.keys.left);
        o.keyRight.setDown(out.keys.right);
        o.keyJump.setDown(out.keys.up);
        o.keyShift.setDown(out.keys.down);
        o.keySprint.setDown(out.keys.sprint);
        keysHeld = true;
    };

    private boolean silent() {
        return rotation.getValue() != Rotation.Camera;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (core == null || !silent())
            return;
        // the core already capped the turn and snapped it to the mouse grid: apply it exactly
        event.setYaw(wantYaw);
        event.setPitch(wantPitch);
        event.setSpeed(180f);
        event.setSmoothing(false);
        event.setMovementFix(SilentRotationManager.MovementFix.STRICT);
        event.setBlockUserInput(true);
    };

    /** Smooth: once a frame, ease the camera toward the rotation being sent. */
    @EventLink
    public final Listener<EventRender2D> onFrame = event -> {
        long now = System.nanoTime();
        double dt = lastFrame == 0 ? 0 : Math.min(0.1, (now - lastFrame) / 1e9);
        lastFrame = now;
        LocalPlayer p = mc.player;
        if (core == null || p == null || rotation.getValue() != Rotation.Smooth || dt == 0)
            return;
        SilentRotationManager srm = Arsenic.getArsenic().getSilentRotationManager();
        double k = 1 - Math.exp(-dt / follow.getValue().getInput());
        float dy = Mth.wrapDegrees(srm.yaw - p.getYRot()), dp = srm.pitch - p.getXRot();
        // never faster than 150 degrees a second, so a big turn is a pan, not a snap
        double cap = 150 * dt;
        p.setYRot(p.getYRot() + (float) Math.max(-cap, Math.min(cap, dy * k)));
        p.setXRot(Mth.clamp(p.getXRot() + (float) Math.max(-cap, Math.min(cap, dp * k)), -90, 90));
    };

    private void releaseKeys() {
        if (!keysHeld)
            return;
        Options o = mc.options;
        o.keyUp.setDown(false);
        o.keyDown.setDown(false);
        o.keyLeft.setDown(false);
        o.keyRight.setDown(false);
        o.keyJump.setDown(false);
        o.keyShift.setDown(false);
        o.keySprint.setDown(false);
        keysHeld = false;
    }

    private int heldId(LocalPlayer p) {
        int slot = p.getInventory().getSelectedSlot();
        ItemStack s = p.getInventory().getItem(slot);
        if (slotId[slot] >= 0 && s.is(items.get(slotId[slot]))) return slotId[slot];
        return -1;
    }

    /** Puts the block for palette id in the hotbar (from the creative inventory if it is not there) and selects it. */
    private boolean hold(LocalPlayer p, int id) {
        Item item = items.get(id);
        if (item == null)
            return false;
        int slot = -1;
        for (int i = 0; i < SLOTS; i++) {
            if (p.getInventory().getItem(i).is(item)) {
                slot = i;
                break;
            }
        }
        if (slot == -1) {
            slot = 0;
            for (int i = 1; i < SLOTS; i++)
                if (slotUsed[i] < slotUsed[slot]) slot = i;
            ItemStack stack = new ItemStack(item, 64);
            p.getInventory().setItem(slot, stack);
            // hotbar slots are 36-44 of the player's inventory menu
            mc.gameMode.handleCreativeModeItemAdd(stack, 36 + slot);
        }
        slotId[slot] = id;
        slotUsed[slot] = ticks;
        p.getInventory().setSelectedSlot(slot);
        return true;
    }

    /** The world as the core sees it: blocks matched to the schematic's palette by block, not by state. */
    private final class View implements BuildView {
        private final McWorldView world;
        private final Level level;

        View(Level level) {
            this.level = level;
            this.world = new McWorldView(level);
        }

        @Override
        public int idAt(int x, int y, int z) {
            BlockState st = level.getBlockState(new BlockPos(x, y, z));
            if (st.isAir() || st.canBeReplaced() && st.getFluidState().isEmpty()) return Blueprint.AIR;
            String block = BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString();
            int bx = x - core.ox, by = y - core.oy, bz = z - core.oz;
            if (bp.inside(bx, by, bz)) {
                int want = bp.get(bx, by, bz);
                if (want > 0 && Blueprint.blockName(bp.palette.get(want)).equals(block)) return want;
            }
            Integer id = idByBlock.get(block);
            return id == null ? -2 : id;
        }

        @Override
        public void collisionBoxes(int x, int y, int z, List<Box> out) {
            world.collisionBoxes(x, y, z, out);
        }

        @Override
        public void rayBoxes(int x, int y, int z, List<Box> out) {
            world.rayBoxes(x, y, z, out);
        }

        @Override
        public int flags(int x, int y, int z) {
            return world.flags(x, y, z);
        }
    }
}
