/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.misc;

import net.minecraft.client.Minecraft;
import net.xmx.velthoric.init.VxMainClass;

import net.xmx.velthoric.physics.object.client.time.VxClientClock;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Unique
    private static boolean velthoric$wasPaused = false;

    @Inject(method = "runTick", at = @At("HEAD"))
    private void velthoric$onRunTick(boolean renderLevel, CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;

        VxClientClock clientClock = VxClientClock.getInstance();

        if (!mc.isSingleplayer()) {
            if (velthoric$wasPaused) {
                VxMainClass.LOGGER.debug("Left singleplayer world, resetting physics pause state.");
                velthoric$wasPaused = false;

                clientClock.resume();
                clientClock.reset();
            }
            return;
        }

        boolean isNowPaused = mc.isPaused();

        if (isNowPaused != velthoric$wasPaused) {
            if (isNowPaused) {
                VxMainClass.LOGGER.debug("Integrated server game is pausing physics...");
                VxPhysicsWorld.getAll().forEach(VxPhysicsWorld::pause);

                clientClock.pause();
            } else {
                VxMainClass.LOGGER.debug("Integrated server game is resuming physics...");
                VxPhysicsWorld.getAll().forEach(VxPhysicsWorld::resume);

                clientClock.resume();
            }
            velthoric$wasPaused = isNowPaused;
        }
    }
}