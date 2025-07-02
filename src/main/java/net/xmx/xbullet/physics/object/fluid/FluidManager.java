package net.xmx.xbullet.physics.object.fluid;

import com.github.stephengold.joltjni.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.FluidState;
import net.xmx.xbullet.physics.object.rigidphysicsobject.RigidPhysicsObject;
import net.xmx.xbullet.physics.world.PhysicsWorld;

import java.util.Collection;
import java.util.Optional;

public final class FluidManager {

    private static final Vec3 UP_VECTOR = new Vec3(0f, 1f, 0f);
    private static final Vec3 ZERO_VELOCITY = new Vec3(0f, 0f, 0f);

    public FluidManager() {}

    public void tickForDimension(ServerLevel level, PhysicsWorld physicsWorld, Collection<RigidPhysicsObject> allObjects) {
        Vec3 gravity = physicsWorld.getPhysicsSystem().getGravity();

        for (RigidPhysicsObject obj : allObjects) {
            physicsWorld.execute(() -> {
                int bodyId = obj.getBodyId();
                if (obj.isRemoved() || bodyId == 0) {
                    return;
                }

                try (BodyLockWrite lock = new BodyLockWrite(physicsWorld.getBodyLockInterface(), bodyId)) {

                    if (!lock.succeededAndIsInBroadPhase()) {
                        return;
                    }

                    Body body = lock.getBody();
                    if (body == null) {
                        return;
                    }

                    RVec3 centerOfMass = body.getCenterOfMassPosition();
                    BlockPos centerBlockPos = BlockPos.containing(centerOfMass.xx(), centerOfMass.yy(), centerOfMass.zz());

                    FluidState fluidState = level.getFluidState(centerBlockPos);
                    Optional<FluidType> fluidTypeOpt = FluidType.fromFluidState(fluidState);

                    if (fluidTypeOpt.isPresent()) {
                        FluidType fluidType = fluidTypeOpt.get();

                        float fluidLevel = fluidState.getHeight(level, centerBlockPos) + centerBlockPos.getY();
                        RVec3 surfacePosition = new RVec3(centerOfMass.xx(), fluidLevel, centerOfMass.zz());

                        float buoyancy = obj.getBuoyancyFactor();
                        float linearDrag = 0.5f;
                        float angularDrag = 0.05f;

                        body.applyBuoyancyImpulse(
                                surfacePosition,
                                UP_VECTOR,
                                buoyancy,
                                linearDrag,
                                angularDrag,
                                ZERO_VELOCITY,
                                gravity,
                                physicsWorld.getFixedTimeStep()
                        );
                    }
                }
            });
        }
    }
}