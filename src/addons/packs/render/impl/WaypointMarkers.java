import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.TextProperty;
import arsenic.command.Command;
import arsenic.command.CommandInfo;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Named world points, shown as a beam with the name and distance. Add one where you stand with
 * ".waypoint add <name>", remove with ".waypoint remove <name>", list with ".waypoint list" and clear all with
 * ".waypoint clear". The points are saved in this module's settings, so they survive a restart.
 */
@ModuleInfo(name = "WaypointMarkers", description = "Named world points shown as beams, added with .waypoint", category = ModuleCategory.RENDER)
public class WaypointMarkers extends Module {

    public final BooleanProperty render = new BooleanProperty("Render", true);
    public final TextProperty points = new TextProperty("Points", "", 4000);

    private static final double BEAM_HEIGHT = 48;

    private static final class Point {
        final String name;
        final double x, y, z;

        Point(String name, double x, double y, double z) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    {
        registerCommand(new WaypointCommand());
    }

    /** Billboard text at a camera-relative point, drawn facing the camera. */
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

    private List<Point> parse() {
        List<Point> out = new ArrayList<>();
        for (String entry : points.getValue().split(";")) {
            String[] parts = entry.split("\\|");
            if (parts.length != 4) continue;
            try {
                out.add(new Point(parts[0], Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3])));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private void save(List<Point> list) {
        StringBuilder sb = new StringBuilder();
        for (Point p : list) {
            if (sb.length() > 0) sb.append(';');
            sb.append(p.name).append('|').append(p.x).append('|').append(p.y).append('|').append(p.z);
        }
        points.setValue(sb.toString());
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorld = event -> {
        if (!render.getValue()) return;
        int main = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
        float r = ((main >> 16) & 0xFF) / 255f, g = ((main >> 8) & 0xFF) / 255f, b = (main & 0xFF) / 255f;
        double vx = mc.getRenderManager().viewerPosX, vy = mc.getRenderManager().viewerPosY, vz = mc.getRenderManager().viewerPosZ;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glLineWidth(2f);
        for (Point p : parse()) {
            double x = p.x - vx, y = p.y - vy, z = p.z - vz;
            GlStateManager.color(r, g, b, 0.8f);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex3d(x, y, z);
            GL11.glVertex3d(x, y + BEAM_HEIGHT, z);
            GL11.glEnd();
        }
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();

        for (Point p : parse()) {
            double dist = Math.sqrt(mc.thePlayer.getDistanceSq(p.x, p.y, p.z));
            drawText(p.name + "  " + (int) dist + "m", p.x - vx, p.y + BEAM_HEIGHT - vy + 1, p.z - vz, 0xFFFFFFFF);
        }
    };

    @CommandInfo(name = "waypoint", args = {"action", "name"}, help = "add/remove/list/clear named waypoints", minArgs = 1)
    private class WaypointCommand extends Command {
        @Override
        public void execute(String[] args) {
            String action = args[0].toLowerCase();
            List<Point> list = parse();
            switch (action) {
                case "add": {
                    if (args.length < 2 || mc.thePlayer == null) return;
                    String name = args[1].replace('|', '_').replace(';', '_');
                    list.add(new Point(name, mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ));
                    save(list);
                    arsenic.utils.minecraft.PlayerUtils.addWaterMarkedMessageToChat("Added waypoint " + name);
                    break;
                }
                case "remove": {
                    if (args.length < 2) return;
                    list.removeIf(p -> p.name.equalsIgnoreCase(args[1]));
                    save(list);
                    break;
                }
                case "clear":
                    save(new ArrayList<>());
                    break;
                case "list":
                    for (Point p : list) {
                        arsenic.utils.minecraft.PlayerUtils.addWaterMarkedMessageToChat(p.name + " " + (int) p.x + " " + (int) p.y + " " + (int) p.z);
                    }
                    break;
                default:
                    break;
            }
        }
    }
}
