package arsenic.module.impl.world;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventDisplayGuiScreen;
import arsenic.injection.accessor.IMixinItemSword;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.minecraft.PlayerUtils;
import ibxm.Player;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ModuleInfo(name = "InvManager", category = ModuleCategory.OTHER)
public class InvManager extends Module {

    public final RangeProperty startDelay = new RangeProperty("StartDelay", new RangeValue(0, 500, 75, 150, 1));
    public final RangeProperty delay = new RangeProperty("Delay", new RangeValue(0, 500, 75, 150, 1));
    public final BooleanProperty hideGui = new BooleanProperty("HideGui", false);
    public final BooleanProperty closeOnFinish = new BooleanProperty("Close on finish", true);

    @PropertyInfo(reliesOn = "Close on finish", value = "true")
    public final RangeProperty closeDelay = new RangeProperty("Close Delay", new RangeValue(0, 500, 75, 150, 1));

    private ExecutorService executor;


    @EventLink
    public final Listener<EventDisplayGuiScreen> eventDisplayGuiScreenListener = event -> {
        if(mc.thePlayer == null || !(event.getGuiScreen() instanceof GuiContainer))
            return;
        if(mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer || event.getGuiScreen() == null)
            return;
        ContainerPlayer container = (ContainerPlayer) mc.thePlayer.openContainer;
        executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            sleep((int) startDelay.getValue().getRandomInRange());
            List<Slot> path = generatePath(container);
            while(!path.isEmpty()) {
                Slot slot = path.remove(0);
                if(slot.slot < 0)
                    continue;
                //PlayerUtils.addWaterMarkedMessageToChat(slot.slot);
                //PlayerUtils.addWaterMarkedMessageToChat( mc.thePlayer.openContainer.getSlot(slot.slot).getStack().getDisplayName() + " ");
                mc.playerController.windowClick(mc.thePlayer.openContainer.windowId, slot.slot, slot.button, slot.mode, mc.thePlayer);
                sleep((int) delay.getValue().getRandomInRange());
            }
        });
    };

    private void sleep(int ms) {
        try {Thread.sleep(ms);} catch (InterruptedException ignored) {}
    }


    //very repetitive need to fix sooner or later
    //zzzz... mi mi mi mi...
    //zzzz... mi mi mi mi...
    //zzzz...
    public List<Slot> generatePath(ContainerPlayer inv) {
        ArrayList<Slot> slots = new ArrayList<>();
        Slot.Armor[] bestArmour = { new Slot.Armor(-1), new Slot.Armor(-1), new Slot.Armor(-1), new Slot.Armor(-1) };
        Slot.Sword bestSword = new Slot.Sword(-1);
        Slot.Stack mostBlocks = new Slot.Stack(-1);
        Slot.Stack projectiles = new Slot.Stack(-1);
        for (int i = 0; i < inv.getInventory().size(); i++) {

            ItemStack stack = inv.getInventory().get(i);
            if(stack == null)
                continue;
            Item item = stack.getItem();

            if (item instanceof ItemArmor) {
                if(!(i > 4 && i < 9)) {
                    Slot.Armor ia = new Slot.Armor(i);
                    if (bestArmour[ia.type].protectionValue < ia.protectionValue) {
                        bestArmour[ia.type].button = 1;
                        bestArmour[ia.type].mode = 4;
                        bestArmour[ia.type] = ia;
                    } else {
                        ia.button = 1;
                        ia.mode = 4;
                        slots.add(ia);
                    }
                }
            }

            else if(item instanceof ItemSword) {
                Slot.Sword sword = new Slot.Sword(i);
                if(sword.attackValue > bestSword.attackValue) {
                    bestSword.button = 1;
                    bestSword.mode = 4;
                    bestSword = sword;
                } else {
                    sword.button = 1;
                    sword.mode = 4;
                    slots.add(sword);
                }
            }

            else if(item instanceof ItemBlock) {
                Slot.Stack block = new Slot.Stack(i);
                if(block.amount > mostBlocks.amount) {
                    mostBlocks.button = 1;
                    mostBlocks.mode = 4;
                    mostBlocks = block;
                } else {
                    block.button = 1;
                    block.mode = 4;
                    slots.add(block);
                }
            }

            else if(item instanceof ItemEgg || item instanceof ItemFishingRod) {
                Slot.Stack projectile = new Slot.Stack(i);
                if(projectile.amount > projectiles.amount)
                    projectiles = projectile;
            }

            else if (item != null) {
                Slot s = new Slot(i);
                s.button = 1;
                s.mode = 4;
                slots.add(s);
            }

        }

        for (int i = 0; i < 4; i++) {
            int slotId = i + 5;
            if(mc.thePlayer.openContainer.getSlot(slotId).getStack() != null && mc.thePlayer.openContainer.getSlot(slotId).getStack().getItem() instanceof ItemArmor) {
                Slot.Armor s = new Slot.Armor(i + 5);
                PlayerUtils.addWaterMarkedMessageToChat(s.protectionValue + " " + bestArmour[i].protectionValue);
                if (s.protectionValue < bestArmour[i].protectionValue) {
                    s.button = 1;
                    s.mode = 4;
                    slots.add(s);
                } else {
                    bestArmour[i].button = 1;
                    bestArmour[i].mode = 4;
                }
            }
            slots.add(bestArmour[i]);
        }

        if(bestSword.slot != 36) {
            bestSword.button = 0;
            bestSword.mode = 2;
            slots.add(bestSword);
        }

        if(mostBlocks.slot != 37) {
            mostBlocks.button = 1;
            mostBlocks.mode = 2;
            slots.add(mostBlocks);
        }

        if(projectiles.slot != 38) {
            projectiles.button = 2;
            projectiles.mode = 2;
            slots.add(projectiles);
        }

        return slots;
    }

    private static class Slot {
        final int x;
        final int y;
        final int slot;
        int mode = 1;
        int button = 0;

        public Slot(int s) {
            this.slot = s;
            this.x = (s + 1) % 10;
            this.y = s / 9;
        }

        private static class Armor extends Slot {
            int type;
            float protectionValue;
            public Armor(int s) {
                super(s);
                setValues();
            }

            public void setValues() {
                if (slot < 0)
                    return;

                ItemStack itemStack = mc.thePlayer.openContainer.getSlot(slot).getStack();
                net.minecraft.item.Item is = itemStack.getItem();
                if (!(is instanceof ItemArmor))
                    return;

                ItemArmor as = (ItemArmor) is;
                float pl;
                try {
                    pl = EnchantmentHelper.getEnchantments(itemStack).get(0);
                } catch(Exception e) {
                    pl = 0;
                }
                protectionValue = as.damageReduceAmount + (float) (pl * 0.6);
                type = as.armorType;
            }
        }

        private static class Sword extends Slot {
            float attackValue;
            public Sword(int s) {
                super(s);
                if(s < 0)
                    return;
                attackValue = ((IMixinItemSword)mc.thePlayer.openContainer.getSlot(s).getStack().getItem()).getAttackDamage();
            }
        }

        private static class Stack extends Slot {
            float amount;
            public Stack(int s) {
                super(s);
                if(s < 0)
                    return;
                amount = mc.thePlayer.openContainer.getSlot(s).getStack().stackSize;
            }
        }

    }

}
