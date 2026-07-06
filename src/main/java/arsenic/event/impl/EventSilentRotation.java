package arsenic.event.impl;

import arsenic.event.types.Event;
import arsenic.injection.accessor.IMixinEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.List;

public class EventSilentRotation implements Event {

    private final float initYaw;
    private final float initPitch;
    private float yaw, pitch;
    private float speed;
    private boolean doMovementFix = true;
    private boolean doJumpFix = true;
    private boolean preventDuplicateLook = false;
    private static Minecraft mc = Minecraft.getMinecraft();

    public EventSilentRotation(float yaw, float pitch,float speed) {
        this.initYaw = yaw;
        this.initPitch = pitch;
        this.yaw = yaw;
        this.pitch = pitch;
        this.speed = speed;
    }

    public boolean hasBeenModified() {
        return initYaw != yaw || initPitch != pitch;
    }

    public float getYaw() { return yaw; }

    public void setYaw(float yaw) { this.yaw = yaw; }

    public float getPitch() { return pitch; }

    public void setPitch(float pitch) { this.pitch = pitch; }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public boolean doMovementFix() {
        return doMovementFix;
    }

    public boolean doJumpFix(){
        return doJumpFix;
    }

    public void setJumpFix(boolean doJumpFix){
        this.doJumpFix = doJumpFix;
    }

    public void setDoMovementFix(boolean doMovementFix) {
        this.doMovementFix = doMovementFix;
    }

    public boolean isPreventDuplicateLook() {
        return preventDuplicateLook;
    }

    public void setPreventDuplicateLook(boolean preventDuplicateLook) {
        this.preventDuplicateLook = preventDuplicateLook;
    }

    /**
     * Fired once per tick after the {@code SilentRotationManager} has settled on the
     * final rotations it will apply (post GCD-patch, speed limiting and duplicate-look
     * handling). Read-only: consumers observe the values the manager committed to, they
     * do not influence them — use the enclosing {@link EventSilentRotation} for that.
     */
    public static class Post implements Event {

        private final float yaw, pitch;
        private final float prevYaw, prevPitch;
        private final boolean modified;
        private final float speed;

        public Post(float yaw, float pitch, float prevYaw, float prevPitch, boolean modified, float speed) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.prevYaw = prevYaw;
            this.prevPitch = prevPitch;
            this.modified = modified;
            this.speed = speed;
        }

        public float getYaw() { return yaw; }

        public float getPitch() { return pitch; }

        public float getPrevYaw() { return prevYaw; }

        public float getPrevPitch() { return prevPitch; }

        public boolean isModified() { return modified; }

        public float getSpeed() { return speed; }

        public MovingObjectPosition getRayTrace() {
            Vec3 vec3 = mc.thePlayer.getPositionEyes(1);
            Vec3 vec31 = ((IMixinEntity) mc.thePlayer).invokeGetVectorForRotation(pitch, yaw);
            Vec3 vec32 = vec3.addVector(vec31.xCoord * 4.5, vec31.yCoord * 4.5, vec31.zCoord * 4.5);
            return mc.thePlayer.worldObj.rayTraceBlocks(vec3, vec32, false, false, true);
        }

        public MovingObjectPosition getRayTraceEntity() {
            Vec3 vec3 = mc.thePlayer.getPositionEyes(1);
            Vec3 vec31 = ((IMixinEntity) mc.thePlayer).invokeGetVectorForRotation(pitch, yaw);
            Vec3 vec32 = vec3.addVector(vec31.xCoord * 4.5, vec31.yCoord * 4.5, vec31.zCoord * 4.5);

            // Raycast blocks
            MovingObjectPosition blockHit = mc.thePlayer.worldObj.rayTraceBlocks(vec3, vec32, false, false, true);
            double blockDistance = blockHit != null ? vec3.distanceTo(blockHit.hitVec) : Double.MAX_VALUE;

            // Raycast entities
            MovingObjectPosition entityHit = null;
            double entityDistance = Double.MAX_VALUE;

            List<Entity> entities = mc.thePlayer.worldObj.getEntitiesInAABBexcluding(
                    mc.thePlayer,
                    mc.thePlayer.getEntityBoundingBox()
                            .addCoord(vec31.xCoord * 4.5, vec31.yCoord * 4.5, vec31.zCoord * 4.5)
                            .expand(1, 1, 1),
                    entity -> entity.canBeCollidedWith()
            );

            for (Entity entity : entities) {
                float f = entity.getCollisionBorderSize();
                AxisAlignedBB aabb = entity.getEntityBoundingBox().expand(f, f, f);
                MovingObjectPosition hit = aabb.calculateIntercept(vec3, vec32);

                if (hit != null) {
                    double dist = vec3.distanceTo(hit.hitVec);
                    if (dist < entityDistance) {
                        entityHit = new MovingObjectPosition(entity, hit.hitVec);
                        entityDistance = dist;
                    }
                }
            }

            return entityDistance < blockDistance ? entityHit : blockHit;
        }
    }
}
