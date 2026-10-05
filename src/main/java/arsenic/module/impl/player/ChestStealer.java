package arsenic.module.impl.player;

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
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockEnderChest;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;

@ModuleInfo(name = "ChestStealer", category = ModuleCategory.PLAYER)
public class ChestStealer extends Module {
    public final DoubleProperty delay = new DoubleProperty("Delay (ms)", new DoubleValue(0, 500, 110, 10));


    private static final long CHEST_CLICK_WINDOW_MS = 3000;

    private boolean inChest;
    private final MSTimer chestClick = MSTimer.expired();
    private ArrayList<Slot> path = new ArrayList<>();
    private int totalSlots;
    private float percentStolen;
    private final Timer timer = new Timer();
    private ContainerChest chest;

    private Runnable nextAction;
    private final Runnable closeAction = () -> {
        mc.thePlayer.closeScreen();
        inChest = false;
    };

    private final Runnable stealAction = () -> {
        if (path.isEmpty()) {
            timer.setCooldown((int) delay.getValue().getInput());
            nextAction = closeAction;
            return;
        }
        percentStolen = (totalSlots - path.size()) / (float) (totalSlots);
        mc.theWorld.playSound(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, "note.hat", 3f, percentStolen * 2f, false);
        mc.playerController.windowClick(mc.thePlayer.openContainer.windowId, path.remove(0).s, 0, 1, mc.thePlayer);
        timer.setCooldown((int) delay.getValue().getInput());
    };

    private final Runnable startAction = () -> {
        path = generatePath(chest);
        totalSlots = path.size();
        nextAction = stealAction;
    };


    @RequiresPlayer
    @EventLink
    public final Listener<EventPacket.OutGoing> chestClickListener = event -> {
        if (!(event.getPacket() instanceof C08PacketPlayerBlockPlacement))
            return;
        C08PacketPlayerBlockPlacement placement = (C08PacketPlayerBlockPlacement) event.getPacket();
        if (placement.getPlacedBlockDirection() == 255 || placement.getPosition() == null)
            return;
        Block block = mc.theWorld.getBlockState(placement.getPosition()).getBlock();
        if (block instanceof BlockChest || block instanceof BlockEnderChest)
            chestClick.reset();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventDisplayGuiScreen> eventDisplayScreen = event -> {
        inChest = event.getGuiScreen() instanceof GuiChest && mc.thePlayer.openContainer instanceof ContainerChest
                && chestClick.getTime() <= CHEST_CLICK_WINDOW_MS;
        chestClick.setTime(0);
        if (!inChest)
            return;
        chest = (ContainerChest) mc.thePlayer.openContainer;
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

    public ArrayList<Slot> generatePath(ContainerChest chest) {
        ArrayList<Slot> slots = new ArrayList<Slot>();
        for (int i = 0; i < chest.getLowerChestInventory().getSizeInventory(); i++) {
            if (chest.getInventory().get(i) != null)
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

    public void draw(GuiContainer container) {
        if (!inChest)
            return;
        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();
        float textX = (container.width) / 2f;
        float textY = 2 * (container.height / 3f);
        int color = RenderUtils.interpolateColours(new Color(0xFFFF0000), new Color(0xFF00FF00), percentStolen);
        GlStateManager.color(1f, 1f, 1f, 1f);
        RenderUtils.resetColorText();
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
