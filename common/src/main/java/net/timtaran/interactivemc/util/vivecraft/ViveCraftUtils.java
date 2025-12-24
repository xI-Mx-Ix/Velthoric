package net.timtaran.interactivemc.util.vivecraft;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.vivecraft.api.VRAPI;
import org.vivecraft.client.ClientVRPlayers;
import org.vivecraft.server.ServerVRPlayers;
import org.vivecraft.server.ServerVivePlayer;

/**
 * Utilities for interacting with ViveCraft without using the API, as it lacks the necessary functions.
 *
 * @author timtaran
 */
public class ViveCraftUtils {

    /**
     * Checks if the given player is a ViveCraft VR player.
     *
     * @param player the player to check
     * @return `true` if the player is a ViveCraft VR player, `false` otherwise
     */
    public static boolean isVRPlayer(Player player) {
        return VRAPI.instance().isVRPlayer(player);
    }


    /**
     * Retrieves the VR player data for the given player.
     * <p>
     * If the player is not a ViveCraft VR player, this method returns `null`.
     * <p>
     * If the player is a server-side ViveCraft VR player, this method retrieves the
     * world scale, height scale, and VR pose from the ServerVivePlayer instance.
     * <p>
     * If the player is a client-side ViveCraft VR player, this method retrieves the
     * world scale, height scale, and VR pose from the ClientVRPlayers instance.
     *
     * @param player the player to retrieve the VR player data for
     * @return the VR player data, or null if the player is not a ViveCraft VR player
     */
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
