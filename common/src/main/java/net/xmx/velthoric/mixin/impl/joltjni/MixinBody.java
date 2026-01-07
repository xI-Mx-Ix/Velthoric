/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.joltjni;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.readonly.RVec3Arg;
import com.github.stephengold.joltjni.readonly.Vec3Arg;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Mixin to replace the implementation of the buoyancy impulse method in the Jolt Body class.
 * <p>
 * This implementation ensures that the correct gravity vector is passed to the
 * native C++ function, ensuring proper buoyancy and drag forces are applied.
 *
 * @author xI-Mx-Ix
 */
@Mixin(value = Body.class, remap = false)
public abstract class MixinBody {

    /**
     * Shadows the private native method used to apply the actual impulse in C++.
     * This signature matches the native declaration exactly.
     */
    @Shadow
    private static native boolean applyBuoyancyImpulse(
            long bodyVa,
            double surfaceX, double surfaceY, double surfaceZ,
            float nx, float ny, float nz,
            float buoyancy,
            float linearDrag, float angularDrag,
            float vx, float vy, float vz,
            float gravityX, float gravityY, float gravityZ,
            float deltaTime
    );

    /**
     * Applies buoyancy and drag impulses to the body.
     * <p>
     * Decomposes the vector arguments into primitive types and invokes the native Jolt method.
     * Explicitly uses the provided gravity vector components for the calculation.
     *
     * @param surfacePosition the location of the fluid's surface
     * @param surfaceNormal   the upward normal direction of the fluid's surface
     * @param buoyancy        buoyancy factor (1 = neutral)
     * @param linearDrag      linear drag coefficient
     * @param angularDrag     angular drag coefficient
     * @param fluidVelocity   velocity of the fluid
     * @param gravity         gravity vector
     * @param deltaTime       simulation step time
     * @return true if an impulse was applied
     * @author xI-Mx-Ix
     * @reason Fixes a copy-paste error where fluid velocity components were passed as gravity components to the native method.
     */
    @Overwrite
    public boolean applyBuoyancyImpulse(
            RVec3Arg surfacePosition, Vec3Arg surfaceNormal, float buoyancy,
            float linearDrag, float angularDrag, Vec3Arg fluidVelocity,
            Vec3Arg gravity, float deltaTime) {

        // Retrieve the native virtual address via the accessor interface.
        AtomicLong addressRef = ((JoltPhysicsObjectAccessor) this).getVirtualAddress();
        long bodyVa = addressRef.get();

        // Extract surface position (double precision)
        double surfaceX = surfacePosition.xx();
        double surfaceY = surfacePosition.yy();
        double surfaceZ = surfacePosition.zz();

        // Extract normal
        float nx = surfaceNormal.getX();
        float ny = surfaceNormal.getY();
        float nz = surfaceNormal.getZ();

        // Extract fluid velocity
        float vx = fluidVelocity.getX();
        float vy = fluidVelocity.getY();
        float vz = fluidVelocity.getZ();

        // Extract gravity components from the gravity vector
        float gravityX = gravity.getX();
        float gravityY = gravity.getY();
        float gravityZ = gravity.getZ();

        // Invoke the native Jolt C++ implementation with the extracted data
        return applyBuoyancyImpulse(
                bodyVa,
                surfaceX, surfaceY, surfaceZ,
                nx, ny, nz,
                buoyancy,
                linearDrag, angularDrag,
                vx, vy, vz,
                gravityX, gravityY, gravityZ,
                deltaTime
        );
    }
}