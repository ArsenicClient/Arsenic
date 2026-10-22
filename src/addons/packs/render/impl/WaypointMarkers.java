import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.RenderUtils;
import net.minecraft.world.phys.Vec3;
import arsenic.module.ModuleTier;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Named world points, shown as a beam with the name and distance. Add one where you stand with
 * ".waypoint add <name>", remove with ".waypoint remove <name>", list with ".waypoint list" and clear all with
 * ".waypoint clear". The points are saved in this module's settings, so they survive a restart.
 */
@ModuleInfo(name = "WaypointMarkers", description = "Named world points shown as beams, added with .waypoint", category = ModuleCategory.RENDER, tier = ModuleTier.QOL)
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
        for (Point p : parse()) {
            Vec3 base = new Vec3(p.x, p.y, p.z);
            RenderUtils.drawLine(base, base.add(0, BEAM_HEIGHT, 0), RenderUtils.withAlpha(main, 204), 2f);
            double dist = Math.sqrt(mc.player.distanceToSqr(p.x, p.y, p.z));
            RenderUtils.drawWorldText(base.add(0, BEAM_HEIGHT + 1, 0), p.name + "  " + (int) dist + "m", 0xFFFFFFFF, 1f);
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
                    if (args.length < 2 || mc.player == null) return;
                    String name = args[1].replace('|', '_').replace(';', '_');
                    list.add(new Point(name, mc.player.getX(), mc.player.getY(), mc.player.getZ()));
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
