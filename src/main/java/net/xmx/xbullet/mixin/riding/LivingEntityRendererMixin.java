package net.xmx.xbullet.mixin.riding;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.xmx.xbullet.physics.object.riding.RidingProxyEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin extends EntityRenderer<LivingEntity> {

    protected LivingEntityRendererMixin(EntityRendererProvider.Context pContext) {
        super(pContext);
    }

    @Inject(
            method = "setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void xbullet_applyPhysicsObjectTransform(LivingEntity entity, PoseStack poseStack, float ageInTicks, float bodyYaw, float partialTicks, CallbackInfo ci) {
        // Wir greifen nur ein, wenn die Entity auf unserer Proxy-Entity sitzt.
        if (entity.getVehicle() instanceof RidingProxyEntity proxy) {
            proxy.getInterpolatedTransform().ifPresent(physicsTransform -> {

                // Wir brechen die ursprüngliche Methode ab und übernehmen die volle Kontrolle.
                ci.cancel();

                // ----- SCHRITT 1: Standard-Spieler-Rotation (Yaw) anwenden -----
                // DIES IST DER ENTSCHEIDENDE FIX.
                // Wir führen die gleiche Rotation durch, die die originale Minecraft-Methode auch machen würde.
                // Dies dreht den Spieler um seine EIGENE Achse, basierend auf der Kamerabewegung.
                // Ohne diesen Schritt würde sich der Spieler um den Drehpunkt des Fahrzeugs drehen.
                float interpolatedBodyYaw = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - interpolatedBodyYaw));

                // ----- SCHRITT 2: Fahrzeug-Rotation aus der Physik-Engine holen -----
                com.github.stephengold.joltjni.Quat joltQuat = physicsTransform.getRotation();
                Quaternionf vehicleWorldRotation = new Quaternionf(joltQuat.getX(), joltQuat.getY(), joltQuat.getZ(), joltQuat.getW());

                // ----- SCHRITT 3: Neigung (Roll/Pitch) des Fahrzeugs isolieren -----
                // Wir wollen nicht die komplette Rotation des Fahrzeugs, sonst würde der Spieler seine
                // Blickrichtung verlieren. Wir wollen nur, dass er sich mit dem Fahrzeug neigt.
                // Dazu berechnen wir die Rotation, die den "Oben"-Vektor der Welt (0,1,0) zum
                // "Oben"-Vektor des Fahrzeugs ausrichtet. Das Ergebnis ist eine reine Neigungs-Rotation.
                Vector3f worldUp = new Vector3f(0.0f, 1.0f, 0.0f);
                Vector3f vehicleUp = vehicleWorldRotation.transform(new Vector3f(0.0f, 1.0f, 0.0f));
                Quaternionf vehicleTilt = new Quaternionf().rotationTo(worldUp, vehicleUp);

                // In manchen Koordinatensystemen oder Setups muss die Rotation invertiert werden,
                // damit sich der Spieler nicht in die entgegengesetzte Richtung neigt.
                // conjugate() macht genau das. Wahrscheinlich war das schon korrekt.
                vehicleTilt.conjugate();

                // ----- SCHRITT 4: Die Fahrzeugneigung auf den bereits gedrehten Spieler anwenden -----
                // Da der Spieler nun schon korrekt ausgerichtet ist, fügen wir nur noch die Neigung hinzu.
                poseStack.mulPose(vehicleTilt);

                // ----- SCHRITT 5: Den "Dinnerbone"-Fall behandeln (wie im Originalcode) -----
                // Diese Logik sorgt dafür, dass umbenannte Mobs auf dem Kopf stehen.
                if (LivingEntityRenderer.isEntityUpsideDown(entity)) {
                    poseStack.translate(0.0F, entity.getBbHeight() + 0.1F, 0.0F);
                    poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
                }
            });
        }
    }
}