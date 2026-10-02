package arsenic.utils.mixin;

import arsenic.injection.accessor.IMixinLocalPlayer;
import arsenic.utils.java.PlayerInfo;
import arsenic.utils.java.UtilityClass;
import net.minecraft.client.player.LocalPlayer;

public class UtilMixinEntityPlayerSP extends UtilityClass {

    public static PlayerInfo getPlayerInfo(LocalPlayer player) {
        IMixinLocalPlayer accessor = (IMixinLocalPlayer) player;
        return new PlayerInfo(accessor.getLastReportedYaw(), accessor.getLastReportedPitch(), accessor.getServerSprintState());
    }

    public static void setPlayerInfo(LocalPlayer player, PlayerInfo playerInfo) {
        IMixinLocalPlayer accessor = (IMixinLocalPlayer) player;
        accessor.setLastReportedPitch(playerInfo.getLastReportedPitch());
        accessor.setLastReportedYaw(playerInfo.getLastReportedYaw());
        accessor.setServerSprintState(playerInfo.isServerSprintState());
    }
}
