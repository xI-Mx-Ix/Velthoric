/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.bridge.collision.entity;

import net.minecraft.world.entity.Entity;
import net.xmx.velthoric.bridge.collision.entity.VxEntityDragger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin that hooks into the entity tick loop to handle physics body dragging.
 * If an entity is standing on a moving physics body, this ensures the entity moves along with it.
 *
 * @author xI-Mx-Ix
 */
@Mixin(Entity.class)
public abstract class EntityTickMixin {

    /**
     * Updates the entity dragging logic at the start of the base tick.
     *
     * @param ci Callback info.
     */
    @Inject(method = "baseTick", at = @At("HEAD"))
    private void onBaseTickStart(CallbackInfo ci) {
        VxEntityDragger.tick((Entity) (Object) this);
    }
}