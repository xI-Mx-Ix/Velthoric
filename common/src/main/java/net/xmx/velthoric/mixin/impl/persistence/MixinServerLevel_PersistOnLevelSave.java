/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.persistence;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProgressListener;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to hook into the server level saving process.
 * This ensures that the physics data for the specific level being saved
 * is flushed to disk, making it robust against crashes or forced shutdowns.
 */
@Mixin(ServerLevel.class)
public class MixinServerLevel_PersistOnLevelSave {

    @Inject(
            method = "save(Lnet/minecraft/util/ProgressListener;ZZ)V",
            at = @At("RETURN")
    )
    private void onSaveLevel(@Nullable ProgressListener progress, boolean flush, boolean skipSave, CallbackInfo ci) {
        if (skipSave) {
            return;
        }

        // Cast self to get the instance of the level being saved.
        ServerLevel self = (ServerLevel) (Object) this;
        VxPhysicsWorld world = VxPhysicsWorld.get(self.dimension());

        if (world != null) {
            try {
                // Flush the persistence for the body manager and constraint manager of this specific world.
                world.getBodyManager().flushPersistence(flush);
                world.getConstraintManager().flushPersistence(flush);
            } catch (Exception e) {
                VxMainClass.LOGGER.error(
                        "An exception occurred while flushing persistence for world {}",
                        world.getDimensionKey().location(),
                        e
                );
            }
        }
    }
}