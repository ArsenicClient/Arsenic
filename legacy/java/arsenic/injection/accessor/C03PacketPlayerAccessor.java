package arsenic.injection.accessor;

import net.minecraft.network.play.client.C03PacketPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(C03PacketPlayer.class)
public interface C03PacketPlayerAccessor {
    @Accessor
    boolean isRotating();

    @Accessor
    void setRotating(boolean rotating);

    @Accessor
    boolean isMoving();

    @Accessor
    void setMoving(boolean moving);

    @Accessor
    boolean isOnGround();

    @Accessor
    void setOnGround(boolean onGround);

    @Accessor
    float getPitch();

    @Accessor
    void setPitch(float pitch);

    @Accessor
    float getYaw();

    @Accessor
    void setYaw(float yaw);

    @Accessor
    double getZ();

    @Accessor
    void setZ(double z);

    @Accessor
    double getY();

    @Accessor
    void setY(double y);

    @Accessor
    double getX();

    @Accessor
    void setX(double x);
}
