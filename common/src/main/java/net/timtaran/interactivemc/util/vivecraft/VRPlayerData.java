package net.timtaran.interactivemc.util.vivecraft;

import org.vivecraft.api.data.VRPose;

public record VRPlayerData(
        float worldScale,
        float heightScale,
        VRPose vrPose
) {
}
