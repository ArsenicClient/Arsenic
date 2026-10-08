import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.property.impl.BooleanProperty;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

/**
 * BedWars gear labels above each enemy: the tier of the sword they hold (wood, stone, iron, gold, diamond) and of their
 * chestplate (leather, chain, iron, gold, diamond). Players who are invisible are tagged INVIS, since their gear is only
 * shown when they are visible. Tiers come from the items, so a player with no sword or chestplate shows "-".
 */
@ModuleInfo(name = "GearReveal", description = "Shows an enemy's sword and armour tier above them (BedWars)", category = ModuleCategory.RENDER)
public class GearReveal extends Module {

    public final BooleanProperty render = new BooleanProperty("Render", true);
    public final BooleanProperty showInvisible = new BooleanProperty("Tag Invisible", true);

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorld = event -> {
        if (!render.getValue()) return;
        double vx = mc.getRenderManager().viewerPosX, vy = mc.getRenderManager().viewerPosY, vz = mc.getRenderManager().viewerPosZ;
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (p == mc.thePlayer || p.isDead || AntiBot.isBot(p)) continue;
            String text = "S:" + swordTier(p.getHeldItem()) + "  A:" + armourTier(p.inventory.armorInventory[2]);
            if (showInvisible.getValue() && p.isInvisible()) text += "  INVIS";
            double x = p.lastTickPosX + (p.posX - p.lastTickPosX) * event.partialTicks - vx;
            double y = p.posY + p.height + 0.9 - vy;
            double z = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * event.partialTicks - vz;
            drawText(text, x, y, z, 0xFFFFFFFF);
        }
    };

    private static String swordTier(ItemStack s) {
        if (s == null) return "-";
        if (s.getItem() == Items.wooden_sword) return "wood";
        if (s.getItem() == Items.stone_sword) return "stone";
        if (s.getItem() == Items.iron_sword) return "iron";
        if (s.getItem() == Items.golden_sword) return "gold";
        if (s.getItem() == Items.diamond_sword) return "diamond";
        return "-";
    }

    private static String armourTier(ItemStack s) {
        if (s == null || !(s.getItem() instanceof ItemArmor)) return "-";
        switch (((ItemArmor) s.getItem()).getArmorMaterial()) {
            case LEATHER: return "leather";
            case CHAIN: return "chain";
            case IRON: return "iron";
            case GOLD: return "gold";
            case DIAMOND: return "diamond";
            default: return "-";
        }
    }

    private static void drawText(String text, double x, double y, double z, int argb) {
        GlStateManager.pushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glNormal3f(0f, 1f, 0f);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0f, 1f, 0f);
        GlStateManager.rotate(mc.getRenderManager().playerViewX, 1f, 0f, 0f);
        float s = 0.025f;
        GlStateManager.scale(-s, -s, s);
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        mc.fontRendererObj.drawString(text, -mc.fontRendererObj.getStringWidth(text) / 2, 0, argb);
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }
}
