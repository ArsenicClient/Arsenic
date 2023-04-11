package arsenic.module.impl.world;

import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.inventory.ContainerChest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

@ModuleInfo(name = "ChestStealer", category = ModuleCategory.WORLD)
public class ChestStealer extends Module {

    public final RangeProperty startDelay = new RangeProperty("StartDelay", new RangeValue(0, 500, 75, 150, 1));

    public final RangeProperty delay = new RangeProperty("Delay", new RangeValue(0, 500, 75, 150, 1));

    public final BooleanProperty closeOnFinish = new BooleanProperty("Close on finish", true);

    @PropertyInfo(reliesOn = "Close on finish", value = "true")
    public final RangeProperty closeDelay = new RangeProperty("Delay", new RangeValue(0, 500, 75, 150, 1));

    private ExecutorService executor;


    public void onChestOpen() {
        if(executor != null)
            executor.shutdownNow();
        executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            ContainerChest chest = (ContainerChest) mc.thePlayer.openContainer;
            PlayerUtils.addWaterMarkedMessageToChat("opened");
            sleep((int) startDelay.getValue().getRandomInRange());
            ArrayList<Slot> path = generatePath(chest);
            while(true) {
                if (path.isEmpty()) {
                    onChestEmpty();
                    return;
                }
                mc.playerController.windowClick(mc.thePlayer.openContainer.windowId, path.remove(0).s, 0, 1,mc.thePlayer);
                sleep((int) delay.getValue().getRandomInRange());
            }
        });
    }

    private void onChestEmpty() {
        if(closeOnFinish.getValue()) {
            sleep((int) closeDelay.getValue().getRandomInRange());
            mc.thePlayer.closeScreen();
        }
    }

    private void sleep(int ms) {
        try {Thread.sleep(ms);} catch (InterruptedException ignored) {}
    }

    public void onChestClose() {
        if(executor != null)
            executor.shutdownNow();
    }


    @Override
    protected void onDisable() {
        if(executor != null)
            executor.shutdownNow();
    }

    //below is copied from raven b++ i will review this later
    public ArrayList<Slot> generatePath(ContainerChest chest) {
        ArrayList<Slot> slots = new ArrayList<Slot>();
        for (int i = 0; i < chest.getLowerChestInventory().getSizeInventory(); i++) {
            if (chest.getInventory().get(i) != null)
                slots.add(new Slot(i));
        }
        Slot[] ss = sort(slots.toArray(new Slot[slots.size()]));
        ArrayList<Slot> newSlots = new ArrayList<Slot>();
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
}
