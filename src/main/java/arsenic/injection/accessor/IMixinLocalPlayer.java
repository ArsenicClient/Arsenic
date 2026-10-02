package arsenic.injection.accessor;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** What the server last heard about the player's rotation and sprint state. */
@Mixin(LocalPlayer.class)
public interface IMixinLocalPlayer {

    @Accessor("yRotLast")
    float getLastReportedYaw();

    @Accessor("xRotLast")
    float getLastReportedPitch();

    @Accessor("wasSprinting")
    boolean getServerSprintState();

    @Accessor("yRotLast")
    void setLastReportedYaw(float lastReportedYaw);

    @Accessor("xRotLast")
    void setLastReportedPitch(float lastReportedPitch);

    @Accessor("wasSprinting")
    void setServerSprintState(boolean serverSprintState);
}
