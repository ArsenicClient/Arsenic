package arsenic.module.impl.player;

import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventDisplayGuiScreen;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.timer.Timer;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ChestMenu;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;

@ModuleInfo(name = "ChestStealer", category = ModuleCategory.PLAYER)
public class ChestStealer extends Module {
    /** Gap between inventory clicks. Lower empties a chest faster and less plausibly. */
    public final DoubleProperty delay = new DoubleProperty("Delay (ms)", new DoubleValue(0, 500, 110, 10));


    private boolean inChest;
    private ArrayList<Slot> path = new ArrayList<>();
    private int totalSlots;
    private float percentStolen;
    private final Timer timer = new Timer();
    private ChestMenu chest;

    private Runnable nextAction;
    private final Runnable closeAction = () -> {
        mc.player.closeContainer();
        inChest = false;
    };

    private final Runnable stealAction = () -> {
        if (path.isEmpty()) {
            timer.setCooldown((int) delay.getValue().getInput());
            nextAction = closeAction;
            return;
        }
        percentStolen = (totalSlots - path.size()) / (float) (totalSlots);
        mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HAT.value(), net.minecraft.sounds.SoundSource.PLAYERS, 3f, percentStolen * 2f, false);
        mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, path.remove(0).s, 0, net.minecraft.world.inventory.ContainerInput.values()[1], mc.player);
        timer.setCooldown((int) delay.getValue().getInput());
    };

    private final Runnable startAction = () -> {
        path = generatePath(chest);
        totalSlots = path.size();
        nextAction = stealAction;
    };


    @EventLink
    public final Listener<EventDisplayGuiScreen> eventDisplayScreen = event -> {
        inChest = (event.getGuiScreen() instanceof ContainerScreen && mc.player.containerMenu instanceof ChestMenu);
        if (!inChest)
            return;
        chest = (ChestMenu) mc.player.containerMenu;
        percentStolen = 0;
        path.clear();
        timer.setCooldown((int) delay.getValue().getInput());
        timer.start();
        nextAction = startAction;
    };


    @EventLink
    public final Listener<EventTick> tickListener = event -> {
        if (!inChest)
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

    //below is copied from raven b++ i will review this later
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
        int color = RenderUtils.interpolateColours(new Color(0xFFFF0000), new Color(0xFF00FF00), percentStolen);
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
