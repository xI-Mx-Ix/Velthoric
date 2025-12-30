/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.body.client.time.VxClientClock;
import net.xmx.velthoric.physics.world.VxClientPhysicsWorld;
import net.xmx.velthoric.util.VxPauseUtil;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to handle the global physics pause state based on the game's pause menu.
 * It also ensures the pause state is reset when disconnecting from a world.
 *
 * @author xI-Mx-Ix
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Unique
    private static boolean velthoric$wasPaused = false;

    @Shadow
    @Nullable
    private IntegratedServer singleplayerServer;

    /**
     * Injects into the main client tick to detect changes in the pause state.
     * When the game is paused in a pausable single-player world, it pauses the physics simulation.
     * When resumed, it resumes the simulation.
     */
    @Inject(method = "runTick", at = @At("HEAD"))
    private void velthoric$onRunTick(boolean renderLevel, CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        VxClientClock clientClock = VxClientPhysicsWorld.getInstance().getClock();

        boolean isGamePausable = this.singleplayerServer != null && !this.singleplayerServer.isPublished();

        // If the game is not currently pausable (e.g., in multiplayer), ensure physics is running.
        if (!isGamePausable) {
            if (velthoric$wasPaused) {
                VxMainClass.LOGGER.debug("Game is no longer pausable. Ensuring physics is running.");
                VxPauseUtil.setPaused(false);
                clientClock.resume();
                velthoric$wasPaused = false;
            }
            return;
        }

        boolean isNowPaused = mc.isPaused();

        if (isNowPaused != velthoric$wasPaused) {
            if (isNowPaused) {
                VxMainClass.LOGGER.debug("Single-player game paused. Pausing physics simulation...");
                VxPauseUtil.setPaused(true);
                clientClock.pause();
            } else {
                VxMainClass.LOGGER.debug("Single-player game resumed. Resuming physics simulation...");
                VxPauseUtil.setPaused(false);
                clientClock.resume();
            }
            velthoric$wasPaused = isNowPaused;
        }
    }

    /**
     * Injects when a level is cleared (e.g., on disconnect).
     * This resets the static pause state tracker to prevent it from carrying over
     * to a new game session.
     */
    @Inject(method = "clearClientLevel(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("HEAD"))
    private void velthoric$onClearLevel(Screen screen, CallbackInfo ci) {
        if (velthoric$wasPaused) {
            velthoric$wasPaused = false;
            VxPauseUtil.setPaused(false);
            VxClientPhysicsWorld.getInstance().getClock().resume(); // Ensure clock is not left in a paused state
        }
    }
}