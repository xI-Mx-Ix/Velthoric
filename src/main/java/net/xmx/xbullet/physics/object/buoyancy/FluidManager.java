package net.xmx.xbullet.physics.object.buoyancy;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.operator.Op;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.FluidState;
import net.xmx.xbullet.physics.object.physicsobject.type.rigid.RigidPhysicsObject;
import net.xmx.xbullet.physics.world.PhysicsWorld;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class FluidManager {

    private static final float GRAVITY_MAGNITUDE = 9.81f;
    private static final float DRAG_COEFFICIENT = 1.05f;
    private static final float POINT_VOLUME_SIDE = 0.4f;
    private static final float POINT_AREA = POINT_VOLUME_SIDE * POINT_VOLUME_SIDE;

    private final ConcurrentHashMap<UUID, List<Vec3>> buoyancyPointCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Float> objectSubmergedRatio = new ConcurrentHashMap<>();
    private final Set<UUID> pendingCalculations = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final RVec3 tempWorldPoint = new RVec3();
    private final Vec3 tempRelativePos = new Vec3();
    private final Vec3 tempPointVelocity = new Vec3();
    private final Vec3 tempDragForce = new Vec3();

    public FluidManager() {}

    public void tickForDimension(ServerLevel level, PhysicsWorld physicsWorld, Collection<RigidPhysicsObject> allObjects) {
        BodyLockInterface bodyLockInterface = physicsWorld.getBodyLockInterface();
        if (bodyLockInterface == null) return;

        for (RigidPhysicsObject obj : allObjects) {
            int bodyId = obj.getBodyId();
            if (obj.isRemoved() || bodyId == 0) {
                cleanupObjectState(obj.getPhysicsId());
                continue;
            }

            List<Vec3> buoyancyPoints = getOrRequestBuoyancyPoints(obj, bodyLockInterface);
            if (buoyancyPoints == null || buoyancyPoints.isEmpty()) {
                continue;
            }

            handleFluidInteraction(level, obj, physicsWorld, bodyLockInterface, buoyancyPoints);
        }
    }

    private List<Vec3> getOrRequestBuoyancyPoints(RigidPhysicsObject obj, BodyLockInterface bodyLockInterface) {
        List<Vec3> points = buoyancyPointCache.get(obj.getPhysicsId());
        if (points == null && pendingCalculations.add(obj.getPhysicsId())) {
            try (BodyLockRead lock = new BodyLockRead(bodyLockInterface, obj.getBodyId())) {
                if (lock.succeededAndIsInBroadPhase()) {
                    try (ShapeRefC shapeRef = lock.getBody().getShape().toRefC()) {
                        BuoyancyPointCalculator.calculateAsync(shapeRef.getPtr()).thenAccept(calculatedPoints -> {
                            buoyancyPointCache.put(obj.getPhysicsId(), calculatedPoints);
                            pendingCalculations.remove(obj.getPhysicsId());
                        });
                    }
                } else {
                    pendingCalculations.remove(obj.getPhysicsId());
                }
            }
        }
        return points;
    }

    private void handleFluidInteraction(ServerLevel level, RigidPhysicsObject obj, PhysicsWorld physicsWorld, BodyLockInterface bodyLockInterface, List<Vec3> buoyancyPoints) {
        try (BodyLockWrite lock = new BodyLockWrite(bodyLockInterface, obj.getBodyId())) {
            if (!lock.succeededAndIsInBroadPhase()) return;

            Body body = lock.getBody();

            if (!body.isActive()) {
                return;
            }

            Vec3 totalBuoyancyForce = new Vec3();
            Vec3 totalDragForce = new Vec3();
            Vec3 totalDragTorque = new Vec3();
            int submergedPointsCount = 0;

            RMat44 transform = body.getCenterOfMassTransform();
            RVec3 centerOfMass = body.getCenterOfMassPosition();
            Vec3 linearVel = body.getLinearVelocity();
            Vec3 angularVel = body.getAngularVelocity();

            for (Vec3 localPoint : buoyancyPoints) {

                tempWorldPoint.set(transform.multiply3x4(localPoint));

                BlockPos blockPos = BlockPos.containing(tempWorldPoint.xx(), tempWorldPoint.yy(), tempWorldPoint.zz());
                FluidState fluidState = level.getFluidState(blockPos);
                if (fluidState.isEmpty()) continue;

                Optional<FluidType> fluidTypeOpt = FluidType.fromFluidState(fluidState);
                if (fluidTypeOpt.isPresent()) {
                    FluidType fluidType = fluidTypeOpt.get();
                    float fluidLevel = fluidState.getHeight(level, blockPos) + blockPos.getY();
                    float immersionDepth = fluidLevel - (float) tempWorldPoint.yy();

                    if (immersionDepth > 0) {
                        submergedPointsCount++;
                        immersionDepth = Math.min(immersionDepth, POINT_VOLUME_SIDE);

                        float buoyantForceMag = immersionDepth * POINT_AREA * fluidType.getDensity() * GRAVITY_MAGNITUDE * obj.getBuoyancyFactor();
                        totalBuoyancyForce.addInPlace(0, buoyantForceMag, 0);

                        tempRelativePos.set(Op.minus(tempWorldPoint, centerOfMass));

                        tempPointVelocity.set(angularVel.cross(tempRelativePos));
                        tempPointVelocity.addInPlace(linearVel.getX(), linearVel.getY(), linearVel.getZ());

                        float velocityMagSq = tempPointVelocity.lengthSq();
                        if (velocityMagSq > 1e-6f) {
                            float dragMag = 0.5f * fluidType.getDensity() * velocityMagSq * DRAG_COEFFICIENT * POINT_AREA;

                            tempDragForce.set(tempPointVelocity.normalized());
                            tempDragForce.scaleInPlace(-dragMag);

                            totalDragForce.addInPlace(tempDragForce.getX(), tempDragForce.getY(), tempDragForce.getZ());
                            Vec3 torqueFromDrag = tempRelativePos.cross(tempDragForce);
                            totalDragTorque.addInPlace(torqueFromDrag.getX(), torqueFromDrag.getY(), torqueFromDrag.getZ());
                        }
                    }
                }
            }

            if (submergedPointsCount > 0) {

                body.addForce(totalBuoyancyForce);
                body.addForce(totalDragForce);
                body.addTorque(totalDragTorque);
            }

            float submergedRatio = buoyancyPoints.isEmpty() ? 0f : (float) submergedPointsCount / buoyancyPoints.size();
            updateDamping(obj, body, submergedRatio);
        }
    }

    private void updateDamping(RigidPhysicsObject obj, Body body, float submergedRatio) {
        UUID objId = obj.getPhysicsId();
        float previousRatio = objectSubmergedRatio.getOrDefault(objId, 0.0f);

        if (Math.abs(submergedRatio - previousRatio) > 0.01f || (submergedRatio > 0 && previousRatio == 0) || (submergedRatio == 0 && previousRatio > 0)) {
            objectSubmergedRatio.put(objId, submergedRatio);

            try (MotionProperties motionProperties = body.getMotionProperties()) {
                if (motionProperties != null && motionProperties.hasAssignedNativeObject()) {
                    float baseLinearDamping = obj.getLinearDamping();
                    float fluidLinearDamping = baseLinearDamping * 5.0f;
                    motionProperties.setLinearDamping(lerp(baseLinearDamping, fluidLinearDamping, submergedRatio));

                    float baseAngularDamping = obj.getAngularDamping();
                    float fluidAngularDamping = baseAngularDamping * 10.0f;
                    motionProperties.setAngularDamping(lerp(baseAngularDamping, fluidAngularDamping, submergedRatio));
                }
            }
        }
    }

    private void cleanupObjectState(UUID objId) {
        buoyancyPointCache.remove(objId);
        objectSubmergedRatio.remove(objId);
        pendingCalculations.remove(objId);
    }

    private static float lerp(float a, float b, float f) {
        return a + f * (b - a);
    }
}