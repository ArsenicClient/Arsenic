package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.ShowWhen;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.HotbarUse;
import arsenic.utils.minecraft.ItemUtils;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * When you click a player without a sword in hand, it holds the click, switches to a hotbar sword, hits, then switches
 * back to what you had. A player shielding towards you gets an axe instead (or no swap at all), so the hit is not wasted.
 */
@ModuleInfo(name = "SwapHit", description = "Swaps to a sword to hit, then swaps back", category = ModuleCategory.COMBAT, tier = ModuleTier.LEGIT)
public class SwapHit extends Module {

    public final BooleanProperty totemOnly = new BooleanProperty("Totem Only", false);
    public final DoubleProperty swapDelay = new DoubleProperty("Swap Delay (ms)", new DoubleValue(0, 300, 50, 1));
    public final DoubleProperty swapBackDelay = new DoubleProperty("Swap Back Delay (ms)", new DoubleValue(0, 300, 50, 1));
    public final BooleanProperty shieldAware = new BooleanProperty("Shield Aware", true);
    @ShowWhen(property = "Shield Aware", is = "true")
    public final BooleanProperty preferAxe = new BooleanProperty("Prefer Axe", true);

    private enum Stage { IDLE, HIT, SWAP_BACK }

    private final HotbarUse hotbar = new HotbarUse();
    private final MSTimer timer = new MSTimer();
    private Stage stage = Stage.IDLE;
    /** Player tick count when the slot changed: the hit waits for a later tick so the switch and attack never share one. */
    private int swapTick;

    @Override
    public boolean isSwappingHotbar() {
        return stage != Stage.IDLE;
    }

    @Override
    protected void onEnable() {
        stage = Stage.IDLE;
    }

    @Override
    protected void onDisable() {
        if (mc.player != null)
            hotbar.release();
        stage = Stage.IDLE;
    }

    /**
     * Called from the head of Minecraft.startAttack. Returns true when the vanilla attack should be dropped because
     * this module is taking it over (or is mid-swap and would otherwise double hit).
     */
    public boolean interceptAttack() {
        if (mc.player == null || mc.level == null || mc.gameMode == null)
            return false;
        if (stage == Stage.HIT)
            return true;
        if (stage != Stage.IDLE || mc.gui.screen() != null || !mc.isWindowActive() || mc.player.isUsingItem())
            return false;
        if (ItemUtils.isSword(mc.player.getMainHandItem()))
            return false;
        if (totemOnly.getValue() && !mc.player.getMainHandItem().is(Items.TOTEM_OF_UNDYING))
            return false;
        if (!(mc.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof Player target)
                || !TargetManager.isValidTarget(target))
            return false;

        int slot;
        if (shieldAware.getValue() && shieldingAtUs(target)) {
            if (!preferAxe.getValue() || ItemUtils.isAxe(mc.player.getMainHandItem()))
                return false;
            slot = HotbarUse.find(ItemUtils::isAxe);
        } else {
            slot = HotbarUse.find(ItemUtils::isSword);
        }
        if (slot == -1)
            return false;

        hotbar.select(slot);
        swapTick = mc.player.tickCount;
        timer.reset();
        stage = Stage.HIT;
        return true;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (stage == Stage.IDLE)
            return;
        if (!mc.player.isAlive()) {
            hotbar.release();
            stage = Stage.IDLE;
            return;
        }
        if (stage == Stage.HIT) {
            if (mc.player.tickCount == swapTick || !timer.hasTimeElapsed((long) swapDelay.getValue().getInput()))
                return;
            if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() != null) {
                // attack first, then the swing (1.9+ order)
                mc.gameMode.attack(mc.player, hit.getEntity());
                PlayerUtils.swingItem();
            }
            timer.reset();
            stage = Stage.SWAP_BACK;
        } else if (timer.hasTimeElapsed((long) swapBackDelay.getValue().getInput())) {
            hotbar.release();
            stage = Stage.IDLE;
        }
    };

    /** A raised shield that faces us blocks the hit, so a sword swing there does nothing. */
    private boolean shieldingAtUs(Player target) {
        if (!target.isUsingItem() || !target.getUseItem().is(Items.SHIELD))
            return false;
        Vec3 toUs = mc.player.position().subtract(target.position());
        Vec3 look = target.getViewVector(1f);
        return toUs.x * look.x + toUs.z * look.z > 0;
    }
}
