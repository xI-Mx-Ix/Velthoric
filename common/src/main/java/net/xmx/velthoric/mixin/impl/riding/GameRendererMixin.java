package net.xmx.velthoric.mixin.impl.riding;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.physics.object.client.VxClientObjectInterpolator;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.client.VxClientObjectStore;
import net.xmx.velthoric.physics.riding.RidingProxyEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow @Final Minecraft minecraft;
    @Shadow @Final private Camera mainCamera;

    @Shadow protected abstract double getFov(Camera camera, float f, boolean bl);
    @Shadow public abstract Matrix4f getProjectionMatrix(double d);

    @Unique
    private static final com.github.stephengold.joltjni.RVec3 velthoric_interpolatedPosition_gr = new com.github.stephengold.joltjni.RVec3();
    @Unique
    private static final com.github.stephengold.joltjni.Quat velthoric_interpolatedRotation_gr = new com.github.stephengold.joltjni.Quat();

    @WrapOperation(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;prepareCullFrustum(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;Lorg/joml/Matrix4f;)V"
            )
    )
    private void velthoric_setupCameraWithPhysicsObject(
            LevelRenderer instance,
            PoseStack ignore,
            Vec3 vec3,
            Matrix4f matrix4f,
            Operation<Void> prepareCullFrustum,
            float partialTicks,
            long finishTimeNano,
            PoseStack matrixStack
    ) {
        Entity player = this.minecraft.player;
        if (player == null || !(player.getVehicle() instanceof RidingProxyEntity proxy)) {
            prepareCullFrustum.call(instance, matrixStack, vec3, matrix4f);
            return;
        }

        proxy.getPhysicsObjectId().ifPresentOrElse(id -> {
            VxClientObjectManager manager = VxClientObjectManager.getInstance();
            VxClientObjectStore store = manager.getStore();
            VxClientObjectInterpolator interpolator = manager.getInterpolator();
            Integer index = store.getIndexForId(id);

            if (index == null || !store.render_isInitialized[index]) {
                prepareCullFrustum.call(instance, matrixStack, vec3, matrix4f);
                return;
            }

            interpolator.interpolateFrame(store, index, partialTicks, velthoric_interpolatedPosition_gr, velthoric_interpolatedRotation_gr);
            Quaternionf physRotation = new Quaternionf(
                    velthoric_interpolatedRotation_gr.getX(),
                    velthoric_interpolatedRotation_gr.getY(),
                    velthoric_interpolatedRotation_gr.getZ(),
                    velthoric_interpolatedRotation_gr.getW()
            );

            Quaternionf invPhysRotation = new Quaternionf(new Quaterniond(physRotation).conjugate());
            matrixStack.mulPose(invPhysRotation);

            Matrix3f matrix3f = new Matrix3f(matrixStack.last().normal());
            matrix3f.invert();
            RenderSystem.setInverseViewRotationMatrix(matrix3f);

            double fov = this.getFov(this.mainCamera, partialTicks, true);

            prepareCullFrustum.call(instance, matrixStack, this.mainCamera.getPosition(),
                    this.getProjectionMatrix(Math.max(fov, this.minecraft.options.fov().get())));

        }, () -> {
            prepareCullFrustum.call(instance, matrixStack, vec3, matrix4f);
        });
    }
}