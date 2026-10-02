package arsenic.utils.render;

import arsenic.utils.java.UtilityClass;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/**
 * Projects world positions onto the GUI, for HUD overlays that track entities (nametags, arrows,
 * 2D ESP). 1.8 read the GL modelview/projection matrices back with glGetFloat; modern Minecraft
 * does not keep those in GL state, so this rebuilds the perspective from the camera directly.
 */
public class WorldToScreen extends UtilityClass {

    /** A projected point in GUI coordinates, plus its distance along the view direction. */
    public record Point(float x, float y, float depth) {
    }

    /** Projects {@code world} to GUI coordinates, or returns null if it is behind the camera. */
    public static Point project(Vec3 world) {
        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 rel = world.subtract(camera.position());
        Vector3fc forward = camera.forwardVector();
        Vector3fc left = camera.leftVector();
        Vector3fc up = camera.upVector();

        double z = rel.x * forward.x() + rel.y * forward.y() + rel.z * forward.z();
        if (z < 0.05)
            return null;
        double x = rel.x * left.x() + rel.y * left.y() + rel.z * left.z();
        double y = rel.x * up.x() + rel.y * up.y() + rel.z * up.z();

        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        double tanHalf = Math.tan(Math.toRadians(camera.getFov()) / 2.0);
        double aspect = (double) width / height;

        double ndcX = -(x / z) / (tanHalf * aspect);
        double ndcY = (y / z) / tanHalf;
        return new Point((float) ((ndcX + 1) / 2 * width), (float) ((1 - ndcY) / 2 * height), (float) z);
    }

    public static boolean isOnScreen(Point point) {
        return point != null && point.x() >= 0 && point.y() >= 0
                && point.x() <= mc.getWindow().getGuiScaledWidth() && point.y() <= mc.getWindow().getGuiScaledHeight();
    }
}
