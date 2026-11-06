package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.Priorities;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventTick;
import arsenic.injection.accessor.IMixinKeyMapping;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.ShowWhen;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.keystrokes.SyntheticKeys;
import arsenic.utils.minecraft.ItemUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

import java.util.Random;

/**
 * Clicks for you while it is on, no button held. Ghost mode left clicks at a random rate inside the CPS range and waits
 * a human reaction time before the first click on a new entity; Interval mode clicks either button at a fixed rate for
 * farms and AFK grinders.
 */
@ModuleInfo(name = "AutoClicker", description = "Clicks your mouse for you", category = ModuleCategory.PLAYER, tier = ModuleTier.LEGIT)
public class AutoClicker extends Module {

    public enum Mode { Ghost, Interval }

    public enum Button { Left, Right }

    public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.Ghost);
    @ShowWhen(property = "Mode", is = "Interval")
    public final EnumProperty<Button> button = new EnumProperty<>("Click", Button.Left);
    @ShowWhen(property = "Mode", is = "Interval")
    public final DoubleProperty interval = new DoubleProperty("Interval", new DoubleValue(0, 10000, 100, 10));
    @ShowWhen(property = "Mode", is = "Ghost")
    public final RangeProperty cps = new RangeProperty("CPS", new RangeValue(1, 20, 5, 15, 0.5));
    @ShowWhen(property = "Mode", is = "Ghost")
    public final BooleanProperty stopOnBlock = new BooleanProperty("Stop On Block", false);
    @ShowWhen(property = "Mode", is = "Ghost")
    public final BooleanProperty weaponOnly = new BooleanProperty("Weapon Only", false);
    @ShowWhen(property = "Mode", is = "Ghost")
    public final DoubleProperty reactionTime = new DoubleProperty("Reaction Time", new DoubleValue(0, 400, 170, 5));
    @ShowWhen(property = "Mode", is = "Ghost")
    public final DoubleProperty reactionJitter = new DoubleProperty("Reaction Jitter", new DoubleValue(0, 100, 25, 1));

    private final Random random = new Random();

    private long lastClick;
    private double nextDelayMs = 200;
    /** This session's personal mean reaction, rolled from the setting so it is not the exact same every time. */
    private double reactionMean;
    private double reactionSetting = -1;
    private int targetId = -1;
    private long targetSince;
    private double targetDelayMs;

    @Override
    public String getHudInfo() {
        return mode.getValue() == Mode.Interval
                ? (int) interval.getValue().getInput() + "ms"
                : cps.getValueString();
    }

    @Override
    protected void onEnable() {
        rollReactionMean();
        targetId = -1;
        targetDelayMs = 0;
        lastClick = System.currentTimeMillis();
        nextDelayMs = rollGhostDelay();
    }

    @Override
    protected void onDisable() {
        targetId = -1;
        targetDelayMs = 0;
    }

    /** Interval clicks run on the game tick like the farms they are for, so 0 ms means once per tick. */
    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mode.getValue() != Mode.Interval || !canClick())
            return;
        long now = System.currentTimeMillis();
        if (now - lastClick < interval.getValue().getInput())
            return;
        click(button.getValue() == Button.Left ? mc.options.keyAttack : mc.options.keyUse);
        lastClick = now;
    };

    /** Ghost clicks run every frame so the random gaps between them are not rounded to whole ticks. */
    @RequiresPlayer
    @EventLink(Priorities.VERY_LOW)
    public final Listener<EventRender2D> onFrame = event -> {
        if (mode.getValue() != Mode.Ghost || !canClick() || mc.player.isUsingItem())
            return;
        if (stopOnBlock.getValue() && mc.hitResult instanceof BlockHitResult)
            return;
        if (weaponOnly.getValue()) {
            ItemStack held = mc.player.getMainHandItem();
            if (!ItemUtils.isSword(held) && !ItemUtils.isAxe(held))
                return;
        }

        long now = System.currentTimeMillis();
        if (mc.hitResult instanceof EntityHitResult hit) {
            int id = hit.getEntity().getId();
            if (id != targetId) {
                targetId = id;
                targetSince = now;
                targetDelayMs = rollReaction();
            }
            if (now - targetSince < targetDelayMs)
                return;
        } else {
            targetId = -1;
        }

        if (now - lastClick < nextDelayMs)
            return;
        click(mc.options.keyAttack);
        lastClick = now;
        nextDelayMs = rollGhostDelay();
    };

    private boolean canClick() {
        return mc.gui.screen() == null && mc.isWindowActive();
    }

    /** A real press of the bound key, so vanilla's attack and use rules (miss delay, cooldown, item use) still apply. */
    private void click(KeyMapping key) {
        KeyMapping.click(((IMixinKeyMapping) key).getBoundKey());
        if (key == mc.options.keyAttack)
            SyntheticKeys.press(SyntheticKeys.Key.LMB);
    }

    private double rollGhostDelay() {
        return 1000.0 / Math.max(1.0, cps.getValue().getRandomInRange());
    }

    private void rollReactionMean() {
        double setting = reactionTime.getValue().getInput();
        reactionSetting = setting;
        reactionMean = setting <= 0 ? 0 : clamp(gaussian(setting, 15), setting - 50, setting + 50);
    }

    /** Delay before the first click on a newly aimed-at entity, 0 when Reaction Time is off. */
    private double rollReaction() {
        if (reactionTime.getValue().getInput() != reactionSetting)
            rollReactionMean();
        if (reactionMean <= 0)
            return 0;
        double jitter = reactionJitter.getValue().getInput();
        return clamp(gaussian(reactionMean, jitter > 0 ? jitter : 1), 80, 500);
    }

    private double gaussian(double mean, double spread) {
        return mean + spread * random.nextGaussian();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
