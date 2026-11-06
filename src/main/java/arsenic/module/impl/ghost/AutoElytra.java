package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.io.Keys;
import arsenic.utils.keystrokes.SyntheticKeys;
import arsenic.utils.minecraft.HotbarUse;
import arsenic.utils.timer.MSTimer;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Puts an elytra on, takes off and fires a rocket, then puts the chestplate back after a short flight (or on
 * landing), all as one timed sequence. It turns itself off when it is done.
 */
@ModuleInfo(name = "AutoElytra", description = "Equips an elytra, takes off with a rocket and swaps the chestplate back", category = ModuleCategory.COMBAT, tier = ModuleTier.LEGIT)
public class AutoElytra extends Module {

    private static final int CHEST_SLOT = 6;

    private static final int OPEN = 0, EQUIP = 1, CLOSE = 2, TAKEOFF = 3, BOOST = 4, OPEN_ROCKET = 5,
            RESTORE_ROCKET = 6, CLOSE_ROCKET = 7, FLY = 8, OPEN_BACK = 9, SWAP_BACK = 10, FINISH = 11;

    public final BooleanProperty requireLand = new BooleanProperty("Require Land", true);
    public final RangeProperty delay = new RangeProperty("Delay (ms)", new RangeValue(0, 300, 50, 100, 5));
    public final BooleanProperty firework = new BooleanProperty("Firework", true);
    public final BooleanProperty autoDisable = new BooleanProperty("Auto Disable", true);
    public final DoubleProperty flyTime = new DoubleProperty("Fly Time (ms)", new DoubleValue(0, 1000, 300, 10));
    public final BooleanProperty silentInventory = new BooleanProperty("Silent Inventory", false);
    public final BooleanProperty autoJump = new BooleanProperty("Auto Jump", false);
    public final BooleanProperty fireworkOnSpace = new BooleanProperty("Firework On Space", false);
    public final RangeProperty spaceCooldown = new RangeProperty("Space Cooldown (ms)", new RangeValue(0, 3000, 400, 600, 10));

    private final HotbarUse swap = new HotbarUse();
    private final MSTimer stepTimer = new MSTimer();
    private final MSTimer flyTimer = new MSTimer();
    private final MSTimer spaceTimer = MSTimer.expired();

    private int step;
    private long stepDelay, spaceDelay;
    /** Inventory index (0-35) the elytra came from, where the chestplate now sits; -1 when nothing was swapped. */
    private int chestBack = -1;
    /** Inventory index of the rockets moved into the hand from the main inventory, or -1. */
    private int rocketFrom = -1;
    /** Hotbar slot the rockets were swapped into. */
    private int rocketTarget;
    private boolean silent, landed, openedScreen, jumpHeld, spaceWasDown;

    public AutoElytra() {
        flyTime.setVisible(autoDisable::getValue);
        spaceCooldown.setVisible(fireworkOnSpace::getValue);
    }

    @Override
    public boolean isSwappingHotbar() {
        return swap.active();
    }

    @Override
    public String getHudInfo() {
        return step >= FLY ? "Flying" : null;
    }

    @Override
    protected void onEnable() {
        step = OPEN;
        chestBack = -1;
        rocketFrom = -1;
        silent = false;
        landed = false;
        openedScreen = false;
        spaceWasDown = true;
        stepTimer.setTime(0);
        stepDelay = 0;
        flyTimer.reset();
    }

