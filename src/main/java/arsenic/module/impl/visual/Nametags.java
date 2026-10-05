package arsenic.module.impl.visual;

import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.render.WorldToScreen;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.joml.Matrix3x2fStack;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Player nametags with health and gear.
 * <p>
 * 1.8 drew these as billboards in the world with raw GL. Here each tag is projected onto the
 * screen and drawn as part of the HUD, scaled with distance like a world-space tag would be, which
 * lets the gear row use the game's real item rendering instead of hand-drawn texture sprites.
 */
@ModuleInfo(name = "Nametags", category = ModuleCategory.RENDER, hidden = true)
public class Nametags extends Module {

    public final DoubleProperty tagScale = new DoubleProperty("Scale", new DoubleValue(0.5, 3, 1, 0.05));
    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(8, 128, 64, 1));

    private static final float ICON_SIZE = 16f;
    private static final float ICON_SPACING = 18f;

    @RequiresPlayer
    @EventLink
    public final Listener<EventRender2D> renderListener = event -> {
        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();
        if (fr == null) return;

        // far tags first, so nearer ones draw on top
        List<Player> players = new ArrayList<>(mc.level.players());
        players.sort(Comparator.comparingDouble(p -> -mc.player.distanceToSqr(p)));

        for (Player player : players) {
            if (player == mc.player) continue;
            if (AntiBot.isBot(player)) continue;
            if (player.isRemoved()) continue;
            if (mc.player.distanceTo(player) > range.getValue().getInput()) continue;

            WorldToScreen.Point point = WorldToScreen.project(
                    RenderUtils.interpolatedPosition(player).add(0, player.getBbHeight() + 0.6, 0));
            if (point == null) continue;

            // the same apparent size a world-space tag of fixed height would have
            float scale = (float) (Math.max(0.4, Math.min(2.0, 7.0 / point.depth())) * tagScale.getValue().getInput());
            Matrix3x2fStack pose = event.getGraphics().pose();
            pose.pushMatrix();
            pose.translate(point.x(), point.y());
            pose.scale(scale, scale);
            try {
                drawTag(fr, player);
            } finally {
                pose.popMatrix();
            }
        }
    };

    private void drawTag(FontRendererExtension<?> fr, Player player) {
        String name = net.minecraft.ChatFormatting.stripFormatting(player.getName().getString());
        String text = name + String.format(" §7%.1f", player.getHealth());

        int textWidth = (int) fr.getWidth(text);
        int textHeight = (int) fr.getHeight(text);
        float halfWidth = textWidth / 2f;

        float healthPercent = Math.min(1f, player.getHealth() / player.getMaxHealth());
        int healthColor = healthPercent > 0.5f ? 0xFF2ECC71
                : healthPercent > 0.25f ? 0xFFFFFF00
                : 0xFFFF0000;

        drawGear(fr, collectGear(player), textHeight);

        DrawUtils.drawRect(-halfWidth - 2, -2, halfWidth + 2, textHeight + 2, new Color(0, 0, 0, 100).getRGB());
        fr.drawString(text, -halfWidth, 0, 0xFFFFFFFF);
        DrawUtils.drawRect(-halfWidth - 2, textHeight + 2, -halfWidth - 2 + (textWidth + 4) * healthPercent, textHeight + 3, healthColor);
    }

    /** Held item + the four armour pieces, in a stable left-to-right order, empty slots skipped. */
    private List<ItemStack> collectGear(Player player) {
        List<ItemStack> gear = new ArrayList<>();
        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty()) gear.add(held);
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack armor = player.getItemBySlot(slot);
            if (!armor.isEmpty()) gear.add(armor);
        }
        return gear;
    }

    private void drawGear(FontRendererExtension<?> fr, List<ItemStack> gear, int textHeight) {
        if (gear.isEmpty()) return;

        int count = gear.size();
        float totalW = count * ICON_SPACING;
        float startX = -totalW / 2f;

        float enchScale = 0.55f;
        int enchLineH = (int) (textHeight * enchScale) + 1;
        int maxEnchLines = 0;
        List<List<String>> enchLists = new ArrayList<>();
        for (ItemStack stack : gear) {
            List<String> lines = enchantLines(stack);
            enchLists.add(lines);
            maxEnchLines = Math.max(maxEnchLines, lines.size());
        }

        float enchBlockH = maxEnchLines * enchLineH;
        float iconBottom = -6 - enchBlockH;
        float iconTop = iconBottom - ICON_SIZE;

        var graphics = arsenic.utils.render.RenderContext.graphics();
        for (int i = 0; i < count; i++) {
            float left = startX + i * ICON_SPACING + (ICON_SPACING - ICON_SIZE) / 2f;
            graphics.pose().pushMatrix();
            graphics.pose().translate(left, iconTop);
            graphics.item(gear.get(i), 0, 0);
            graphics.itemDecorations(mc.font, gear.get(i), 0, 0);
            graphics.pose().popMatrix();
        }

        for (int i = 0; i < count; i++) {
            List<String> lines = enchLists.get(i);
            float colCenter = startX + i * ICON_SPACING + ICON_SPACING / 2f;
            for (int l = 0; l < lines.size(); l++) {
                String line = lines.get(l);
                float ty = iconBottom + l * enchLineH;
                graphics.pose().pushMatrix();
                graphics.pose().translate(colCenter, ty);
                graphics.pose().scale(enchScale, enchScale);
                fr.drawString(line, -fr.getWidth(line) / 2f, 0, 0xFFFFFFFF);
                graphics.pose().popMatrix();
            }
        }
    }

    /** One "AbbrevLevel" token per enchantment, e.g. "Prot4", "Unb3". */
    private List<String> enchantLines(ItemStack stack) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<Holder<Enchantment>, Integer> entry : stack.getEnchantments().entrySet())
            out.add(abbreviate(entry.getKey()) + entry.getValue());
        return out;
    }

    private String abbreviate(Holder<Enchantment> enchantment) {
        String id = enchantment.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
        switch (id) {
            case "protection": return "§bProt";
            case "fire_protection": return "§6FP";
            case "feather_falling": return "§fFF";
            case "blast_protection": return "§8BP";
            case "projectile_protection": return "§ePP";
            case "respiration": return "§3Resp";
            case "aqua_affinity": return "§3AA";
            case "thorns": return "§2Thn";
            case "depth_strider": return "§3DS";
            case "sharpness": return "§cSharp";
            case "smite": return "§cSmite";
            case "bane_of_arthropods": return "§cBane";
            case "knockback": return "§7KB";
            case "fire_aspect": return "§6Fire";
            case "looting": return "§aLoot";
            case "efficiency": return "§aEff";
            case "silk_touch": return "§7Silk";
            case "unbreaking": return "§7Unb";
            case "fortune": return "§aFort";
            case "power": return "§cPow";
            case "punch": return "§7Pun";
            case "flame": return "§6Flame";
            case "infinity": return "§eInf";
            default:
                String n = net.minecraft.ChatFormatting.stripFormatting(enchantment.value().description().getString());
                return n.length() > 3 ? n.substring(0, 3) : n;
        }
    }
}
