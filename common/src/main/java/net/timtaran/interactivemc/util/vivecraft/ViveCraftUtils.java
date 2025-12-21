package net.timtaran.interactivemc.util.vivecraft;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.vivecraft.api.VRAPI;
import org.vivecraft.client.ClientVRPlayers;
import org.vivecraft.server.ServerVRPlayers;
import org.vivecraft.server.ServerVivePlayer;

public class ViveCraftUtils {
    public static boolean isVRPlayer(Player player) {
        return VRAPI.instance().isVRPlayer(player);
    }

    @Nullable
    public static VRPlayerData getVRPlayerData(Player player) {
        if (!isVRPlayer(player)) {
            return null;
        } else if (player instanceof ServerPlayer serverPlayer) {
            ServerVivePlayer vivePlayer = ServerVRPlayers.getVivePlayer(serverPlayer);
            return new VRPlayerData(
                    vivePlayer.worldScale,
                    vivePlayer.heightScale,
                    vivePlayer.asVRPose()
            );
        } else {
            ClientVRPlayers.RotInfo clientRotations = ClientVRPlayers.getInstance().getRotationsForPlayer(player.getUUID());

            return new VRPlayerData(
                    clientRotations.worldScale,
                    clientRotations.heightScale,
                    clientRotations.asVRPose(player.position())
            );
        }
    }
}
