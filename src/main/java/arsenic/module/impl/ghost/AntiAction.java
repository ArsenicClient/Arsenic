package arsenic.module.impl.ghost;

import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.minecraft.ItemUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Swallows clicks that would throw a fight: a second glowstone into an anchor, opening an ender chest with combat
 * gear in hand, placing glowstone on the ground, firing a rocket while not flying, and swinging a sword into a raised
 * shield. The checks run from {@link arsenic.injection.mixin.MixinMinecraft_AntiAction} before the click happens.
 */
@ModuleInfo(name = "AntiAction", description = "Blocks misclicks that would interrupt your PvP", category = ModuleCategory.COMBAT, tier = ModuleTier.QOL)
public class AntiAction extends Module {

    public final BooleanProperty doubleGlowstone = new BooleanProperty("Double Glowstone", true);
    public final BooleanProperty openEChest = new BooleanProperty("Open E-Chest", true);
    public final BooleanProperty placeGlowstone = new BooleanProperty("Place Glowstone", true);
    public final BooleanProperty groundFirework = new BooleanProperty("Ground Firework", true);
    public final BooleanProperty swordShield = new BooleanProperty("Sword Shield", true);

    /** True if the right click about to happen should be dropped. */
    public boolean shouldBlockUse() {
        if (mc.player == null || mc.level == null)
            return false;
        ItemStack main = mc.player.getMainHandItem();

        if (groundFirework.getValue() && main.is(Items.FIREWORK_ROCKET)
                && !mc.player.isFallFlying() && !mc.player.getOffhandItem().is(Items.CROSSBOW))
            return true;

        if (!(mc.hitResult instanceof BlockHitResult blockHit) || blockHit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK)
            return false;
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        boolean sneaking = mc.player.isShiftKeyDown();
        boolean holdingGlowstone = main.is(Items.GLOWSTONE);
        boolean anchor = state.is(Blocks.RESPAWN_ANCHOR);

        if (doubleGlowstone.getValue() && anchor && !sneaking && holdingGlowstone
                && state.getValue(RespawnAnchorBlock.CHARGE) != 0)
            return true;

        if (openEChest.getValue() && state.is(Blocks.ENDER_CHEST) && !sneaking
                && (isCombatItem(main) || isCombatItem(mc.player.getOffhandItem())))
            return true;

        return placeGlowstone.getValue() && holdingGlowstone && (!anchor || sneaking) && isSolid(state);
    }

    /** True if the left click about to happen should be dropped. */
    public boolean shouldBlockAttack() {
        if (mc.player == null || !swordShield.getValue())
            return false;
        return mc.hitResult instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof Player target
                && ItemUtils.isSword(mc.player.getMainHandItem())
                && target.isUsingItem()
                && target.getUseItem().is(Items.SHIELD);
    }

    private static boolean isCombatItem(ItemStack stack) {
        if (stack.isEmpty())
            return false;
        return ItemUtils.isSword(stack) || ItemUtils.isAxe(stack) || ItemUtils.isMace(stack)
                || stack.is(Items.END_CRYSTAL) || stack.is(Items.OBSIDIAN) || stack.is(Items.CRYING_OBSIDIAN)
                || stack.is(Items.TOTEM_OF_UNDYING) || stack.is(Items.EXPERIENCE_BOTTLE) || stack.is(Items.GLOWSTONE);
    }

    /** A block glowstone would be placed against rather than into. */
    private static boolean isSolid(BlockState state) {
        return !state.canBeReplaced()
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SHORT_GRASS)
                || (state.is(Blocks.SNOW) && state.getValue(BlockStateProperties.LAYERS) == 1);
    }
}
