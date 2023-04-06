package arsenic.module.impl.world;

import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.minecraft.PlayerUtils;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ModuleInfo(name = "Chest Stealer", category = ModuleCategory.WORLD)
public class ChestStealer extends Module {

    public final RangeProperty startDelay = new RangeProperty("StartDelay", new RangeValue(0, 500, 150, 75, 1));

    public final RangeProperty delay = new RangeProperty("Delay", new RangeValue(0, 500, 150, 75, 1));

    public final BooleanProperty closeOnFinish = new BooleanProperty("Close on finish", true);

    @PropertyInfo(reliesOn = "Close on finish", value = "true")
    public final RangeProperty closeDelay = new RangeProperty("Delay", new RangeValue(0, 500, 150, 75, 1));

    public final ExecutorService executor = Executors.newSingleThreadExecutor();


    public void onChestOpen() {
        executor.execute(() -> {
            while(true) {
                PlayerUtils.addWaterMarkedMessageToChat("opened");
                try {Thread.sleep(5000);} catch (InterruptedException ignored) {}
            }
        });
    }

    public void onChestClose() {
        executor.shutdownNow();
    }


    @Override
    protected void onDisable() {
        executor.shutdownNow();
    }
}
