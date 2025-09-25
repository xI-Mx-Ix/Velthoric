/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.object.client.time.VxClientClock;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Unique
    private static boolean velthoric$wasPaused = false;

    @Shadow
    @Nullable
    private IntegratedServer singleplayerServer;

    @Inject(method = "runTick", at = @At("HEAD"))
    private void velthoric$onRunTick(boolean renderLevel, CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        VxClientClock clientClock = VxClientClock.getInstance();

        boolean isGamePausable = this.singleplayerServer != null && !this.singleplayerServer.isPublished();

        if (!isGamePausable) {
            if (velthoric$wasPaused) {
                VxMainClass.LOGGER.debug("Game is no longer pausable (e.g., opened to LAN). Ensuring physics is running.");
                VxPhysicsWorld.getAll().forEach(VxPhysicsWorld::resume);
                clientClock.resume();
                velthoric$wasPaused = false;
            }

            return;
        }

        boolean isNowPaused = mc.isPaused();

        if (isNowPaused != velthoric$wasPaused) {
            if (isNowPaused) {
                VxMainClass.LOGGER.debug("Single-player game paused. Pausing physics simulation...");
                VxPhysicsWorld.getAll().forEach(VxPhysicsWorld::pause);
                clientClock.pause();
            } else {
                VxMainClass.LOGGER.debug("Single-player game resumed. Resuming physics simulation...");
                VxPhysicsWorld.getAll().forEach(VxPhysicsWorld::resume);
                clientClock.resume();
            }
            velthoric$wasPaused = isNowPaused;
        }
    }
}