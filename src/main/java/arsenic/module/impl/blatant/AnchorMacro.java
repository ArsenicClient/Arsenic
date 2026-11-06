package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventPlayerJoinWorld;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.notifications.Notification;
import arsenic.notifications.NotificationManager;
import arsenic.notifications.NotificationType;
import arsenic.utils.minecraft.HotbarUse;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Respawn anchor PvP from the crosshair: a bind places an anchor, glowstone charges the anchor you look at, and a
 * charged anchor is blown up while holding the slot you pick (keep a totem there). Each step can use the held item,
 * swap to it, or swap for one click and back, with a random delay between actions.
 */
@ModuleInfo(name = "AnchorMacro", description = "Places, charges and explodes respawn anchors at your crosshair", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class AnchorMacro extends Module {

    public enum SwapMode {
        None, Swap, Silent
    }

    public enum PlaceKey {
        None, Middle, Mouse4, Mouse5
    }

    public final BooleanProperty onlyOwn = new BooleanProperty("Only Own Anchors", false);
    public final RangeProperty delay = new RangeProperty("Delay (ms)", new RangeValue(0, 200, 30, 50, 1));

    public final BooleanProperty place = new BooleanProperty("Place", true);
    public final EnumProperty<PlaceKey> placeBind = new EnumProperty<>("Place Bind", PlaceKey.None);
    public final EnumProperty<SwapMode> placeSwap = new EnumProperty<>("Place Swap", SwapMode.None);
    public final BooleanProperty placeSwapBack = new BooleanProperty("Place Swap Back", true);

    public final BooleanProperty glowstone = new BooleanProperty("Glowstone", true);
    public final EnumProperty<SwapMode> glowstoneSwap = new EnumProperty<>("Glowstone Swap", SwapMode.None);
    public final BooleanProperty glowstoneSwapBack = new BooleanProperty("Glowstone Swap Back", true);
    public final BooleanProperty onlyOnPlaceBind = new BooleanProperty("Only On Place Bind", false);

    public final BooleanProperty explode = new BooleanProperty("Explode", true);
    public final EnumProperty<SwapMode> explodeSwap = new EnumProperty<>("Explode Swap", SwapMode.None);
    public final DoubleProperty explodeSlot = new DoubleProperty("Explode Slot", new DoubleValue(1, 9, 1, 1));
    public final BooleanProperty explodeSwapBack = new BooleanProperty("Explode Swap Back", true);

    /** Anchors this client placed (filled only with Only Own Anchors on). Written from the network thread too. */
    private final Set<BlockPos> ownAnchors = ConcurrentHashMap.newKeySet();
    /** Anchors placed with the Place Bind, for Only On Place Bind. */
    private final Set<BlockPos> boundAnchors = ConcurrentHashMap.newKeySet();

    private final MSTimer timer = new MSTimer();
    private double nextDelay;
    private boolean placedThisPress;
    private int placeRestore = -1, glowRestore = -1, explodeRestore = -1;

    public AnchorMacro() {
        placeBind.setVisible(place::getValue);
        placeSwap.setVisible(place::getValue);
        placeSwapBack.setVisible(() -> place.getValue() && placeSwap.getValue() == SwapMode.Swap);
        glowstoneSwap.setVisible(glowstone::getValue);
        glowstoneSwapBack.setVisible(() -> glowstone.getValue() && glowstoneSwap.getValue() == SwapMode.Swap);
        onlyOnPlaceBind.setVisible(glowstone::getValue);
        explodeSwap.setVisible(explode::getValue);
        explodeSlot.setVisible(explode::getValue);
        explodeSwapBack.setVisible(() -> explode.getValue() && explodeSwap.getValue() == SwapMode.Swap);
    }

    @Override
    public boolean isSwappingHotbar() {
        return placeRestore != -1 || glowRestore != -1 || explodeRestore != -1;
    }

    @Override
    protected void onEnable() {
        nextDelay = delay.getValue().getRandomInRange();
        timer.reset();
        placedThisPress = false;
    }

    @Override
    protected void onDisable() {
        // put back whichever slot a Swap step left us on (they are taken in order, so the first is the original)
        int restore = placeRestore != -1 ? placeRestore : glowRestore != -1 ? glowRestore : explodeRestore;
        if (restore != -1 && mc.player != null)
            mc.player.getInventory().setSelectedSlot(restore);
        placeRestore = glowRestore = explodeRestore = -1;
        ownAnchors.clear();
        boundAnchors.clear();
    }

    @EventLink
    public final Listener<EventPlayerJoinWorld> onJoin = event -> {
        ownAnchors.clear();
        boundAnchors.clear();
        placeRestore = glowRestore = explodeRestore = -1;
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.level == null || mc.gui.screen() != null || !mc.isWindowActive() || mc.player.isUsingItem())
            return;
        if (timer.getTime() < nextDelay)
            return;
        timer.reset();
        nextDelay = delay.getValue().getRandomInRange();

        if (onlyOwn.getValue())
            ownAnchors.removeIf(pos -> !mc.level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR));

        if (place.getValue() && doPlace())
            return;
        if (glowstone.getValue() && doCharge())
            return;
        if (explode.getValue())
            doExplode();
    };

    /** Remembers where our own anchors land, from the place packet itself (so it also covers manual placing). */
    @RequiresPlayer
    @EventLink
    public final Listener<EventPacket.OutGoing> onOutgoing = event -> {
        if (!onlyOwn.getValue() || !(event.getPacket() instanceof ServerboundUseItemOnPacket packet))
            return;
        ItemStack stack = packet.hand() == InteractionHand.MAIN_HAND ? mc.player.getMainHandItem() : mc.player.getOffhandItem();
        if (!stack.is(Items.RESPAWN_ANCHOR) || mc.level == null)
            return;
        BlockHitResult hit = packet.hitResult();
        BlockState clicked = mc.level.getBlockState(hit.getBlockPos());
        ownAnchors.add(clicked.canBeReplaced() && !clicked.is(Blocks.RESPAWN_ANCHOR)
                ? hit.getBlockPos() : hit.getBlockPos().relative(hit.getDirection()));
    };

    /** An anchor that blew up or was broken is forgotten. Runs on the network thread. */
    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onIncoming = event -> {
        if (!(event.getPacket() instanceof ClientboundBlockUpdatePacket packet))
            return;
        BlockState state = packet.getBlockState();
        if (state == null || state.is(Blocks.RESPAWN_ANCHOR))
            return;
        ownAnchors.remove(packet.getPos());
        boundAnchors.remove(packet.getPos());
    };

    /** Place Bind: puts an anchor where you look, or charges an empty anchor there, once per press. */
    private boolean doPlace() {
        if (placeBind.getValue() == PlaceKey.None)
            return false;
        if (placeRestore != -1) {
            mc.player.getInventory().setSelectedSlot(placeRestore);
            placeRestore = -1;
            return false;
        }
        if (!bindDown()) {
            placedThisPress = false;
            return false;
        }
        if (placedThisPress)
            return false;
        BlockHitResult hit = crosshairBlock();
        if (hit == null)
            return false;

        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        boolean isAnchor = state.is(Blocks.RESPAWN_ANCHOR);
        // a charged anchor is Explode's job; clicking it with anything but glowstone would blow it up here
        if (isAnchor && charges(state) != 0)
            return false;
        boolean charge = isAnchor;
        if (!charge && !canPlaceAgainst(state))
            return false;

        Item item = charge ? Items.GLOWSTONE : Items.RESPAWN_ANCHOR;
        int silentRestore = -1;
        switch (placeSwap.getValue()) {
            case None -> {
                if (!mc.player.getMainHandItem().is(item))
                    return false;
            }
            case Swap -> {
                int slot = HotbarUse.find(s -> s.is(item));
                if (slot == -1)
                    return false;
                if (placeSwapBack.getValue() && slot != selected())
                    placeRestore = selected();
                mc.player.getInventory().setSelectedSlot(slot);
            }
            case Silent -> {
                int slot = HotbarUse.find(s -> s.is(item));
                if (slot == -1)
                    return false;
                silentRestore = selected();
                mc.player.getInventory().setSelectedSlot(slot);
            }
        }

        if (!charge)
            boundAnchors.add(state.canBeReplaced() ? pos : pos.relative(hit.getDirection()));
        NotificationManager.show(new Notification(NotificationType.INFO, getName(), charge ? "Charging an anchor" : "Placing an anchor", 1));
        interact(hit);
        if (silentRestore != -1)
            mc.player.getInventory().setSelectedSlot(silentRestore);
        placedThisPress = true;
        return true;
    }

    /** Glowstone: charges an empty anchor under the crosshair. */
    private boolean doCharge() {
        if (glowRestore != -1) {
            mc.player.getInventory().setSelectedSlot(glowRestore);
            glowRestore = -1;
            return false;
        }
        BlockHitResult hit = crosshairBlock();
        if (hit == null || mc.player.isShiftKeyDown())
            return false;
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (!state.is(Blocks.RESPAWN_ANCHOR) || charges(state) != 0)
            return false;
        if (onlyOwn.getValue() && !ownAnchors.contains(pos))
            return false;
        if (onlyOnPlaceBind.getValue() && !boundAnchors.contains(pos))
            return false;

        int silentRestore = -1;
        switch (glowstoneSwap.getValue()) {
            case None -> {
                if (!mc.player.getMainHandItem().is(Items.GLOWSTONE))
                    return false;
            }
            case Swap -> {
                int slot = HotbarUse.find(s -> s.is(Items.GLOWSTONE));
                if (slot == -1)
                    return false;
                if (glowstoneSwapBack.getValue() && slot != selected())
                    glowRestore = selected();
                mc.player.getInventory().setSelectedSlot(slot);
            }
            case Silent -> {
                int slot = HotbarUse.find(s -> s.is(Items.GLOWSTONE));
                if (slot == -1)
                    return false;
                silentRestore = selected();
                mc.player.getInventory().setSelectedSlot(slot);
            }
        }
        interact(hit);
        if (silentRestore != -1)
            mc.player.getInventory().setSelectedSlot(silentRestore);
        return true;
    }

    /** Explode: clicks a charged anchor while holding the Explode Slot. */
    private void doExplode() {
        if (explodeRestore != -1) {
            mc.player.getInventory().setSelectedSlot(explodeRestore);
            explodeRestore = -1;
            return;
        }
        BlockHitResult hit = crosshairBlock();
        if (hit == null || mc.player.isShiftKeyDown())
            return;
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (!state.is(Blocks.RESPAWN_ANCHOR) || charges(state) == 0)
            return;
        if (onlyOwn.getValue() && !ownAnchors.contains(pos))
            return;

        int slot = (int) explodeSlot.getValue().getInput() - 1;
        int silentRestore = -1;
        switch (explodeSwap.getValue()) {
            case None -> {
                if (selected() != slot)
                    return;
            }
            case Swap -> {
                if (explodeSwapBack.getValue() && slot != selected())
                    explodeRestore = selected();
                mc.player.getInventory().setSelectedSlot(slot);
            }
            case Silent -> {
                silentRestore = selected();
                mc.player.getInventory().setSelectedSlot(slot);
            }
        }
        interact(hit);
        if (silentRestore != -1)
            mc.player.getInventory().setSelectedSlot(silentRestore);
    }

    private void interact(BlockHitResult hit) {
        InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        if (result.consumesAction())
            PlayerUtils.swingItem();
    }

    private static BlockHitResult crosshairBlock() {
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK)
            return bhr;
        return null;
    }

    /** A block an anchor can be put on or into: something solid, or fire, short grass or a single snow layer. */
    private static boolean canPlaceAgainst(BlockState state) {
        return !state.canBeReplaced()
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SHORT_GRASS)
                || (state.is(Blocks.SNOW) && state.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS) == 1);
    }

    private static int charges(BlockState state) {
        return state.getValue(RespawnAnchorBlock.CHARGE);
    }

    private static int selected() {
        return mc.player.getInventory().getSelectedSlot();
    }

    /** The Place Bind mouse button, read straight from SDL so it works whatever vanilla binds it to. */
    private boolean bindDown() {
        int mask = org.lwjgl.sdl.SDLMouse.SDL_GetMouseState(null, null);
        return switch (placeBind.getValue()) {
            case Middle -> (mask & 2) != 0;
            case Mouse4 -> (mask & 8) != 0;
            case Mouse5 -> (mask & 16) != 0;
            case None -> false;
        };
    }
}
