package net.xmx.vortex.mixin.impl.riding;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.xmx.vortex.physics.object.riding.RidingProxyEntity;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow @Final @Mutable
    private Quaternionf rotation;

    @Inject(method = "setup", at = @At("TAIL"))
    private void applyPhysicsObjectRotation(BlockGetter area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float partialTick, CallbackInfo ci) {
        if (focusedEntity != null && focusedEntity.getVehicle() instanceof RidingProxyEntity proxy) {
            Quaternionf physicsRotation = proxy.getPhysicsObjectRotation();
            this.rotation.mul(physicsRotation);
        }
    }
}