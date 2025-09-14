package net.xmx.velthoric.mixin.impl.entity_collision;

import net.minecraft.world.entity.Entity;
import net.xmx.velthoric.physics.entity_collision.EntityDragger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityTickMixin {

    @Inject(method = "baseTick", at = @At("HEAD"))
    private void onBaseTickStart(CallbackInfo ci) {
        EntityDragger.tick((Entity) (Object) this);
    }
}