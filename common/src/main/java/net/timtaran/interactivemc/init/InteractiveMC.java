package net.timtaran.interactivemc.init;

import com.mojang.logging.LogUtils;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.level.ServerPlayer;
import net.timtaran.interactivemc.init.registries.KeyMappings;
import net.timtaran.interactivemc.util.vivecraft.VRPlayerData;
import net.timtaran.interactivemc.util.vivecraft.ViveCraftUtils;
import org.slf4j.Logger;
import org.vivecraft.api.data.VRPose;

import java.util.concurrent.atomic.AtomicLong;

/**
 * The main class for the InteractiveMC mod.
 *
 * @author timtaran
 */
public class InteractiveMC {
    public static final String MOD_ID = "interactivemc";
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Called during the common initialization phase (Server and Client).
     * Initializes registries, packets, and loads native libraries.
     */
    public static void onInit() {
        LOGGER.info("Initializing InteractiveMC");

        AtomicLong tick = new AtomicLong();

        TickEvent.SERVER_LEVEL_PRE.register(level -> {
            tick.getAndIncrement();

            if (tick.get() % 20 == 0) {
                LOGGER.info("Server Level Tick: {}", tick.get());

                for (ServerPlayer serverPlayer : level.players()) {
                    LOGGER.info(" - Player: {} at {}", serverPlayer.getName().getString(), serverPlayer.blockPosition());
                    if (ViveCraftUtils.isVRPlayer(serverPlayer)) {
                        LOGGER.info("   - VR Player detected");

                        try {
                            VRPlayerData playerData = ViveCraftUtils.getVRPlayerData(serverPlayer);
                            if (playerData != null) {
                                VRPose pose = playerData.vrPose();
                                LOGGER.info("   - World Scale: {}", playerData.worldScale());
                                LOGGER.info("   - Height Scale: {}", playerData.heightScale());
                                LOGGER.info("   - Head Position: {}", pose.getHead().getPos());
                                LOGGER.info("   - Head Rotation: {}", pose.getHead().getRotation());
                                LOGGER.info("   - Main Hand Position: {}", pose.getMainHand().getPos());
                                LOGGER.info("   - Main Hand Rotation: {}", pose.getMainHand().getRotation());
                                LOGGER.info("   - Off Hand Position: {}", pose.getOffHand().getPos());
                                LOGGER.info("   - Off Hand Rotation: {}", pose.getOffHand().getRotation());
                            }
                        } catch (Exception exception) {
                            LOGGER.error("   - Error retrieving VR Pose: {}", exception.getMessage());
                        }
                    }
                }
            }
        });
    }

    public static void onClientInit() {
        LOGGER.info("Initializing InteractiveMC Client");
        KeyMappings.init();
    }
}
