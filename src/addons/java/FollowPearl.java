import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.gui.hud.HudElement;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S0EPacketSpawnObject;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Watches pearls thrown by nearby players, works out where the pearl will land, then lobs our own pearl at the same
 * block so we teleport to the enemy instead of to wherever they came from. Detection is the spawn packet plus the
 * velocity the server sends right after it, so the simulated path is exact (1.5 speed, 0.99 drag, 0.03 gravity).
 */
@ModuleInfo(name = "FollowPearl", description = "Throws your pearl to the landing spot of a nearby player's pearl",
        category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class FollowPearl extends Module {

    public final DoubleProperty range = new DoubleProperty("Player Range", new DoubleValue(6, 40, 20, 1));
    public final DoubleProperty maxRange = new DoubleProperty("Max Throw Range", new DoubleValue(8, 40, 26, 1));
    public final DoubleProperty tolerance = new DoubleProperty("Landing Tolerance", new DoubleValue(0.5, 4, 1.5, 0.1));
    public final DoubleProperty speed = new DoubleProperty("Rotation Speed", new DoubleValue(60, 180, 150, 1));
    public final EnumProperty<SilentRotationManager.MovementFix> fix =
            new EnumProperty<>("Movement Fix", SilentRotationManager.MovementFix.SILENT);
    public final DoubleProperty reaction = new DoubleProperty("Reaction Ticks", new DoubleValue(0, 6, 1, 1));
    public final DoubleProperty cooldown = new DoubleProperty("Cooldown Ticks", new DoubleValue(10, 80, 20, 1));
    public final BooleanProperty onGround = new BooleanProperty("Only On Ground", true);
    public final BooleanProperty ignoreTeams = new BooleanProperty("Ignore Teams", true);
    public final BooleanProperty render = new BooleanProperty("Render", true);
    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private final HudElement panel = hudElement("FollowPearl", 4, 220, 120, 16);
    private final Random random = new Random();

    private static final class Flight {
        boolean hit; double x, y, z; int ticks; EnumFacing side; BlockPos block;
    }

    private static final class Track {
        final int id; final EntityPlayer owner; final int seen;
        Track(int id, EntityPlayer owner, int seen) { this.id = id; this.owner = owner; this.seen = seen; }
    }

    private static final class Pending {
        final double x, y, z; final int tick;
        Pending(double x, double y, double z, int tick) { this.x = x; this.y = y; this.z = z; this.tick = tick; }
    }

    private static final class Plan {
        final BlockPos landingBlock; final Vec3 point; final float yaw, pitch; final int ticks;
        Plan(BlockPos b, Vec3 p, float y, float pi, int t) { landingBlock = b; point = p; yaw = y; pitch = pi; ticks = t; }
    }

    // the packet event runs on the netty thread, before the game has processed the packet: only record here
    private final Map<Integer, Pending> spawns = new ConcurrentHashMap<>();
    private final Map<Integer, double[]> velocities = new ConcurrentHashMap<>();
    private Track track;
    private Plan plan;
    private boolean ready;
    private BlockPos shownLanding;
    private int tickCounter, cooldownLeft, reactionTicks, thrown, restoreIn, savedSlot = -1, lastSetSlot = -1;
    private int trackStart, nextPlanTick;

    private static final int PEARL_OBJECT_TYPE = 65;
    private static final int MAX_TRACK_TICKS = 160;

    @Override
    protected void onDisable() {
        restoreSlot();
        spawns.clear();
        velocities.clear();
        track = null;
        plan = null;
        shownLanding = null;
        cooldownLeft = 0;
    }

    @Override
    public boolean isSwappingHotbar() { return lastSetSlot != -1; }

    // ---- detect the throw: spawn packet (type 65) then the velocity packet the server sends with it ----
    // netty thread: only record, the world is read in the tick
    @EventLink
    public final Listener<EventPacket.Incoming.Post> onPacket = event -> {
        Packet<?> packet = event.getPacket();
        if (packet instanceof S0EPacketSpawnObject) {
            S0EPacketSpawnObject spawn = (S0EPacketSpawnObject) packet;
            if (spawn.getType() != PEARL_OBJECT_TYPE) return;
            spawns.put(spawn.getEntityID(), new Pending(spawn.getX() / 32.0, spawn.getY() / 32.0, spawn.getZ() / 32.0, tickCounter));
        } else if (packet instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity velocity = (S12PacketEntityVelocity) packet;
            if (!spawns.containsKey(velocity.getEntityID())) return;
            velocities.put(velocity.getEntityID(),
                    new double[]{velocity.getMotionX() / 8000.0, velocity.getMotionY() / 8000.0, velocity.getMotionZ() / 8000.0});
        }
    };

    // main thread: the pearl entity exists now, so match it to its thrower and start the track
    private void processPending() {
        if (velocities.isEmpty()) return;
        for (Map.Entry<Integer, double[]> entry : velocities.entrySet()) {
            int id = entry.getKey();
            double[] v = entry.getValue();
            velocities.remove(id);
            Pending spawn = spawns.remove(id);
            if (spawn == null) continue;
            if (tickCounter - spawn.tick > 5) continue;
            EntityEnderPearl pearl = pearlEntity(id);
            if (pearl == null) { // the game has not spawned it yet: retry next tick
                spawns.put(id, spawn);
                velocities.put(id, v);
                continue;
            }
            listen(spawn, v, pearl);
        }
    }

    private void listen(Pending spawn, double[] v, EntityEnderPearl pearl) {
        if (mc.theWorld == null || mc.thePlayer == null) return;
        EntityPlayer owner = findOwner(spawn);
        if (owner == null || owner == mc.thePlayer || owner.isSpectator() || !owner.isEntityAlive()) return;
        if (mc.thePlayer.getDistanceToEntity(owner) > range.getValue().getInput()) return;
        if (ignoreTeams.getValue() && PlayerUtils.isEntityTeamSameAsPlayer(owner)) return;

        Flight flight = simulate(pearl.posX, pearl.posY, pearl.posZ, v[0], v[1], v[2]);
        if (!flight.hit || flight.ticks > MAX_TRACK_TICKS) return;
        double horizontal = Math.hypot(flight.x - mc.thePlayer.posX, flight.z - mc.thePlayer.posZ);
        if (horizontal > maxRange.getValue().getInput()) return;

        track = new Track(pearl.getEntityId(), owner, tickCounter);
        plan = null;
        ready = false;
        trackStart = tickCounter;
        nextPlanTick = 0;
        reactionTicks = (int) reaction.getValue().getInput();
    }

    // the pearl spawns 0.16 behind the thrower's eyes, so match the spawn point against every player's eyes
    private EntityPlayer findOwner(Pending spawn) {
        EntityPlayer best = null;
        double bestScore = Double.MAX_VALUE;
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == null || player == mc.thePlayer || !player.isEntityAlive()) continue;
            Vec3 eyes = player.getPositionEyes(1f);
            double yaw = Math.toRadians(player.rotationYaw);
            double ex = eyes.xCoord - Math.cos(yaw) * 0.16;
            double ey = eyes.yCoord - 0.1;
            double ez = eyes.zCoord - Math.sin(yaw) * 0.16;
            double score = Math.sqrt((spawn.x - ex) * (spawn.x - ex) + (spawn.y - ey) * (spawn.y - ey) + (spawn.z - ez) * (spawn.z - ez));
            double box = player.getDistanceToEntity(mc.thePlayer);
            if (box > range.getValue().getInput() + 2) continue;
            if (score < bestScore) { bestScore = score; best = player; }
        }
        return bestScore <= 2.5 ? best : null;
    }

    private EntityEnderPearl pearlEntity(int id) {
        net.minecraft.entity.Entity e = mc.theWorld.getEntityByID(id);
        return e instanceof EntityEnderPearl ? (EntityEnderPearl) e : null;
    }

    @RequiresPlayer @EventLink
    public final Listener<EventTick> onTick = event -> {
        tickCounter++;
        if (restoreIn > 0 && --restoreIn == 0) restoreSlot();
        if (cooldownLeft > 0) cooldownLeft--;
        if (lastSetSlot != -1 && restoreIn == 0 && mc.thePlayer.inventory.currentItem != lastSetSlot) {
            savedSlot = -1;
            lastSetSlot = -1;
        }
        if ((tickCounter & 31) == 0) spawns.values().removeIf(p -> tickCounter - p.tick > 10);
        processPending();
        if (track == null) return;
        if (tickCounter - trackStart > MAX_TRACK_TICKS) { track = null; plan = null; shownLanding = null; ready = false; return; }

        EntityEnderPearl pearl = pearlEntity(track.id);
        if (pearl == null) { track = null; plan = null; ready = false; return; }

        Flight flight = simulate(pearl.posX, pearl.posY, pearl.posZ, pearl.motionX, pearl.motionY, pearl.motionZ);
        if (!flight.hit || flight.ticks < 2) { track = null; plan = null; ready = false; return; }
        shownLanding = new BlockPos(flight.x, flight.y, flight.z);

        if (cooldownLeft > 0 || pearlSlot() == -1 || hotbarBusy()) return;
        if (onGround.getValue() && !mc.thePlayer.onGround) return;
        if (tickCounter - trackStart < reactionTicks) return;

        if (plan == null || tickCounter >= nextPlanTick) {
            nextPlanTick = tickCounter + 5;
            Plan next = solveThrow(flight);
            if (next != null) { plan = next; ready = false; }
        }
        if (ready && plan != null) throwPearl();
    };

    @RequiresPlayer @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (plan == null || mc.currentScreen != null || cooldownLeft > 0) return;
        event.setYaw(plan.yaw);
        event.setPitch(plan.pitch);
        event.setSpeed((float) (speed.getValue().getInput() * (0.95 + random.nextDouble() * 0.1)));
        event.setPreventDuplicateLook(true);
        event.setBlockUserInput(true);
        event.setMovementFix(fix.getValue());
    };

    @RequiresPlayer @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        ready = false;
        if (plan == null || cooldownLeft > 0) return;
        Flight flight = simulateFromRotation(event.getYaw(), event.getPitch());
        if (!flight.hit || flight.side != EnumFacing.UP) return;
        if (distance(flight, plan.point) > tolerance.getValue().getInput()) return;
        ready = true;
    };

    // where to put our pearl: the block the enemy will be standing on when their pearl lands
    private Plan solveThrow(Flight enemy) {
        BlockPos support = null;
        int top = (int) Math.floor(enemy.y);
        for (int y = top; y >= Math.max(0, top - 40); y--) {
            BlockPos pos = new BlockPos(enemy.x, y, enemy.z);
            if (mc.theWorld.getBlockState(pos).getBlock().getMaterial().isSolid()) { support = pos; break; }
        }
        if (support == null) return null;
        double tx = support.getX() + 0.5, ty = support.getY() + 1.0, tz = support.getZ() + 0.5;
        Vec3 point = new Vec3(tx, ty, tz);
        EntityPlayerSP p = mc.thePlayer;
        Vec3 eyes = p.getPositionEyes(1f);
        if (Math.hypot(tx - eyes.xCoord, tz - eyes.zCoord) > maxRange.getValue().getInput() + 1) return null;

        float yaw = yawTo(tx - eyes.xCoord, tz - eyes.zCoord);
        double yr = Math.toRadians(yaw);
        yaw = yawTo(tx - (eyes.xCoord - Math.cos(yr) * 0.16), tz - (eyes.zCoord - Math.sin(yr) * 0.16));

        // coarse sweep for the pitch that lands closest, then a fine one around it
        double closest = Double.MAX_VALUE;
        float closestPitch = Float.NaN;
        for (float pitch = -89.5f; pitch <= 50f; pitch += 1.0f) {
            Flight flight = simulateFrom(yaw, pitch);
            if (!flight.hit || flight.side != EnumFacing.UP) continue;
            double d = distance(flight, point);
            if (d < closest) { closest = d; closestPitch = pitch; }
        }
        if (Float.isNaN(closestPitch)) return null;

        double toleranceDistance = tolerance.getValue().getInput();
        Plan best = null;
        double bestDistance = toleranceDistance;
        for (float pitch = closestPitch - 1.5f; pitch <= closestPitch + 1.5f; pitch += 0.1f) {
            Flight flight = simulateFrom(yaw, pitch);
            if (!flight.hit || flight.side != EnumFacing.UP) continue;
            double d = distance(flight, point);
            if (d > bestDistance) continue;
            bestDistance = d;
            best = new Plan(new BlockPos(flight.x, flight.y, flight.z), point, yaw, pitch, flight.ticks);
        }
        return best;
    }

    // 1.8.9 ender pearl physics
    private Flight simulate(double x, double y, double z, double mx, double my, double mz) {
        Flight f = new Flight();
        for (int t = 0; t < MAX_TRACK_TICKS; t++) {
            Vec3 a = new Vec3(x, y, z), b = new Vec3(x + mx, y + my, z + mz);
            MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(a, b, false, false, false);
            if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                f.hit = true;
                f.x = x; f.y = y; f.z = z;
                f.ticks = t + 1;
                f.side = mop.sideHit;
                f.block = mop.getBlockPos();
                return f;
            }
            x += mx; y += my; z += mz;
            mx *= 0.99; my = my * 0.99 - 0.03; mz *= 0.99;
            if (y < 0) break;
        }
        return f;
    }

    private Flight simulateFromRotation(float yaw, float pitch) {
        return simulateFrom(yaw, pitch);
    }

    private Flight simulateFrom(float yaw, float pitch) {
        EntityPlayerSP p = mc.thePlayer;
        Vec3 eyes = p.getPositionEyes(1f);
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        double sx = eyes.xCoord - Math.cos(yr) * 0.16;
        double sy = eyes.yCoord - 0.1;
        double sz = eyes.zCoord - Math.sin(yr) * 0.16;
        double mx = -Math.sin(yr) * Math.cos(pr) * 1.5;
        double my = -Math.sin(pr) * 1.5;
        double mz = Math.cos(yr) * Math.cos(pr) * 1.5;
        return simulate(sx, sy, sz, mx, my, mz);
    }

    private static double distance(Flight f, Vec3 point) {
        return Math.sqrt((f.x - point.xCoord) * (f.x - point.xCoord)
                + (f.y - point.yCoord) * (f.y - point.yCoord)
                + (f.z - point.zCoord) * (f.z - point.zCoord));
    }

    private static float yawTo(double dx, double dz) { return (float) Math.toDegrees(Math.atan2(-dx, dz)); }

    private int pearlSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.thePlayer.inventory.mainInventory[i];
            if (s != null && s.stackSize > 0 && s.getItem() == Items.ender_pearl) return i;
        }
        return -1;
    }

    private boolean hotbarBusy() {
        for (Module m : Arsenic.getArsenic().getModuleManager().getModules())
            if (m != this && m.isEnabled() && m.isSwappingHotbar()) return true;
        return false;
    }

    private void throwPearl() {
        int slot = pearlSlot();
        if (slot == -1) return;
        if (mc.thePlayer.isUsingItem()) mc.playerController.onStoppedUsingItem(mc.thePlayer);
        if (savedSlot == -1) savedSlot = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = slot;
        lastSetSlot = slot;
        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        if (held == null || held.getItem() != Items.ender_pearl) { restoreSlot(); return; }
        if (mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, held)) {
            mc.thePlayer.swingItem();
            thrown++;
        }
        cooldownLeft = (int) cooldown.getValue().getInput();
        restoreIn = 2;
        ready = false;
        plan = null;
    }

    private void restoreSlot() {
        if (savedSlot != -1 && mc.thePlayer != null && mc.thePlayer.inventory.currentItem == lastSetSlot)
            mc.thePlayer.inventory.currentItem = savedSlot;
        savedSlot = -1;
        lastSetSlot = -1;
        restoreIn = 0;
    }

    @RequiresPlayer @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorld = event -> {
        if (!render.getValue() || shownLanding == null || mc.theWorld == null) return;
        int main = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
        // the enemy pearl's landing
        RenderUtils.renderBlock(shownLanding, ColorUtils.withAlpha(main, 110), false, true);
        RenderUtils.renderBlock(shownLanding, ColorUtils.withAlpha(main, 230), true, false);
        // where our pearl would land
        if (plan != null) {
            BlockPos ours = plan.landingBlock;
            RenderUtils.renderBlock(ours, ColorUtils.withAlpha(0xFFFFFF, 90), false, true);
            RenderUtils.renderBlock(ours, ColorUtils.withAlpha(0xFFFFFF, 200), true, false);
        }
    };

    @EventLink
    public final Listener<EventRender2D> onRenderHud = event -> {
        if (!hud.getValue()) return;
        String text;
        if (track == null) text = "FollowPearl: idle";
        else if (pearlSlot() == -1) text = "FollowPearl: no pearl";
        else if (cooldownLeft > 0) text = "FollowPearl: away";
        else if (plan == null) text = "FollowPearl: no path";
        else text = ready ? "FollowPearl: throwing" : "FollowPearl: aiming";
        text += "  x" + thrown;
        panel.setSize(Math.max(90, mc.fontRendererObj.getStringWidth(text) + 12), 16);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        mc.fontRendererObj.drawStringWithShadow(text, panel.x + 6, panel.y + 4, 0xFFFFFFFF);
    };
}
