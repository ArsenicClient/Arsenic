package arsenic.injection.accessor;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.8's C03PacketPlayer accessor. The fields are final now, hence {@link Mutable}. */
@Mixin(ServerboundMovePlayerPacket.class)
public interface IMixinMovePlayerPacket {

    @Accessor("hasRot")
    boolean isRotating();

    @Accessor("hasPos")
    boolean isMoving();

    @Accessor("onGround")
    boolean isOnGround();

    @Mutable
    @Accessor("onGround")
    void setOnGround(boolean onGround);

    @Accessor("xRot")
    float getPitch();

    @Mutable
    @Accessor("xRot")
    void setPitch(float pitch);

    @Accessor("yRot")
    float getYaw();

    @Mutable
    @Accessor("yRot")
    void setYaw(float yaw);

    @Accessor("x")
    double getX();

    @Mutable
    @Accessor("x")
    void setX(double x);

    @Accessor("y")
    double getY();

    @Mutable
    @Accessor("y")
    void setY(double y);

    @Accessor("z")
    double getZ();

    @Mutable
    @Accessor("z")
    void setZ(double z);
}
