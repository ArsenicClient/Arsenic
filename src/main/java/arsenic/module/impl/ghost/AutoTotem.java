package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventTick;
import arsenic.injection.accessor.IMixinAbstractContainerScreen_AutoTotem;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.property.ShowWhen;
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
import arsenic.utils.timer.MSTimer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.damagesource.DamageEffects;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;

import java.util.ArrayDeque;
import java.util.Random;

/**
 * Keeps a totem of undying in the offhand. Always swaps one in whenever the offhand is empty; Crystal runs an
 * inventory routine after a pop (open, swap, refill a hotbar totem, close) with human-like delays; SMP swaps a totem
 * in at low health with the offhand key and puts the old item back once you are safe.
 */
@ModuleInfo(name = "AutoTotem", description = "Keeps a totem in your offhand", category = ModuleCategory.COMBAT, tier = ModuleTier.LEGIT)
public class AutoTotem extends Module {

    /** The swap button that targets the offhand. */
    private static final int OFFHAND_BUTTON = 40;
    /** The offhand slot in the player's inventory menu. */
    private static final int OFFHAND_MENU_SLOT = 45;
    /** The entity event a totem pop sends. */
    private static final byte TOTEM_POP = 35;

    public enum Mode {
        /** Swaps a totem into the offhand whenever it has none, without opening the inventory. */
        Always,
        /** After each pop, opens the inventory, swaps a totem in, refills a hotbar totem and closes it again. */
        Crystal,
        /** Swaps a totem in with the offhand key at low health and swaps the old item back once you recover. */
        SMP
    }

