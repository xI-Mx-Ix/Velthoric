package net.xmx.vortex.mixin.impl.riding;

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
import net.xmx.vortex.physics.object.physicsobject.client.ClientObjectDataManager;
import net.xmx.vortex.physics.object.physicsobject.client.interpolation.InterpolationFrame;
import net.xmx.vortex.physics.object.physicsobject.client.interpolation.RenderState;
import net.xmx.vortex.physics.object.riding.RidingProxyEntity;
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

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private Camera mainCamera;

    @Shadow protected abstract double getFov(Camera camera, float f, boolean bl);
    @Shadow public abstract Matrix4f getProjectionMatrix(double d);

    @Unique
    private static final RenderState vortex_reusableRenderState_gameRenderer = new RenderState();

    @WrapOperation(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;prepareCullFrustum(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;Lorg/joml/Matrix4f;)V"
            )
    )
    private void vortex_setupCameraWithPhysicsObject(
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
            InterpolationFrame frame = ClientObjectDataManager.getInstance().getInterpolationFrame(id);
            if (frame == null || !frame.isInitialized) {
                prepareCullFrustum.call(instance, matrixStack, vec3, matrix4f);
                return;
            }

            frame.interpolate(vortex_reusableRenderState_gameRenderer, partialTicks);
            com.github.stephengold.joltjni.Quat physRotQuat = vortex_reusableRenderState_gameRenderer.transform.getRotation();
            Quaternionf physRotation = new Quaternionf(physRotQuat.getX(), physRotQuat.getY(), physRotQuat.getZ(), physRotQuat.getW());

            Quaternionf invPhysRotation = new Quaternionf(
                    new Quaterniond(physRotation).conjugate()
            );
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