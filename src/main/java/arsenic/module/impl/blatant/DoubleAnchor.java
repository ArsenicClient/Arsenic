package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventPlayerJoinWorld;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.notifications.Notification;
import arsenic.notifications.NotificationManager;
import arsenic.notifications.NotificationType;
import arsenic.utils.io.Keys;
import arsenic.utils.minecraft.HotbarUse;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Puts a second anchor straight back where the one you just blew up stood, so you can charge and pop it again. A bind
 * can also run the whole place, charge, explode, re-place, charge, explode sequence at your crosshair.
 */
@ModuleInfo(name = "DoubleAnchor", description = "Places another anchor in the air after you explode one", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class DoubleAnchor extends Module {

    public enum Bind {
        None, Middle, Mouse4, Mouse5, R, F, G, V, X, Z, C, Q, Tab, Left_Alt, Caps_Lock
    }

    public enum SwapMode {
        None, Swap
    }

    private enum State {
        IDLE, PLACE_1, GLOW_1, EXPLODE_DOUBLE_1, GLOW_2, EXPLODE_2, RESTORE
    }

    /** Minimum time between two automatic double placements. */
    private static final long DOUBLE_COOLDOWN_MS = 200;
    /** How long an anchor spot stays "already doubled" before it can be doubled again. */
    private static final long SAME_SPOT_MEMORY_MS = 2000;

    public final DoubleProperty chance = new DoubleProperty("Chance", new DoubleValue(0, 100, 100, 1));
    public final BooleanProperty glowstone = new BooleanProperty("Glowstone", false);
    public final RangeProperty glowDelay = new RangeProperty("Glowstone Delay (ms)", new RangeValue(0, 500, 50, 100, 1));

    public final EnumProperty<Bind> macroBind = new EnumProperty<>("Macro Bind", Bind.None);
    public final BooleanProperty holdRepeat = new BooleanProperty("Macro Hold Repeat", false);
    public final RangeProperty macroDelay = new RangeProperty("Macro Delay (ms)", new RangeValue(0, 500, 30, 50, 1));
    public final EnumProperty<SwapMode> macroSwap = new EnumProperty<>("Macro Swap", SwapMode.Swap);
    public final BooleanProperty swapBack = new BooleanProperty("Macro Swap Back", true);
    public final DoubleProperty explodeSlot = new DoubleProperty("Macro Explode Slot", new DoubleValue(1, 9, 8, 1));
    public final BooleanProperty damageTick = new BooleanProperty("Macro Damage Tick", true);
    public final DoubleProperty damageTickRange = new DoubleProperty("Macro Damage Tick Range", new DoubleValue(1, 20, 10, 0.5));
    public final DoubleProperty damageTickMaxWait = new DoubleProperty("Macro Damage Tick Max Wait (ms)", new DoubleValue(0, 2000, 500, 10));
    public final BooleanProperty notify = new BooleanProperty("Notifications", true);

    // automatic double
    private final MSTimer doubleTimer = MSTimer.expired();
    private final MSTimer glowTimer = new MSTimer();
    private BlockHitResult pendingDouble;
    private boolean glowPending;
    private double nextGlowDelay;
    private BlockPos lastDoubled;
    private long lastDoubledAt;

    // macro
    private final MSTimer stageTimer = new MSTimer();
    private final MSTimer damageTimer = new MSTimer();
    private double nextStageDelay;
    private State state = State.IDLE;
    private BlockHitResult startHit;
    private BlockPos anchorPos;
    private int originalSlot = -1;
    private boolean bindConsumed;
    private boolean waitingNotified;

    /** True while this module is sending its own interactions, so they are not taken for a manual explode. */
    private boolean acting;

    public DoubleAnchor() {
        glowDelay.setVisible(glowstone::getValue);
        holdRepeat.setVisible(this::macroOn);
        macroDelay.setVisible(this::macroOn);
        macroSwap.setVisible(this::macroOn);
        swapBack.setVisible(() -> macroOn() && macroSwap.getValue() == SwapMode.Swap);
        explodeSlot.setVisible(() -> macroOn() && macroSwap.getValue() == SwapMode.Swap);
        damageTick.setVisible(this::macroOn);
        damageTickRange.setVisible(() -> macroOn() && damageTick.getValue());
        damageTickMaxWait.setVisible(() -> macroOn() && damageTick.getValue());
    }

    private boolean macroOn() {
        return macroBind.getValue() != Bind.None;
    }

    @Override
    public boolean isSwappingHotbar() {
        return state != State.IDLE || acting;
    }

    @Override
    protected void onEnable() {
        resetMacro();
        bindConsumed = false;
        pendingDouble = null;
        glowPending = false;
    }

    @Override
    protected void onDisable() {
        if (state != State.IDLE && macroSwap.getValue() == SwapMode.Swap && originalSlot != -1 && mc.player != null)
            mc.player.getInventory().setSelectedSlot(originalSlot);
        resetMacro();
        bindConsumed = false;
        pendingDouble = null;
        glowPending = false;
        lastDoubled = null;
    }

    @EventLink
    public final Listener<EventPlayerJoinWorld> onJoin = event -> {
        resetMacro();
        pendingDouble = null;
        glowPending = false;
        lastDoubled = null;
    };

    private void resetMacro() {
        state = State.IDLE;
        startHit = null;
        anchorPos = null;
        originalSlot = -1;
        waitingNotified = false;
    }

    // ---------------------------------------------------------------- automatic double

    /**
     * You clicked a charged anchor while holding an anchor: that blows it up, so queue a second click on the same spot,
     * which the server reads as placing an anchor into the now empty block. Sent at the start of the player tick, right
     * after the vanilla click in the same client tick, so the packets stay in order.
     */
    @RequiresPlayer
    @EventLink
    public final Listener<EventPacket.OutGoing> onOutgoing = event -> {
        if (acting || state != State.IDLE || !(event.getPacket() instanceof ServerboundUseItemOnPacket packet))
            return;
        if (packet.hand() != InteractionHand.MAIN_HAND || mc.level == null)
            return;
        if (!doubleTimer.hasTimeElapsed(DOUBLE_COOLDOWN_MS) || !mc.player.getMainHandItem().is(Items.RESPAWN_ANCHOR))
            return;
        BlockHitResult hit = crosshairBlock();
        if (hit == null || !hit.getBlockPos().equals(packet.hitResult().getBlockPos()))
            return;
        BlockState clicked = mc.level.getBlockState(hit.getBlockPos());
        if (!clicked.is(Blocks.RESPAWN_ANCHOR) || charges(clicked) == 0)
            return;
        if (ThreadLocalRandom.current().nextDouble(100) > chance.getValue().getInput())
            return;
        long now = System.currentTimeMillis();
        if (hit.getBlockPos().equals(lastDoubled) && now - lastDoubledAt < SAME_SPOT_MEMORY_MS)
            return;
        lastDoubled = hit.getBlockPos();
        lastDoubledAt = now;
        doubleTimer.reset();
        pendingDouble = hit;
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.level == null)
            return;

        if (pendingDouble != null) {
            BlockHitResult hit = pendingDouble;
            pendingDouble = null;
            if (mc.player.getMainHandItem().is(Items.RESPAWN_ANCHOR)) {
                interact(hit);
                if (glowstone.getValue()) {
                    glowPending = true;
                    nextGlowDelay = glowDelay.getValue().getRandomInRange();
                    glowTimer.reset();
                }
            }
        }

        if (glowPending && glowTimer.getTime() >= nextGlowDelay) {
            glowPending = false;
            BlockHitResult hit = crosshairBlock();
            if (hit != null) {
                BlockState st = mc.level.getBlockState(hit.getBlockPos());
                int slot = HotbarUse.find(s -> s.is(Items.GLOWSTONE));
                if (st.is(Blocks.RESPAWN_ANCHOR) && charges(st) == 0 && slot != -1) {
                    mc.player.getInventory().setSelectedSlot(slot);
                    interact(hit);
                }
            }
        }

        tickMacro();
    };

    // ---------------------------------------------------------------- macro

    private void tickMacro() {
        if (!macroOn() || mc.gui.screen() != null || !mc.isWindowActive() || mc.player.isUsingItem() || mc.player.isShiftKeyDown())
            return;

        if (state != State.IDLE) {
            if (stageTimer.getTime() >= nextStageDelay)
                step();
            return;
        }

        if (!bindDown()) {
            bindConsumed = false;
            return;
        }
        if (bindConsumed && !holdRepeat.getValue())
            return;
        if (stageTimer.getTime() < nextStageDelay)
            return;

        BlockHitResult hit = crosshairBlock();
        if (hit == null)
            return;
        BlockState clicked = mc.level.getBlockState(hit.getBlockPos());
        if (!canPlaceAgainst(clicked) || clicked.is(Blocks.RESPAWN_ANCHOR))
            return;
        BlockPos target = clicked.canBeReplaced() ? hit.getBlockPos() : hit.getBlockPos().relative(hit.getDirection());
        if (mc.player.getBoundingBox().intersects(new AABB(target)))
            return;

        bindConsumed = true;
        startHit = hit;
        anchorPos = target;
        originalSlot = mc.player.getInventory().getSelectedSlot();
        state = State.PLACE_1;
        nextStage();
        info("Sequence started");
    }

    private void step() {
        acting = true;
        try {
            switch (state) {
                case PLACE_1 -> {
                    if (mc.player.getBoundingBox().intersects(new AABB(anchorPos))) {
                        abort("you are in the way");
                        return;
                    }
                    if (!useItem(Items.RESPAWN_ANCHOR, startHit)) {
                        abort("no respawn anchor");
                        return;
                    }
                    state = State.GLOW_1;
                }
                case GLOW_1 -> {
                    if (!isAnchor(anchorPos)) {
                        abort("anchor missing");
                        return;
                    }
                    if (!useItem(Items.GLOWSTONE, anchorHit(anchorPos))) {
                        abort("no glowstone");
                        return;
                    }
                    state = State.EXPLODE_DOUBLE_1;
                }
                case EXPLODE_DOUBLE_1 -> {
                    if (!isCharged(anchorPos)) {
                        abort("anchor not charged");
                        return;
                    }
                    BlockHitResult hit = anchorHit(anchorPos);
                    if (!explode(hit)) {
                        abort("explode failed");
                        return;
                    }
                    // the anchor is gone server side: the same click now puts a new one in its place
                    useItem(Items.RESPAWN_ANCHOR, hit);
                    state = State.GLOW_2;
                }
                case GLOW_2 -> {
                    if (!isAnchor(anchorPos)) {
                        abort("double place failed");
                        return;
                    }
                    if (!useItem(Items.GLOWSTONE, anchorHit(anchorPos))) {
                        abort("no glowstone");
                        return;
                    }
                    damageTimer.reset();
                    waitingNotified = false;
                    state = State.EXPLODE_2;
                }
                case EXPLODE_2 -> {
                    if (!isCharged(anchorPos)) {
                        abort("second anchor not charged");
                        return;
                    }
                    if (shouldWaitForDamageTick()) {
                        if (!waitingNotified) {
                            info("Waiting for the damage tick");
                            waitingNotified = true;
                        }
                        return;
                    }
                    if (!explode(anchorHit(anchorPos))) {
                        abort("explode failed");
                        return;
                    }
                    state = State.RESTORE;
                }
                case RESTORE -> {
                    if (swapBack.getValue() && macroSwap.getValue() == SwapMode.Swap && originalSlot != -1)
                        mc.player.getInventory().setSelectedSlot(originalSlot);
                    info("Done");
                    resetMacro();
                }
                default -> {
                }
            }
            nextStage();
        } finally {
            acting = false;
        }
    }

    private void nextStage() {
        stageTimer.reset();
        nextStageDelay = macroDelay.getValue().getRandomInRange();
    }

    private void abort(String reason) {
        if (macroSwap.getValue() == SwapMode.Swap && originalSlot != -1)
            mc.player.getInventory().setSelectedSlot(originalSlot);
        info("Aborted: " + reason);
        resetMacro();
        nextStage();
    }

    /** Holds off the second explosion while the nearest enemy is still invulnerable from the first one. */
    private boolean shouldWaitForDamageTick() {
        if (!damageTick.getValue())
            return false;
        Player target = nearestEnemy(damageTickRange.getValue().getInput());
        if (target == null || target.hurtTime == 0)
            return false;
        return damageTimer.getTime() < damageTickMaxWait.getValue().getInput();
    }

    private static Player nearestEnemy(double range) {
        Player best = null;
        double bestDist = range;
        for (Player p : mc.level.players()) {
            if (p == mc.player || p.isDeadOrDying() || AntiBot.isBot(p) || Arsenic.getArsenic().getFriendManager().isFriend(p))
                continue;
            double d = mc.player.distanceTo(p);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    /** Clicks with the item: in None mode it must already be in hand, in Swap mode the hotbar switches to it. */
    private boolean useItem(Item item, BlockHitResult hit) {
        if (macroSwap.getValue() == SwapMode.None) {
            if (!mc.player.getMainHandItem().is(item))
                return false;
        } else {
            int slot = HotbarUse.find(s -> s.is(item));
            if (slot == -1)
                return false;
            mc.player.getInventory().setSelectedSlot(slot);
        }
        interact(hit);
        return true;
    }

    /** Clicks a charged anchor with something that is not glowstone, which sets it off. */
    private boolean explode(BlockHitResult hit) {
        if (macroSwap.getValue() == SwapMode.None) {
            if (mc.player.getMainHandItem().is(Items.GLOWSTONE))
                return false;
        } else {
            mc.player.getInventory().setSelectedSlot((int) explodeSlot.getValue().getInput() - 1);
        }
        interact(hit);
        return true;
    }

    // ---------------------------------------------------------------- helpers

    private void interact(BlockHitResult hit) {
        boolean was = acting;
        acting = true;
        try {
            if (mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit).consumesAction())
                PlayerUtils.swingItem();
        } finally {
            acting = was;
        }
    }

    /** The crosshair hit when it is on the anchor (best for placement checks), otherwise the top face centre. */
    private static BlockHitResult anchorHit(BlockPos pos) {
        BlockHitResult cross = crosshairBlock();
        if (cross != null && cross.getBlockPos().equals(pos))
            return cross;
        return new BlockHitResult(new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5), Direction.UP, pos, false);
    }

    private static BlockHitResult crosshairBlock() {
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK)
            return bhr;
        return null;
    }

    private static boolean isAnchor(BlockPos pos) {
        return mc.level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR);
    }

    private static boolean isCharged(BlockPos pos) {
        BlockState st = mc.level.getBlockState(pos);
        return st.is(Blocks.RESPAWN_ANCHOR) && charges(st) > 0;
    }

    private static int charges(BlockState state) {
        return state.getValue(RespawnAnchorBlock.CHARGE);
    }

    /** Something an anchor can be put on or into: a solid block, fire, short grass or a single snow layer. */
    private static boolean canPlaceAgainst(BlockState state) {
        return !state.canBeReplaced()
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SHORT_GRASS)
                || (state.is(Blocks.SNOW) && state.getValue(SnowLayerBlock.LAYERS) == 1);
    }

    private void info(String msg) {
        if (notify.getValue())
            NotificationManager.show(new Notification(NotificationType.INFO, getName(), msg, 1));
    }

    private boolean bindDown() {
        Bind bind = macroBind.getValue();
        if (bind == Bind.None)
            return false;
        int mask = switch (bind) {
            case Middle -> 2;
            case Mouse4 -> 8;
            case Mouse5 -> 16;
            default -> 0;
        };
        if (mask != 0)
            return (org.lwjgl.sdl.SDLMouse.SDL_GetMouseState(null, null) & mask) != 0;
        return Keys.isKeyDown(Keys.getKeyIndex(bind.name()));
    }
}