    @Override
    protected void onDisable() {
        swap.release();
        if (jumpHeld) {
            jumpHeld = false;
            Keys.setDown(mc.options.keyJump, Keys.isPhysicallyDown(mc.options.keyJump));
        }
        closeOurScreen();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        swap.tick();
        if (jumpHeld) {
            jumpHeld = false;
            Keys.setDown(mc.options.keyJump, Keys.isPhysicallyDown(mc.options.keyJump));
        }
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof InventoryScreen))
            return;

        spaceRocket();

        if (mc.player.onGround()) {
            landed = true;
            if (step == FLY && autoDisable.getValue())
                flyTimer.reset();
        }

        if (!stepTimer.hasTimeElapsed(stepDelay) || (!landed && requireLand.getValue()))
            return;

        boolean wearing = mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
        if (step <= EQUIP) {
            int elytra = wearing ? -1 : findInventory(Items.ELYTRA);
            int rocket = firework.getValue() ? findInventory(Items.FIREWORK_ROCKET) : 0;
            if ((!wearing && elytra == -1) || rocket == -1) {
                setEnabled(false);
                return;
            }
            silent = silentInventory.getValue() || (elytra < 9 && rocket < 9);
        }

        switch (step) {
            case OPEN, OPEN_ROCKET, OPEN_BACK -> {
                if (step == OPEN_ROCKET)
                    swap.release();
                openInventory();
                next();
            }
            case EQUIP -> {
                if (!inventoryReady()) {
                    step = OPEN;
                    return;
                }
                int elytra = wearing ? -1 : findInventory(Items.ELYTRA);
                if (firework.getValue()) {
                    int rocket = findInventory(Items.FIREWORK_ROCKET);
                    if (rocket >= 9) {
                        // into the held slot, or the next one when the elytra sits there
                        int held = mc.player.getInventory().getSelectedSlot();
                        rocketTarget = held == elytra ? (held + 1) % 9 : held;
                        click(rocket, rocketTarget, ContainerInput.SWAP);
                        rocketFrom = rocket;
                    }
                }
                if (elytra != -1) {
                    if (elytra < 9) {
                        // a right click with the elytra in hand puts it on and leaves the chestplate in that slot
                        swap.select(elytra);
                    } else {
                        swapChest(elytra);
                    }
                    chestBack = elytra;
                }
                next();
            }
            case CLOSE, CLOSE_ROCKET -> {
                closeOurScreen();
                next();
            }
            case TAKEOFF -> {
                if (mc.gui.screen() != null)
                    return;
                if (!mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
                    if (mc.player.getMainHandItem().is(Items.ELYTRA))
                        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                    rollDelay();
                    return;
                }
                if (mc.player.onGround()) {
                    if (autoJump.getValue() && !jumpHeld) {
                        mc.options.keyJump.setDown(true);
                        SyntheticKeys.press(SyntheticKeys.Key.JUMP);
                        jumpHeld = true;
                    }
                    return;
                }
                if (!mc.player.isFallFlying() && mc.player.tryToStartFallFlying())
                    mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
                if (mc.player.isFallFlying()) {
                    flyTimer.reset();
                    next();
                }
            }
            case BOOST -> {
                if (firework.getValue()) {
                    if (!mc.player.getMainHandItem().is(Items.FIREWORK_ROCKET)) {
                        int rocket = HotbarUse.find(s -> s.is(Items.FIREWORK_ROCKET));
                        if (rocket == -1) {
                            next();
                            return;
                        }
                        swap.select(rocket);
                        rollDelay();
                        return;
                    }
                    mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                }
                // nothing to put back without a rocket swap: go straight to the flight. The hotbar goes back on a
                // later tick, never in the tick of the use (PacketOrderE)
                step = rocketFrom == -1 ? FLY : OPEN_ROCKET;
                rollDelay();
            }
            case RESTORE_ROCKET -> {
                if (!inventoryReady()) {
                    step = OPEN_ROCKET;
                    return;
                }
                click(rocketFrom, rocketTarget, ContainerInput.SWAP);
                rocketFrom = -1;
                next();
            }
            case FLY -> {
                swap.release();
                if (autoDisable.getValue()) {
                    if (!flyTimer.hasTimeElapsed(flyTime.getValue().getInput()))
                        return;
                } else if (!(mc.player.onGround() || mc.player.isInWater()) || mc.player.isFallFlying()) {
                    return;
                }
                if (chestBack == -1) {
                    step = FINISH;
                    return;
                }
                if (chestBack < 9) {
                    // the chestplate went to the hotbar: right click it back on, no inventory needed
                    step = SWAP_BACK;
                    return;
                }
                silent = silentInventory.getValue();
                next();
            }
            case SWAP_BACK -> {
                if (chestBack < 9) {
                    if (mc.player.getInventory().getSelectedSlot() != chestBack) {
                        swap.select(chestBack);
                        rollDelay();
                        return;
                    }
                    if (mc.player.getMainHandItem().isEmpty())
                        swapChest(chestBack); // nothing was worn before: take the elytra off into the slot
                    else
                        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                } else {
                    if (!inventoryReady()) {
                        step = OPEN_BACK;
                        return;
                    }
                    swapChest(chestBack);
                }
                chestBack = -1;
                next();
            }
            case FINISH -> {
                swap.release();
                closeOurScreen();
                setEnabled(false);
            }
            default -> {
            }
        }
    };

    /** Fires a rocket on a fresh jump press while gliding, with its own random cooldown. */
    private void spaceRocket() {
        boolean down = Keys.isPhysicallyDown(mc.options.keyJump);
        boolean pressed = down && !spaceWasDown;
        spaceWasDown = down;
        if (!pressed || !fireworkOnSpace.getValue() || mc.gui.screen() != null || !mc.player.isFallFlying())
            return;
        if (step < BOOST || step > FLY || swap.active() || !spaceTimer.hasTimeElapsed(spaceDelay))
            return;
        if (mc.player.getMainHandItem().is(Items.FIREWORK_ROCKET)) {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        } else {
            int rocket = HotbarUse.find(s -> s.is(Items.FIREWORK_ROCKET));
            if (rocket == -1)
                return;
            swap.use(rocket);
        }
        spaceTimer.reset();
        spaceDelay = (long) spaceCooldown.getValue().getRandomInRange();
    }

    private void next() {
        step++;
        rollDelay();
    }

    private void rollDelay() {
        stepTimer.reset();
        stepDelay = (long) delay.getValue().getRandomInRange();
    }

    private void openInventory() {
        if (silent || mc.gui.screen() != null)
            return;
        mc.gui.setScreen(new InventoryScreen(mc.player));
        openedScreen = true;
    }

    private void closeOurScreen() {
        if (openedScreen && mc.player != null && mc.gui.screen() instanceof InventoryScreen)
            mc.player.closeContainer();
        openedScreen = false;
    }

    /** Clicks are made silently or with our own inventory screen showing; if the screen got closed, reopen it. */
    private boolean inventoryReady() {
        return silent || mc.gui.screen() instanceof InventoryScreen;
    }

    /** First inventory index (0-35, hotbar first) holding the item, or -1. */
    private static int findInventory(Item item) {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).is(item))
                return i;
        }
        return -1;
    }

    /** Inventory index to inventory menu slot: the hotbar sits at 36-44 in the menu. */
    private static int menuSlot(int index) {
        return index < 9 ? index + 36 : index;
    }

    private static void click(int index, int button, ContainerInput input) {
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, menuSlot(index), button, input, mc.player);
    }

    /** Swaps the chest slot with an inventory slot through the cursor. */
    private static void swapChest(int index) {
        int id = mc.player.inventoryMenu.containerId;
        int slot = menuSlot(index);
        mc.gameMode.handleContainerInput(id, slot, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(id, CHEST_SLOT, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(id, slot, 0, ContainerInput.PICKUP, mc.player);
    }
}
