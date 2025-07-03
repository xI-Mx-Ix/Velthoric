package net.xmx.xbullet.physics.object.fluid;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.operator.Op;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.FluidState;
import net.xmx.xbullet.physics.object.rigidphysicsobject.RigidPhysicsObject;
import net.xmx.xbullet.physics.world.PhysicsWorld;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class FluidManager {

    private static final float GRAVITY_MAGNITUDE = 9.81f;
    private static final float FLUID_DAMPING_MULTIPLIER = 7.0f;
    private static final float DRAG_COEFFICIENT = 0.8f;
    private static final float POINT_VOLUME_SIDE = 0.5f;
    private static final float POINT_AREA = POINT_VOLUME_SIDE * POINT_VOLUME_SIDE;

    private static final float ANGULAR_DAMPING_EXTRA_MULTIPLIER = 10.0f;

    private final ConcurrentHashMap<UUID, List<Vec3>> buoyancyPointCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> objectInFluidState = new ConcurrentHashMap<>();
    private final Set<UUID> pendingCalculations = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public FluidManager() {}

    public void tickForDimension(ServerLevel level, PhysicsWorld physicsWorld, Collection<RigidPhysicsObject> allObjects) {
        for (RigidPhysicsObject obj : allObjects) {
            int bodyId = obj.getBodyId();
            if (obj.isRemoved() || bodyId == 0) {
                cleanupObjectState(obj.getPhysicsId());
                continue;
            }

            List<Vec3> buoyancyPoints = buoyancyPointCache.get(obj.getPhysicsId());
            if (buoyancyPoints == null) {
                if (pendingCalculations.add(obj.getPhysicsId())) {

                    BodyLockInterface bodyLockInterface = physicsWorld.getBodyLockInterface();
                    if (bodyLockInterface == null) {
                        pendingCalculations.remove(obj.getPhysicsId());
                        continue;
                    }

                    try (BodyLockRead lock = new BodyLockRead(bodyLockInterface, bodyId)) {
                        if (lock.succeededAndIsInBroadPhase()) {
                            try (ShapeRefC shapeRef = lock.getBody().getShape().toRefC()) {
                                BuoyancyPointCalculator.calculateAsync(shapeRef.getPtr()).thenAccept(points -> {
                                    buoyancyPointCache.put(obj.getPhysicsId(), points);
                                    pendingCalculations.remove(obj.getPhysicsId());
                                });
                            }
                        } else {
                            pendingCalculations.remove(obj.getPhysicsId());
                        }
                    }
                }
                continue;
            }

            if (buoyancyPoints.isEmpty()) {
                continue;
            }

            handleFluidInteraction(level, obj, physicsWorld, buoyancyPoints);
        }
    }

    private void handleFluidInteraction(ServerLevel level, RigidPhysicsObject obj, PhysicsWorld physicsWorld, List<Vec3> buoyancyPoints) {
        BodyLockInterface bodyLockInterface = physicsWorld.getBodyLockInterface();
        if (bodyLockInterface == null) return;

        try (BodyLockWrite lock = new BodyLockWrite(bodyLockInterface, obj.getBodyId())) {
            if (!lock.succeededAndIsInBroadPhase()) return;

            Body body = lock.getBody();
            boolean isInFluidThisTick = false;

            try (RMat44 transform = body.getCenterOfMassTransform()) {
                for (Vec3 localPoint : buoyancyPoints) {

                    RVec3 worldPoint = transform.multiply3x4(localPoint);
                    BlockPos blockPos = BlockPos.containing(worldPoint.xx(), worldPoint.yy(), worldPoint.zz());
                    FluidState fluidState = level.getFluidState(blockPos);

                    Optional<FluidType> fluidTypeOpt = FluidType.fromFluidState(fluidState);
                    if (fluidTypeOpt.isPresent()) {
                        float fluidLevel = fluidState.getHeight(level, blockPos) + blockPos.getY();
                        float immersionDepth = fluidLevel - worldPoint.y();

                        if (immersionDepth > 0) {
                            isInFluidThisTick = true;
                            immersionDepth = Math.min(immersionDepth, POINT_VOLUME_SIDE);

                            FluidType fluidType = fluidTypeOpt.get();
                            float buoyantForceMag = immersionDepth * POINT_AREA * fluidType.getDensity() * GRAVITY_MAGNITUDE * obj.getBuoyancyFactor();
                            body.addForce(new Vec3(0, buoyantForceMag, 0), worldPoint);

                            Vec3 linearVel = body.getLinearVelocity();
                            Vec3 angularVel = body.getAngularVelocity();
                            RVec3 centerOfMass = body.getCenterOfMassPosition();

                            Vec3 r = Op.minus(worldPoint, centerOfMass).toVec3();
                            Vec3 rotationalVel = angularVel.cross(r);
                            Vec3 velocityAtPoint = Op.plus(linearVel, rotationalVel);

                            float velocityMagSq = velocityAtPoint.lengthSq();
                            if (velocityMagSq > 0.001f) {
                                float dragForceMag = 0.5f * fluidType.getDensity() * velocityMagSq * DRAG_COEFFICIENT * POINT_AREA;
                                Vec3 dragForce = velocityAtPoint.normalized();
                                dragForce.scaleInPlace(-dragForceMag);
                                body.addForce(dragForce, worldPoint);
                            }
                        }
                    }
                }
            }

            UUID objId = obj.getPhysicsId();
            boolean wasInFluid = objectInFluidState.getOrDefault(objId, false);

            try (MotionProperties motionProperties = body.getMotionProperties()) {
                if (motionProperties != null && motionProperties.hasAssignedNativeObject()) {
                    if (isInFluidThisTick && !wasInFluid) {
                        objectInFluidState.put(objId, true);
                        motionProperties.setLinearDamping(obj.getLinearDamping() * FLUID_DAMPING_MULTIPLIER);
                        motionProperties.setAngularDamping(obj.getAngularDamping() * FLUID_DAMPING_MULTIPLIER * ANGULAR_DAMPING_EXTRA_MULTIPLIER);
                    } else if (!isInFluidThisTick && wasInFluid) {
                        objectInFluidState.put(objId, false);
                        motionProperties.setLinearDamping(obj.getLinearDamping());
                        motionProperties.setAngularDamping(obj.getAngularDamping());
                    }
                }
            }
        }
    }

    private void cleanupObjectState(UUID objId) {
        buoyancyPointCache.remove(objId);
        objectInFluidState.remove(objId);
        pendingCalculations.remove(objId);
    }
}