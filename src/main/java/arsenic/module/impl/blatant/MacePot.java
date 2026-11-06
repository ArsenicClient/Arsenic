package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.ContainerUtils;
import arsenic.utils.minecraft.HotbarUse;
import arsenic.utils.minecraft.ItemUtils;
import arsenic.utils.rotations.SilentRotationManager;
import arsenic.utils.timer.MSTimer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Throws a speed or strength splash potion at your feet whenever you stand on the ground without that effect. Pots in
 * the hotbar are thrown with a silent downward look; a pot in the main inventory is first swapped into your held slot,
 * and the item it replaced goes back once both effects are on.
 */
@ModuleInfo(name = "MacePot", description = "Throws speed and strength pots from your inventory when needed", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class MacePot extends Module {

    /** How long to wait before throwing the same effect again, so the splash has time to land. */
    private static final long EFFECT_RETRY_MS = 300;

    public final DoubleProperty delay = new DoubleProperty("Delay", new DoubleValue(50, 300, 300, 10));
    public final BooleanProperty autoDisable = new BooleanProperty("Auto Disable", false);
    public final DoubleProperty pitch = new DoubleProperty("Pitch", new DoubleValue(50, 90, 70, 1));

    private final HotbarUse swap = new HotbarUse();
    private final MSTimer actionTimer = MSTimer.expired();
    private final MSTimer speedTimer = MSTimer.expired();
    private final MSTimer strengthTimer = MSTimer.expired();

    /** Main inventory menu slot (9-35) a pot was pulled from, and the hotbar slot it went into; -1 when none. */
    private int pulledFrom = -1;
    private int pulledInto = -1;

    /** Hotbar slot to throw from once the silent look is down, or -1. */
    private int throwSlot = -1;
    private boolean throwSpeed;
    private float throwPitch;

    @Override
    public boolean isSwappingHotbar() {
        return swap.active() || pulledFrom != -1;
    }

    @Override
    protected void onDisable() {
        swap.release();
        if (pulledFrom != -1 && mc.player != null && mc.gameMode != null && !mc.player.isUsingItem())
            ContainerUtils.swap(pulledFrom, pulledInto);
        pulledFrom = -1;
        pulledInto = -1;
        throwSlot = -1;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        swap.tick();
        throwSlot = -1;
        if (mc.gui.screen() != null || mc.player.isUsingItem())
            return;
        if (!actionTimer.finished((long) delay.getValue().getInput()))
            return;

        boolean hasSpeed = mc.player.hasEffect(MobEffects.SPEED);
        boolean hasStrength = mc.player.hasEffect(MobEffects.STRENGTH);

        if (mc.player.onGround()) {
            if (!hasSpeed && speedTimer.finished(EFFECT_RETRY_MS) && prepare(MobEffects.SPEED, true))
                return;
            if (!hasStrength && strengthTimer.finished(EFFECT_RETRY_MS) && prepare(MobEffects.STRENGTH, false))
                return;
        }

        if (hasSpeed && hasStrength && pulledFrom != -1) {
            ContainerUtils.swap(pulledFrom, pulledInto);
            pulledFrom = -1;
            pulledInto = -1;
            if (autoDisable.getValue())
                setEnabled(false);
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (throwSlot == -1)
            return;
        event.setPitch(throwPitch);
        event.setSpeed(180f);
        event.setPreventDuplicateLook(true);
        event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        if (throwSlot == -1 || Math.abs(event.getPitch() - throwPitch) > 2f)
            return;
        ItemStack stack = mc.player.getInventory().getItem(throwSlot);
        if (!ItemUtils.isSplashPotion(stack)) {
            throwSlot = -1;
            return;
        }
        swap.use(throwSlot);
        actionTimer.reset();
        (throwSpeed ? speedTimer : strengthTimer).reset();
        throwSlot = -1;
    };

    /**
     * Queues a throw from the hotbar, or pulls a pot out of the main inventory into the held slot. True when it acted,
     * so nothing else happens this tick.
     */
    private boolean prepare(Holder<MobEffect> effect, boolean speed) {
        int hotbar = HotbarUse.find(s -> ItemUtils.isSplashPotion(s) && ItemUtils.hasEffect(s, effect));
        if (hotbar != -1) {
            throwSlot = hotbar;
            throwSpeed = speed;
            float base = (float) pitch.getValue().getInput();
            throwPitch = Math.min(90f, base - 1f + ThreadLocalRandom.current().nextFloat());
            return true;
        }
        int menuSlot = findInventoryPot(effect);
        if (menuSlot != -1) {
            int held = mc.player.getInventory().getSelectedSlot();
            if (pulledFrom == -1) {
                pulledFrom = menuSlot;
                pulledInto = held;
            }
            ContainerUtils.swap(menuSlot, held);
            actionTimer.reset();
            return true;
        }
        return false;
    }

    /** Main inventory menu slot (9-35) holding a splash pot with the effect, or -1. */
    private static int findInventoryPot(Holder<MobEffect> effect) {
        for (int i = 9; i <= 35; i++) {
            ItemStack stack = ContainerUtils.getItemStack(i);
            if (ItemUtils.isSplashPotion(stack) && ItemUtils.hasEffect(stack, effect))
                return i;
        }
        return -1;
    }
}
