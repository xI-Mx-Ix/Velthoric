package net.xmx.xbullet.physics.object.global;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.operator.Op;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.physicsworld.PhysicsWorld;

public class DetonationEvents {

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        PhysicsWorld physicsWorld = PhysicsWorld.get(serverLevel.dimension());
        if (physicsWorld == null || !physicsWorld.isRunning()) {
            return;
        }

        net.minecraft.world.phys.Vec3 explosionCenterMc = event.getExplosion().getPosition();
        final RVec3 explosionCenter = new RVec3(explosionCenterMc.x, explosionCenterMc.y, explosionCenterMc.z);
        final float explosionRadius = 12.5f;
        final float explosionRadiusSq = explosionRadius * explosionRadius;
        final float rigidBodyImpulseMagnitude = 450.0f;
        final float softBodyNodeImpulseMagnitude = 15.0f;

        physicsWorld.execute(() -> {
            for (IPhysicsObject pco : physicsWorld.getPhysicsObjectsMap().values()) {
                if (pco.isRemoved() || pco.getBodyId() == 0) {
                    continue;
                }

                int bodyId = pco.getBodyId();

                try (BodyLockWrite lock = new BodyLockWrite(physicsWorld.getBodyLockInterface(), bodyId)) {
                    if (!lock.succeeded()) continue;

                    Body body = lock.getBody();
                    if (!body.isActive() || body.isStatic() || body.isKinematic()) {
                        continue;
                    }

                    if (pco instanceof net.xmx.xbullet.physics.object.rigidphysicsobject.RigidPhysicsObject) {
                        applyImpulseToRigidBody(body, explosionCenter, explosionRadius, explosionRadiusSq, rigidBodyImpulseMagnitude);
                    } else if (pco instanceof net.xmx.xbullet.physics.object.softphysicsobject.SoftPhysicsObject) {
                        applyImpulseToSoftBody(body, explosionCenter, explosionRadius, explosionRadiusSq, softBodyNodeImpulseMagnitude);
                    }
                } catch (Exception e) {
                    XBullet.LOGGER.error("Error applying explosion impulse to object {}", pco.getPhysicsId(), e);
                }
            }
        });
    }

    private static void applyImpulseToRigidBody(Body body, RVec3 explosionCenter, float radius, float radiusSq, float magnitude) {
        RVec3 bodyPosition = body.getCenterOfMassPosition();
        double distanceSq = Op.minus(bodyPosition, explosionCenter).lengthSq();

        if (distanceSq > radiusSq) {
            return;
        }

        Vec3 direction = Op.minus(bodyPosition, explosionCenter).toVec3();
        if (direction.lengthSq() < 1e-6f) {
            direction.set(0, 1, 0);
        } else {
            direction.normalizeInPlace();
        }

        float distance = (float) Math.sqrt(distanceSq);
        float attenuation = 1.0f - (distance / radius);
        attenuation = Math.max(0f, attenuation);

        Vec3 impulse = Op.star(direction, magnitude * attenuation);
        body.addImpulse(impulse);
    }

    private static void applyImpulseToSoftBody(Body body, RVec3 explosionCenter, float radius, float radiusSq, float magnitude) {

        SoftBodyMotionProperties mp = (SoftBodyMotionProperties) body.getMotionProperties();
        if (mp == null) return;

        int numVertices = mp.getSettings().countVertices();
        if (numVertices == 0) return;

        RMat44 worldTransform = body.getWorldTransform();

        for (int i = 0; i < numVertices; i++) {

            SoftBodyVertex vertex = mp.getVertex(i);
            if (vertex == null || vertex.getInvMass() <= 0f) {
                continue;
            }

            Vec3 localPos = vertex.getPosition();

            RVec3 worldPos = worldTransform.multiply3x4(localPos);

            double distanceSq = Op.minus(worldPos, explosionCenter).lengthSq();
            if (distanceSq > radiusSq) {
                continue;
            }

            Vec3 direction = Op.minus(worldPos, explosionCenter).toVec3();
            if (direction.lengthSq() < 1e-6f) {
                direction.set(0, 1, 0);
            } else {
                direction.normalizeInPlace();
            }

            float distance = (float) Math.sqrt(distanceSq);
            float attenuation = 1.0f - (distance / radius);
            attenuation = Math.max(0f, attenuation);

            Vec3 impulse = Op.star(direction, magnitude * attenuation);
            body.addImpulse(impulse, worldPos);
        }
    }
}