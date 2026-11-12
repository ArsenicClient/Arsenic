import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventTick;
import arsenic.gui.hud.HudElement;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.BedwarsTracker;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;

import java.util.HashMap;
import java.util.Map;

@ModuleInfo(name = "BedwarsAdvisor", description = "BedWars armor advice and diamond and emerald generator recommendations", category = ModuleCategory.PLAYER, tier = ModuleTier.VISUAL)
public class BedwarsAdvisor extends Module {
    public final BooleanProperty hud = new BooleanProperty("HUD", true);
    public final BooleanProperty render = new BooleanProperty("Render", true);
    public final BooleanProperty armorAdvice = new BooleanProperty("Armor Advice", true);
    public final BooleanProperty diamondGenerator = new BooleanProperty("Diamond Generator", true);
    public final BooleanProperty emeraldGenerator = new BooleanProperty("Emerald Generator", true);
    public final BooleanProperty showCount = new BooleanProperty("Show Count", true);
    public final BooleanProperty showDistance = new BooleanProperty("Show Distance", true);
    public final DoubleProperty minDiamonds = new DoubleProperty("Min Diamonds", new DoubleValue(1, 64, 3, 1));
    public final DoubleProperty minEmeralds = new DoubleProperty("Min Emeralds", new DoubleValue(1, 64, 2, 1));
    private final HudElement panel = hudElement("BedwarsAdvisor", 4, 60, 150, 26);
    private BlockPos target;
    private int diamonds;
    private double distance;
    private BlockPos emeraldTarget;
    private int emeralds;
    private double emeraldDistance;
    private String armor = "";

    @EventLink
    @RequiresPlayer
    public final Listener<EventTick> onTick = event -> {
        target = null;
        diamonds = 0;
        emeraldTarget = null;
        emeralds = 0;
        armor = "";
        if (!BedwarsTracker.inGame()) return;
        ItemStack leggings = mc.thePlayer.inventory.armorInventory[1];
        Item item = leggings == null ? null : leggings.getItem();
        if (armorAdvice.getValue() && item != Items.iron_leggings && item != Items.diamond_leggings) {
            int gold = 0;
            for (ItemStack stack : mc.thePlayer.inventory.mainInventory)
                if (stack != null && stack.getItem() == Items.gold_ingot) gold += stack.stackSize;
            armor = gold >= 12 ? "Buy iron armor (12 gold)" : "Get iron armor (" + gold + "/12 gold)";
        }
        Map<BlockPos, Integer> counts = new HashMap<>();
        Map<BlockPos, Integer> emeraldCounts = new HashMap<>();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityItem) || entity.isDead) continue;
            ItemStack stack = ((EntityItem) entity).getEntityItem();
            if (stack == null || (stack.getItem() != Items.diamond && stack.getItem() != Items.emerald)) continue;
            boolean emerald = stack.getItem() == Items.emerald;
            if (emerald ? !emeraldGenerator.getValue() : !diamondGenerator.getValue()) continue;
            BlockPos origin = new BlockPos(entity.posX, entity.posY, entity.posZ);
            BlockPos generator = null;
            double nearest = Double.MAX_VALUE;
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = -4; y <= 0; y++) {
                BlockPos candidate = origin.add(x, y, z);
                if (!mc.theWorld.isBlockLoaded(candidate) || mc.theWorld.getBlockState(candidate).getBlock() != (emerald ? Blocks.emerald_block : Blocks.diamond_block)) continue;
                double dx = entity.posX - candidate.getX() - 0.5;
                double dz = entity.posZ - candidate.getZ() - 0.5;
                double d = dx * dx + dz * dz;
                if (d <= 2.25 && d < nearest) { nearest = d; generator = candidate; }
            }
            if (generator != null) {
                Map<BlockPos, Integer> selected = emerald ? emeraldCounts : counts;
                selected.put(generator, selected.getOrDefault(generator, 0) + stack.stackSize);
            }
        }
        for (Map.Entry<BlockPos, Integer> entry : counts.entrySet()) {
            if (entry.getValue() < minDiamonds.getValue().getInput()) continue;
            BlockPos pos = entry.getKey();
            double d = mc.thePlayer.getDistance(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (entry.getValue() > diamonds || (entry.getValue() == diamonds && d < distance)) {
                target = pos;
                diamonds = entry.getValue();
                distance = d;
            }
        }
        for (Map.Entry<BlockPos, Integer> entry : emeraldCounts.entrySet()) {
            if (entry.getValue() < minEmeralds.getValue().getInput()) continue;
            BlockPos pos = entry.getKey();
            double d = mc.thePlayer.getDistance(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (entry.getValue() > emeralds || (entry.getValue() == emeralds && d < emeraldDistance)) {
                emeraldTarget = pos;
                emeralds = entry.getValue();
                emeraldDistance = d;
            }
        }
    };

    @EventLink
    @RequiresPlayer
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue() || !BedwarsTracker.inGame()) return;
        String shownArmor = armorAdvice.getValue() ? armor : "";
        String diamond = target == null || !diamondGenerator.getValue() || diamonds < minDiamonds.getValue().getInput() ? "" : generatorText("Diamond", diamonds, distance);
        String emerald = emeraldTarget == null || !emeraldGenerator.getValue() || emeralds < minEmeralds.getValue().getInput() ? "" : generatorText("Emerald", emeralds, emeraldDistance);
        int rows = (shownArmor.isEmpty() ? 0 : 1) + (diamond.isEmpty() ? 0 : 1) + (emerald.isEmpty() ? 0 : 1);
        if (rows == 0) return;
        int width = Math.max(150, Math.max(mc.fontRendererObj.getStringWidth(shownArmor), Math.max(mc.fontRendererObj.getStringWidth(diamond), mc.fontRendererObj.getStringWidth(emerald))) + 12);
        int height = 6 + rows * 10;
        panel.setSize(width, height);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + width, panel.y + height, 5, 0x96121212);
        int y = panel.y + 4;
        if (!shownArmor.isEmpty()) {
            mc.fontRendererObj.drawStringWithShadow(shownArmor, panel.x + 6, y, 0xFFFFFFFF);
            y += 10;
        }
        if (!diamond.isEmpty()) {
            mc.fontRendererObj.drawStringWithShadow(diamond, panel.x + 6, y, 0xFF55FFFF);
            y += 10;
        }
        if (!emerald.isEmpty()) mc.fontRendererObj.drawStringWithShadow(emerald, panel.x + 6, y, 0xFF55FF55);
    };

    @EventLink
    @RequiresPlayer
    public final Listener<EventRenderWorldLast> onWorld = event -> {
        if (!render.getValue() || !BedwarsTracker.inGame()) return;
        if (target != null && diamondGenerator.getValue() && diamonds >= minDiamonds.getValue().getInput()) {
            RenderUtils.renderBlock(target, 0x4655FFFF, false, true);
            RenderUtils.renderBlock(target, 0xDC55FFFF, true, false);
        }
        if (emeraldTarget != null && emeraldGenerator.getValue() && emeralds >= minEmeralds.getValue().getInput()) {
            RenderUtils.renderBlock(emeraldTarget, 0x4655FF55, false, true);
            RenderUtils.renderBlock(emeraldTarget, 0xDC55FF55, true, false);
        }
    };
    private String generatorText(String name, int count, double distance) {
        String text = name;
        if (showCount.getValue()) text += ": " + count;
        if (showDistance.getValue()) text += (showCount.getValue() ? "  |  " : ": ") + Math.round(distance) + "m";
        return text;
    }
}
