package net.xmx.velthoric.physics.object.util;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.operator.Op;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

public class VxExplosionUtil {

    public void applyExplosion(VxPhysicsWorld physicsWorld, Vec3 explosionCenter, float explosionRadius, float explosionStrength) {
        if (!physicsWorld.isRunning()) return;

        physicsWorld.execute(() -> {
            PhysicsSystem physicsSystem = physicsWorld.getPhysicsSystem();
            if (physicsSystem == null) return;

            BroadPhaseQuery broadPhaseQuery = physicsSystem.getBroadPhaseQuery();
            AllHitCollideShapeBodyCollector collector = new AllHitCollideShapeBodyCollector();

            BroadPhaseLayerFilter broadPhaseFilter = new BroadPhaseLayerFilter();
            ObjectLayerFilter objectFilter = new ObjectLayerFilter();

            try {
                broadPhaseQuery.collideSphere(explosionCenter, explosionRadius, collector, broadPhaseFilter, objectFilter);

                int[] hitBodyIds = collector.getHits();

                for (int bodyId : hitBodyIds) {
                    physicsSystem.getBodyInterface().activateBody(bodyId);

                    try (BodyLockWrite lock = new BodyLockWrite(physicsSystem.getBodyLockInterface(), bodyId)) {
                        if (lock.succeededAndIsInBroadPhase()) {
                            Body body = lock.getBody();
                            if (body.isStatic()) {
                                continue;
                            }

                            if (body.isRigidBody()) {
                                RVec3 bodyPosR = body.getCenterOfMassPosition();
                                Vec3 vectorToBody = Op.minus(bodyPosR, explosionCenter.toRVec3()).toVec3();

                                float distanceSq = vectorToBody.lengthSq();
                                if (distanceSq < explosionRadius * explosionRadius && distanceSq > 1.0E-6f) {
                                    float distance = (float)Math.sqrt(distanceSq);

                                    float falloff = 1.0f - (distance / explosionRadius);
                                    float impulseMagnitude = explosionStrength * falloff * falloff;

                                    Vec3 impulseDirection = vectorToBody.normalized();
                                    Vec3 impulse = Op.star(impulseDirection, impulseMagnitude);

                                    body.addImpulse(impulse);
                                }
                            } else if (body.isSoftBody()) {
                                SoftBodyMotionProperties motionProperties = (SoftBodyMotionProperties) body.getMotionProperties();
                                SoftBodyVertex[] vertices = motionProperties.getVertices();
                                if (vertices.length == 0) continue;

                                for(SoftBodyVertex vertex : vertices) {
                                    Vec3 vertexPos = vertex.getPosition();
                                    Vec3 vectorToVertex = Op.minus(vertexPos, explosionCenter);

                                    float distanceSq = vectorToVertex.lengthSq();
                                    if (distanceSq < explosionRadius * explosionRadius && distanceSq > 1.0E-6f) {
                                        float distance = (float)Math.sqrt(distanceSq);
                                        float falloff = 1.0f - (distance / explosionRadius);

                                        float impulseMagnitude = (explosionStrength / vertices.length) * falloff * falloff;

                                        Vec3 impulseDirection = vectorToVertex.normalized();
                                        Vec3 impulse = Op.star(impulseDirection, impulseMagnitude);

                                        if(vertex.getInvMass() > 0) {
                                            Vec3 deltaV = Op.star(impulse, vertex.getInvMass());
                                            vertex.setVelocity(Op.plus(vertex.getVelocity(), deltaV));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } finally {
                collector.close();
                broadPhaseFilter.close();
                objectFilter.close();
            }
        });
    }
}