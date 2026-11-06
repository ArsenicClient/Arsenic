package arsenic.module.impl.player;

import arsenic.module.ModuleTier;
import arsenic.gui.themes.ThemeManager;
import arsenic.module.property.ShowWhen;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.io.Keys;
import arsenic.utils.timer.MSTimer;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventDisplayGuiScreen;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.timer.Timer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;

/** Shift-clicks every item out of a chest you open, in a short human-looking path, then closes it. */
@ModuleInfo(name = "ChestStealer", description = "Steals items out of chests", category = ModuleCategory.PLAYER, tier = ModuleTier.LEGIT)
public class ChestStealer extends Module {

    public enum Bind {
        None, Middle, Mouse4, Mouse5, R, F, G, V, X, Z, C, Q, Tab, Left_Alt, Caps_Lock
    }

    @ShowWhen(property = "Randomize Delay", is = "false")
    public final DoubleProperty delay = new DoubleProperty("Delay (ms)", new DoubleValue(0, 500, 110, 10));
    /** Waits a random time in a range between pulls instead of a fixed delay. */
    public final BooleanProperty randomize = new BooleanProperty("Randomize Delay", false);
    @ShowWhen(property = "Randomize Delay", is = "true")
    public final RangeProperty delayRange = new RangeProperty("Delay Range (ms)", new RangeValue(0, 1000, 100, 200, 10));
    /** Closes the chest once it is empty. */
    public final BooleanProperty close = new BooleanProperty("Close", true);
    /** Also steals from chests you did not just click: barrels, chest minecarts, chests opened by plugins. */
    public final BooleanProperty anyContainer = new BooleanProperty("Any Container", false);
    /** Hold to steal; None steals automatically as soon as the chest opens. */
    public final EnumProperty<Bind> activateKey = new EnumProperty<>("Activate Key", Bind.None);


    private static final long CHEST_CLICK_WINDOW_MS = 3000;

    private boolean inChest;
    private final MSTimer chestClick = MSTimer.expired();
    private ArrayList<Slot> path = new ArrayList<>();
    private int totalSlots;
    private float percentStolen;
    private final Timer timer = new Timer();
    private ChestMenu chest;

    private Runnable nextAction;
    private final Runnable closeAction = () -> {
        if (close.getValue())
            mc.player.closeContainer();
        inChest = false;
    };

