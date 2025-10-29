/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.persistence;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProgressListener;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.constraint.manager.VxConstraintManager;
import net.xmx.velthoric.physics.body.manager.VxBodyManager;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to hook into the server level saving process.
 * This ensures that all custom physics data is flushed to disk
 * whenever Minecraft performs a world save, making it robust against crashes or
 * forced shutdowns after a save event.
 *
 * @author xI-Mx-Ix
 */
@Mixin(ServerLevel.class)
public class MixinServerLevel_PersistOnLevelSave {

    @Inject(
            method = "save(Lnet/minecraft/util/ProgressListener;ZZ)V",
            at = @At("RETURN")
    )
    private void onSaveLevel(@Nullable ProgressListener progress, boolean flush, boolean skipSave, CallbackInfo ci) {
        if (!skipSave) {
            for (VxPhysicsWorld world : VxPhysicsWorld.getAll()) {
                try {
                    // flush body manager
                    VxBodyManager bodyManager = VxPhysicsWorld.getBodyManager(world.getLevel().dimension());
                    if (bodyManager != null) {
                        bodyManager.flushPersistence(flush);
                    }

                    // flush constraint manager
                    VxConstraintManager constraintManager = VxPhysicsWorld.getConstraintManager(world.getLevel().dimension());
                    if (constraintManager != null) {
                        constraintManager.flushPersistence(flush);
                    }
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
}