    public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.Always);

    @ShowWhen(property = "Mode", is = "Always")
    public final DoubleProperty delay = new DoubleProperty("Delay (ticks)", new DoubleValue(0, 10, 2, 1));

    // ---- Crystal and SMP ----
    @ShowWhen(property = "Mode", not = "Always")
    public final RangeProperty actionDelay = new RangeProperty("Action Delay (ms)", new RangeValue(0, 500, 30, 50, 1));

    // ---- Crystal ----
    @ShowWhen(property = "Mode", is = "Crystal")
    public final BooleanProperty dynamicDelay = new BooleanProperty("Dynamic Delay", false);
    @ShowWhen(property = "Dynamic Delay", is = "true")
    public final BooleanProperty measureHealth = new BooleanProperty("Measure Health", true);
    @ShowWhen(property = "Dynamic Delay", is = "true")
    public final BooleanProperty measureDistance = new BooleanProperty("Measure Distance", true);
    @ShowWhen(property = "Dynamic Delay", is = "true")
    public final BooleanProperty measureYLevel = new BooleanProperty("Measure Y Level", true);
    @ShowWhen(property = "Dynamic Delay", is = "true")
    public final BooleanProperty measureThreats = new BooleanProperty("Measure Nearby Threats", true);
    @ShowWhen(property = "Dynamic Delay", is = "true")
    public final BooleanProperty measureDamageRate = new BooleanProperty("Measure Damage Rate", true);

    @ShowWhen(property = "Mode", is = "Crystal")
    public final BooleanProperty doubleHand = new BooleanProperty("Double Hand", true);
    @ShowWhen(property = "Mode", is = "Crystal")
    public final BooleanProperty openInventory = new BooleanProperty("Open Inventory", true);
    @ShowWhen(property = "Mode", is = "Crystal")
    public final BooleanProperty refillHotbar = new BooleanProperty("Refill Hotbar Totem", true);
    @ShowWhen(property = "Mode", is = "Crystal")
    public final BooleanProperty switchTotem = new BooleanProperty("Switch", true);
    @ShowWhen(property = "Mode", is = "Crystal")
    public final BooleanProperty closeInventory = new BooleanProperty("Close Inventory", true);
    @ShowWhen(property = "Mode", is = "Crystal")
    public final DoubleProperty totemSlot = new DoubleProperty("Totem Slot", new DoubleValue(1, 9, 8, 1));
    @ShowWhen(property = "Mode", is = "Crystal")
    @ShowWhen(property = "Switch", is = "true")
    public final BooleanProperty legit = new BooleanProperty("Legit", false);
    @ShowWhen(property = "Legit", is = "true")
    public final RangeProperty hoverDelay = new RangeProperty("Hover Delay (ms)", new RangeValue(0, 300, 30, 50, 1));

    @ShowWhen(property = "Mode", is = "Crystal")
    public final BooleanProperty doubleHandOnGui = new BooleanProperty("Double Hand On GUI", false);
    @ShowWhen(property = "Mode", is = "Crystal")
    public final BooleanProperty bothPopsOnly = new BooleanProperty("Only Inventory On Both Pops", false);
    @ShowWhen(property = "Mode", is = "Crystal")
    public final BooleanProperty antiTotemGuard = new BooleanProperty("Anti Totem Guard", true);
    @ShowWhen(property = "Mode", is = "Crystal")
    public final BooleanProperty pauseInteractions = new BooleanProperty("Pause Interactions", true);

    // ---- SMP ----
    @ShowWhen(property = "Mode", is = "SMP")
    public final DoubleProperty health = new DoubleProperty("Health", new DoubleValue(1, 20, 5, 0.5));
    @ShowWhen(property = "Mode", is = "SMP")
    public final BooleanProperty waitWhenShielding = new BooleanProperty("Wait When Shielding", true);
    @ShowWhen(property = "Mode", is = "SMP")
    public final BooleanProperty antiMace = new BooleanProperty("Anti Mace", true);

    private final Random random = new Random();
    private final MSTimer actionTimer = new MSTimer();
    private final MSTimer hoverTimer = new MSTimer();
    private final MSTimer popTimer = new MSTimer();
    private final int[] candidates = new int[36];
    /** Recent damage taken, as {time, amount} pairs, for Dynamic Delay's damage rate. */
    private final ArrayDeque<long[]> damageLog = new ArrayDeque<>();

    // Always
    private int wait;

    // Crystal
    private volatile boolean popPending;
    private volatile long guardUntil;
    private volatile boolean resetPending;
    private boolean popped;
    private InventoryScreen ourScreen;
    private long rolledDelay, rolledHover;
    private float lastHealth = Float.NaN;

    // SMP
    /** Where the item that was in the offhand went (inventory index, 0-8 hotbar), or -1 when nothing is swapped. */
    private int swappedFrom = -1;
    /** The hotbar slot to go back to after an offhand-key swap, or -1. */
    private int restoreSlot = -1;
    /** The hotbar slot an offhand-key swap is waiting for the server to have selected, or -1. */
    private int pendingKeySlot = -1;
    private boolean pendingKeyRestoring;
    private double lastEnemyY = Double.NaN;

    @Override
    protected void onEnable() {
        reset();
    }

    @Override
    protected void onDisable() {
        closeOurScreen();
        if (restoreSlot != -1 && mc.player != null)
            mc.player.getInventory().setSelectedSlot(restoreSlot);
        reset();
    }

    private void reset() {
        popped = false;
        popPending = false;
        resetPending = false;
        guardUntil = 0;
        ourScreen = null;
        wait = 0;
        swappedFrom = -1;
        restoreSlot = -1;
        pendingKeySlot = -1;
        pendingKeyRestoring = false;
        lastEnemyY = Double.NaN;
        lastHealth = Float.NaN;
        damageLog.clear();
        rolledDelay = 0;
        rolledHover = (long) hoverDelay.getValue().getRandomInRange();
        actionTimer.lastMS = 0;
        hoverTimer.reset();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (resetPending)
            reset();
        switch (mode.getValue()) {
            case Always -> tickAlways();
            case Crystal -> tickCrystal();
            case SMP -> tickSmp();
        }
    };

    /** Totem pops, respawns and TotemGuard-style damage checks. Runs on the network thread, so it only sets flags. */
    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onIncoming = event -> {
        Packet<?> packet = event.getPacket();
        if (packet instanceof ClientboundRespawnPacket) {
            resetPending = true;
            return;
        }
        if (mc.player == null || mc.level == null)
            return;
        if (packet instanceof ClientboundBundlePacket bundle) {
            for (Packet<?> sub : bundle.subPackets())
                handleIncoming(sub);
        } else {
            handleIncoming(packet);
        }
    };

    /** Crystal's Pause Interactions: no block or item use goes out while the routine has the inventory open. */
    @EventLink
    public final Listener<EventPacket.OutGoing> onOutgoing = event -> {
        if (mode.getValue() != Mode.Crystal || !pauseInteractions.getValue())
            return;
        if (!(event.getPacket() instanceof ServerboundUseItemOnPacket) && !(event.getPacket() instanceof ServerboundUseItemPacket))
            return;
        if (ourScreen != null && mc.gui.screen() != ourScreen)
            ourScreen = null;
        if (ourScreen != null)
            event.cancel();
    };

    private void handleIncoming(Packet<?> packet) {
        if (packet instanceof ClientboundDamageEventPacket damage && mode.getValue() == Mode.Crystal
                && antiTotemGuard.getValue() && damage.entityId() == mc.player.getId() && isManualCheck(damage)) {
            guardUntil = System.currentTimeMillis() + 500L;
            NotificationManager.show(new Notification(NotificationType.WARNING, getName(),
                    "Detected a manual totem check, ignoring the pop", 2));
        } else if (packet instanceof ClientboundEntityEventPacket status && status.getEventId() == TOTEM_POP) {
            Entity entity = status.getEntity(mc.level);
            if (entity != mc.player)
                return;
            long guard = guardUntil;
            guardUntil = 0;
            if (antiTotemGuard.getValue() && guard != 0 && System.currentTimeMillis() <= guard)
                return;
            popPending = true;
        }
    }

    /** A plain generic hit with no attacker, the way anticheats deal damage to see whether a totem comes back. */
    private static boolean isManualCheck(ClientboundDamageEventPacket damage) {
        Holder<DamageType> holder = damage.sourceType();
        DamageType type = holder.value();
        return "generic".equals(type.msgId()) && type.exhaustion() == 0f && type.effects() == DamageEffects.HURT
                && damage.sourceCauseId() == -1 && holder.kind() == Holder.Kind.REFERENCE;
    }

    // ------------------------------------------------------------------ Always

    private void tickAlways() {
        if (mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            wait = (int) delay.getValue().getInput();
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        // any other screen's container has its own slot numbers
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof InventoryScreen))
            return;
        int slot = HotbarUse.findMenuSlot(s -> s.is(Items.TOTEM_OF_UNDYING));
        if (slot == -1)
            return;
        swap(slot, OFFHAND_BUTTON);
        wait = (int) delay.getValue().getInput();
    }

    // ------------------------------------------------------------------ Crystal

    private void tickCrystal() {
        trackDamage();
        if (popPending) {
            popPending = false;
            popped = true;
            popTimer.reset();
            nextDelay();
        }
        if (ourScreen != null && mc.gui.screen() != ourScreen)
            ourScreen = null;

        int hotbarTotem = HotbarUse.find(s -> s.is(Items.TOTEM_OF_UNDYING));
        if (doubleHandOnGui.getValue() && mc.gui.screen() != null && hotbarTotem != -1)
            mc.player.getInventory().setSelectedSlot(hotbarTotem);

        if (!popped || !mc.isWindowActive() || !delayPassed())
            return;
        if (mc.player.isBlocking())
            return;

        if (hotbarTotem != -1 && mc.gui.screen() == null && doubleHand.getValue()
                && !mc.player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
            mc.player.getInventory().setSelectedSlot(hotbarTotem);
            nextDelay();
            return;
        }

        if (!hasTotem(0, 36)) {
            if (mc.gui.screen() instanceof InventoryScreen && closeInventory.getValue())
                closeScreen();
            popped = false;
            return;
        }

        boolean offhandOk = mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
        boolean needRefill = hotbarTotem == -1 && refillHotbar.getValue();
        boolean shouldOpen = mc.gui.screen() == null && (needRefill || !offhandOk);
        if (bothPopsOnly.getValue()) {
            shouldOpen = mc.gui.screen() == null && needRefill && !offhandOk;
            // only one of the two is missing: leave it to Double Hand rather than opening the inventory
            if (!shouldOpen && mc.gui.screen() == null && needRefill != !offhandOk) {
                nextDelay();
                return;
            }
        }

        if (shouldOpen) {
            hoverTimer.reset();
            if (!openInventory.getValue())
                return;
            InventoryScreen screen = new InventoryScreen(mc.player);
            mc.gui.setScreen(screen);
            if (mc.gui.screen() == screen)
                ourScreen = screen;
            nextDelay();
            return;
        }

        if (mc.gui.screen() instanceof InventoryScreen screen) {
            int hotbarTarget = (int) totemSlot.getValue().getInput() - 1;
            if (!offhandOk && switchTotem.getValue()) {
                if (!legit.getValue()) {
                    int slot = randomTotem(9, 36);
                    if (slot == -1)
                        slot = randomTotem(0, 9);
                    if (slot != -1) {
                        swap(slot < 9 ? 36 + slot : slot, OFFHAND_BUTTON);
                        nextDelay();
                        return;
                    }
                } else {
                    int hovered = hoveredTotem(screen, -1);
                    if (hovered == -1 || !hoverPassed())
                        return;
                    swap(hovered, OFFHAND_BUTTON);
                    nextDelay();
                    return;
                }
            }
            if (needRefill && switchTotem.getValue() && hasTotem(9, 36)) {
                if (!legit.getValue()) {
                    swap(randomTotem(9, 36), hotbarTarget);
                    nextDelay();
                    return;
                }
                int hovered = hoveredTotem(screen, 36 + hotbarTarget);
                if (hovered == -1 || !hoverPassed())
                    return;
                swap(hovered, hotbarTarget);
                nextDelay();
                return;
            }
            if (!closeInventory.getValue() || needRefill || !offhandOk)
                return;
            closeScreen();
            nextDelay();
            return;
        }

        // nothing left to do for a while: this pop is handled
        if (popTimer.hasTimeElapsed(150L) && actionTimer.hasTimeElapsed(150L))
            popped = false;
    }

    /** The menu slot under the mouse if it holds a totem (not the offhand or the given slot), else -1. */
    private int hoveredTotem(InventoryScreen screen, int exclude) {
        Slot slot = ((IMixinAbstractContainerScreen_AutoTotem) screen).arsenic$getHoveredSlotAutoTotem();
        if (slot == null || slot.index == OFFHAND_MENU_SLOT || slot.index == exclude || slot.index < 9
                || !slot.getItem().is(Items.TOTEM_OF_UNDYING)) {
            hoverTimer.reset();
            rolledHover = (long) hoverDelay.getValue().getRandomInRange();
            return -1;
        }
        return slot.index;
    }

    private boolean hoverPassed() {
        return hoverTimer.hasTimeElapsed(rolledHover);
    }

    /** A random inventory index in [from, to) holding a totem, or -1. */
    private int randomTotem(int from, int to) {
        int n = 0;
        for (int i = from; i < to; i++)
            if (mc.player.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING))
                candidates[n++] = i;
        return n == 0 ? -1 : candidates[random.nextInt(n)];
    }

    private boolean hasTotem(int from, int to) {
        for (int i = from; i < to; i++)
            if (mc.player.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING))
                return true;
        return false;
    }

    private void closeScreen() {
        mc.player.closeContainer();
        ourScreen = null;
    }

    private void closeOurScreen() {
        if (ourScreen != null && mc.player != null && mc.gui.screen() == ourScreen)
            mc.player.closeContainer();
        ourScreen = null;
    }

    // ------------------------------------------------------------------ SMP

    private void tickSmp() {
        // an offhand-key swap waits a tick so the server has the new hotbar slot before the key packet
        if (pendingKeySlot != -1) {
            if (mc.player.getInventory().getSelectedSlot() == pendingKeySlot)
                pressOffhandKey();
            pendingKeySlot = -1;
            return;
        }
        if (restoreSlot != -1) {
            mc.player.getInventory().setSelectedSlot(restoreSlot);
            restoreSlot = -1;
            return;
        }
        if (!mc.isWindowActive())
            return;

        boolean maceDanger = antiMace.getValue() && !mc.player.isBlocking() && maceDive();
        if (!delayPassed() && !maceDanger)
            return;
        if (waitWhenShielding.getValue() && mc.player.isBlocking())
            return;

        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (hp > health.getValue().getInput() && !maceDanger) {
            // safe again: put the old offhand item back where it was
            if (swappedFrom != -1 && !mc.player.isUsingItem()) {
                if (swappedFrom < 9)
                    keySwap(swappedFrom);
                else
                    swap(swappedFrom, OFFHAND_BUTTON);
                swappedFrom = -1;
            }
            nextDelay();
            return;
        }
        if (!mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING) && swappedFrom == -1) {
            int slot = HotbarUse.find(s -> s.is(Items.TOTEM_OF_UNDYING));
            if (slot == -1)
                slot = randomTotem(9, 36);
            if (slot != -1) {
                if (slot < 9)
                    keySwap(slot);
                else
                    swap(slot, OFFHAND_BUTTON);
                swappedFrom = slot;
            }
        }
        nextDelay();
    }

    /** Swaps a hotbar slot with the offhand the way the offhand key does, selecting the slot first if needed. */
    private void keySwap(int hotbarSlot) {
        int selected = mc.player.getInventory().getSelectedSlot();
        if (selected == hotbarSlot) {
            pressOffhandKey();
            return;
        }
        restoreSlot = selected;
        mc.player.getInventory().setSelectedSlot(hotbarSlot);
        pendingKeySlot = hotbarSlot;
    }

    private void pressOffhandKey() {
        mc.player.connection.send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
    }

    /** The nearest enemy rising above us fast and close by, about to come down with a mace. */
    private boolean maceDive() {
        Player enemy = nearestEnemy(20);
        if (enemy == null) {
            lastEnemyY = Double.NaN;
            return false;
        }
        boolean diving = !Double.isNaN(lastEnemyY) && enemy.getY() - lastEnemyY >= 0.4
                && mc.player.getY() < enemy.getY()
                && Math.hypot(enemy.getX() - mc.player.getX(), enemy.getZ() - mc.player.getZ()) < 8;
        lastEnemyY = enemy.getY();
        if (diving)
            NotificationManager.show(new Notification(NotificationType.WARNING, getName(),
                    "Mace dive detected, switching to a totem", 2));
        return diving;
    }

    // ------------------------------------------------------------------ delays

    private boolean delayPassed() {
        return actionTimer.hasTimeElapsed(rolledDelay);
    }

    /** Starts the wait before the next action; Dynamic Delay shortens it the more danger you are in. */
    private void nextDelay() {
        double min = actionDelay.getValue().getMin();
        double rolled = actionDelay.getValue().getRandomInRange();
        if (dynamicDelay.getValue() && mode.getValue() == Mode.Crystal) {
            double danger = danger();
            rolled = Math.max(min, rolled - (rolled - min) * danger * danger);
        }
        rolledDelay = (long) rolled;
        actionTimer.reset();
    }

    /** How much danger you are in, 0 to 1, averaged over the enabled measures. */
    private double danger() {
        Player enemy = nearestEnemy(20);
        if (enemy == null)
            return 0;
        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        double dist = mc.player.distanceTo(enemy);
        double sum = 0;
        int count = 0;
        if (measureHealth.getValue()) {
            sum += clamp((20f - hp) / 18f);
            count++;
        }
        if (measureDistance.getValue()) {
            sum += clamp((10 - dist) / 6);
            count++;
        }
        if (measureYLevel.getValue()) {
            sum += clamp((mc.player.getY() - enemy.getY()) / 100);
            count++;
        }
        if (measureThreats.getValue()) {
            double closest = Double.MAX_VALUE;
            for (EndCrystal crystal : mc.level.getEntitiesOfClass(EndCrystal.class, mc.player.getBoundingBox().inflate(12)))
                closest = Math.min(closest, mc.player.distanceTo(crystal));
            sum += closest <= 6 ? 1 : closest <= 12 ? 0.6 : 0;
            count++;
        }
        if (measureDamageRate.getValue()) {
            long now = System.currentTimeMillis();
            damageLog.removeIf(e -> now - e[0] > 5000L);
            float total = 0;
            for (long[] e : damageLog)
                total += Float.intBitsToFloat((int) e[1]);
            sum += Math.min(1f, total / 20f);
            count++;
        }
        return count == 0 ? 0 : sum / count;
    }

    private void trackDamage() {
        if (!dynamicDelay.getValue())
            return;
        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (!Float.isNaN(lastHealth) && lastHealth > hp)
            damageLog.addLast(new long[]{System.currentTimeMillis(), Float.floatToIntBits(lastHealth - hp)});
        lastHealth = hp;
        while (damageLog.size() > 64)
            damageLog.pollFirst();
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    // ------------------------------------------------------------------ helpers

    private void swap(int menuSlot, int button) {
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, menuSlot, button, ContainerInput.SWAP, mc.player);
    }

    private Player nearestEnemy(double range) {
        Player best = null;
        double bestDist = range;
        for (Player p : mc.level.players()) {
            if (p == mc.player || !p.isAlive() || AntiBot.isBot(p)
                    || Arsenic.getArsenic().getFriendManager().isFriend(p))
                continue;
            double d = mc.player.distanceTo(p);
            if (d <= bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }
}