    private final Runnable stealAction = () -> {
        // skip slots emptied since the path was planned (by hand or by the server)
        while (!path.isEmpty() && !chest.getSlot(path.get(0).s).hasItem())
            path.remove(0);
        if (path.isEmpty()) {
            timer.setCooldown(nextDelay());
            nextAction = closeAction;
            return;
        }
        percentStolen = (totalSlots - path.size()) / (float) (totalSlots);
        mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HAT.value(), net.minecraft.sounds.SoundSource.PLAYERS, 3f, percentStolen * 2f, false);
        mc.gameMode.handleContainerInput(chest.containerId, path.remove(0).s, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, mc.player);
        timer.setCooldown(nextDelay());
    };

    private final Runnable startAction = () -> {
        path = generatePath(chest);
        totalSlots = path.size();
        nextAction = stealAction;
    };


    @RequiresPlayer
    @EventLink
    public final Listener<EventPacket.OutGoing> chestClickListener = event -> {
        if (!(event.getPacket() instanceof ServerboundUseItemOnPacket placement))
            return;
        Block block = mc.level.getBlockState(placement.hitResult().getBlockPos()).getBlock();
        if (block instanceof ChestBlock || block instanceof EnderChestBlock)
            chestClick.reset();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventDisplayGuiScreen> eventDisplayScreen = event -> {
        inChest = event.getGuiScreen() instanceof ContainerScreen && mc.player.containerMenu instanceof ChestMenu
                && (anyContainer.getValue() || chestClick.getTime() <= CHEST_CLICK_WINDOW_MS);
        chestClick.setTime(0);
        if (!inChest)
            return;
        chest = (ChestMenu) mc.player.containerMenu;
        percentStolen = 0;
        path.clear();
        timer.setCooldown(nextDelay());
        timer.start();
        nextAction = startAction;
    };


    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> tickListener = event -> {
        if (!inChest)
            return;
        if (mc.gameMode == null || mc.player.containerMenu != chest) {
            inChest = false;
            return;
        }
        // with an Activate Key, stealing pauses while it is not held
        if (activateKey.getValue() != Bind.None && !bindDown(activateKey.getValue()))
            return;

        if (timer.hasFinished()) {
            nextAction.run();
            timer.start();
        }
    };

    @Override
    protected void onDisable() {
        inChest = false;
    }

    private int nextDelay() {
        return (int) (randomize.getValue() ? delayRange.getValue().getRandomInRange() : delay.getValue().getInput());
    }

    private static boolean bindDown(Bind b) {
        int mask = switch (b) {
            case Middle -> 2;
            case Mouse4 -> 8;
            case Mouse5 -> 16;
            default -> 0;
        };
        if (mask != 0)
            return (org.lwjgl.sdl.SDLMouse.SDL_GetMouseState(null, null) & mask) != 0;
        return Keys.isKeyDown(Keys.getKeyIndex(b.name()));
    }

    public ArrayList<Slot> generatePath(ChestMenu chest) {
        ArrayList<Slot> slots = new ArrayList<Slot>();
        for (int i = 0; i < chest.getContainer().getContainerSize(); i++) {
            if (!chest.getSlot(i).getItem().isEmpty())
                slots.add(new Slot(i));
        }
        Slot[] ss = sort(slots.toArray(new Slot[slots.size()]));
        ArrayList<Slot> newSlots = new ArrayList<>();
        Collections.addAll(newSlots, ss);
        return newSlots;
    }

    public static Slot[] sort(Slot[] in) {
        if (in == null || in.length == 0) {
            return in;
        }
        Slot[] out = new Slot[in.length];
        Slot current = in[ThreadLocalRandom.current().nextInt(0, in.length)];
        for (int i = 0; i < in.length; i++) {
            if (i == in.length - 1) {
                out[in.length - 1] = Arrays.stream(in).filter(p -> !p.visited).findAny().orElseGet(null);
                break;
            }
            Slot finalCurrent = current;
            out[i] = finalCurrent;
            finalCurrent.visit();
            Slot next = Arrays.stream(in).filter(p -> !p.visited)
                    .min(Comparator.comparingDouble(p -> p.getDistance(finalCurrent))).get();
            current = next;
        }
        return out;
    }

    private class Slot {
        final int x;
        final int y;
        final int s;
        boolean visited;

        public Slot(int s) {
            this.x = (s + 1) % 10;
            this.y = s / 9;
            this.s = s;
        }

        public double getDistance(Slot s) {
            return Math.abs(this.x - s.x) + Math.abs(this.y - s.y);
        }

        public void visit() {
            visited = true;
        }
    }

    public void draw(AbstractContainerScreen container) {
        if (!inChest)
            return;
        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();
        float textX = (container.width) / 2f;
        float textY = 2 * (container.height / 3f);
        int color = RenderUtils.interpolateColoursInt(ThemeManager.getDarkerColor(), ThemeManager.getMainColor(), percentStolen);
        String text = "Stealing (press escape to leave)";
        fr.drawStringWithShadow(text, textX, textY, color, fr.CENTREX, fr.CENTREY);
        float fontWidth = fr.getWidth(text);
        float fontHeight = fr.getHeight(text);
        float x1 = textX - fontWidth / 2f;
        float y1 = textY + fontHeight;
        float x2 = x1 + 1 + (fontWidth * percentStolen);
        float y2 = textY + (2 * fontHeight);
        float radius = (y2 - y1);
        x2 = Math.max(x1 + radius, x2);
        DrawUtils.drawRoundedRect(x1, y1, x2, y2, radius, color);
    }

    public boolean isInChest() {
        return inChest;
    }
}
