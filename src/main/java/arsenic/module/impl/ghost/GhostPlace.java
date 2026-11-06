package arsenic.module.impl.ghost;

import arsenic.injection.accessor.IMixinMinecraft;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Right clicking while your crosshair is on an enemy places your block (cobweb by default) on the block behind them
 * instead of interacting with the player, so you can web someone you are looking straight at.
 */
@ModuleInfo(name = "GhostPlace", description = "Places blocks behind enemies instead of interacting with them", category = ModuleCategory.COMBAT, tier = ModuleTier.LEGIT)
public class GhostPlace extends Module {

    /** Vanilla's delay between right clicks. */
    private static final int USE_DELAY = 4;

    public final BooleanProperty cobwebOnly = new BooleanProperty("Cobweb Only", true);
    public final BooleanProperty useOffhand = new BooleanProperty("Use Offhand", false);

    /**
     * Called from the head of vanilla's right click. When the crosshair is on an enemy and a placeable is held, the
     * block behind them is right clicked instead and true is returned so vanilla's entity interaction is skipped.
     * Runs at the vanilla click timing, so the placement packet goes out exactly where a normal right click would.
     */
    public boolean handleUse() {
        if (mc.player == null || mc.level == null || mc.gameMode == null)
            return false;
        if (mc.gui.screen() != null || mc.player.isUsingItem() || mc.player.isHandsBusy() || mc.gameMode.isDestroying())
            return false;
        if (!isEnemy(mc.hitResult))
            return false;
        InteractionHand hand = placeHand();
        if (hand == null)
            return false;
        BlockHitResult hit = blockBehind();
        if (hit == null || hit.getType() != HitResult.Type.BLOCK)
            return false;

        // same as vanilla's block branch of a right click, only aimed at the block behind the target
        ((IMixinMinecraft) mc).setRightClickDelay(USE_DELAY);
        ItemStack stack = mc.player.getItemInHand(hand);
        int countBefore = stack.getCount();
        InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, hit);
        if (result instanceof InteractionResult.Success success
                && success.swingSource() == InteractionResult.SwingSource.PREDICTED) {
            mc.player.swing(hand, stack.getInteractAnimation(), false);
            if (!stack.isEmpty() && (stack.getCount() != countBefore || mc.player.hasInfiniteMaterials()))
                mc.player.itemUsed(hand);
        }
        return true;
    }

    private InteractionHand placeHand() {
        if (placeable(mc.player.getMainHandItem()))
            return InteractionHand.MAIN_HAND;
        return useOffhand.getValue() && placeable(mc.player.getOffhandItem()) ? InteractionHand.OFF_HAND : null;
    }

    private boolean placeable(ItemStack stack) {
        return cobwebOnly.getValue() ? stack.is(Items.COBWEB) : stack.getItem() instanceof BlockItem;
    }

    private static boolean isEnemy(HitResult hitResult) {
        if (!(hitResult instanceof EntityHitResult entityHit) || !(entityHit.getEntity() instanceof Player target))
            return false;
        return target != mc.player && target.isAlive() && !AntiBot.isBot(target)
                && !Arsenic.getArsenic().getFriendManager().isFriend(target);
    }

    /** Raycasts blocks only, ignoring the player in front, along the rotation the server sees. */
    private BlockHitResult blockBehind() {
        SilentRotationManager rotations = Arsenic.getArsenic().getSilentRotationManager();
        Vec3 eyes = mc.player.getEyePosition(1f);
        Vec3 look = rotations.isModified()
                ? Vec3.directionFromRotation(rotations.pitch, rotations.yaw)
                : mc.player.getViewVector(1f);
        Vec3 end = eyes.add(look.scale(mc.player.blockInteractionRange()));
        return mc.level.clip(new ClipContext(eyes, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
    }
}
