/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.joltjni;

import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.JoltPhysicsObject;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.readonly.ConstPlane;
import com.github.stephengold.joltjni.readonly.Mat44Arg;
import com.github.stephengold.joltjni.readonly.RVec3Arg;
import com.github.stephengold.joltjni.readonly.Vec3Arg;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.nio.FloatBuffer;

/**
 * TODO: Remove this mixin once JoltJNI version is greater than 3.4.0.
 * Reason: JoltJNI 3.4.1+ fixes the FloatBuffer bug in ConvexShape#getSubmergedVolume,
 * so this patch is no longer needed.
 */
@Mixin(value = com.github.stephengold.joltjni.ConvexShape.class, remap = false)
public abstract class MixinConvexShape extends JoltPhysicsObject {

    /**
     * A self-contained, thread-local buffer to reuse for native calls.
     * This avoids performance penalties from repeated allocation of direct buffers and
     * cleanly bypasses all access issues with the package-private Temporaries class.
     * The size of 12 matches the original implementation to ensure sufficient capacity.
     */
    @Unique
    private static final ThreadLocal<FloatBuffer> velthoric$reusableFloatBuffer
            = ThreadLocal.withInitial(() -> Jolt.newDirectFloatBuffer(12));

    /**
     * @author xI-Mx-Ix
     * @reason Corrects a bug causing an InvalidMarkException and resolves access issues
     * to the package-private Temporaries class by using a self-managed, reusable buffer.
     */
    @Overwrite
    public void getSubmergedVolume(
            Mat44Arg comTransform, Vec3Arg scale, ConstPlane surface,
            float[] storeTotalVolume, float[] storeSubmergedVolume,
            Vec3 storeCenterOfBuoyancy, RVec3Arg baseOffset) {

        final long shapeVa = this.va();
        final long comTransformVa = comTransform.targetVa();

        // Get the reusable, thread-local buffer. This is highly performant.
        FloatBuffer floatBuffer = velthoric$reusableFloatBuffer.get();

        // The original bug fix: use clear() instead of reset().
        floatBuffer.clear();

        scale.put(floatBuffer);
        surface.put(floatBuffer);

        boolean useBase = Jolt.implementsDebugRendering();
        double baseX = useBase ? baseOffset.xx() : 0.;
        double baseY = useBase ? baseOffset.yy() : 0.;
        double baseZ = useBase ? baseOffset.zz() : 0.;

        getSubmergedVolume(shapeVa, comTransformVa, floatBuffer, baseX, baseY, baseZ);

        if (storeTotalVolume != null && storeTotalVolume.length > 0) {
            storeTotalVolume[0] = floatBuffer.get(0);
        }
        if (storeSubmergedVolume != null && storeSubmergedVolume.length > 0) {
            storeSubmergedVolume[0] = floatBuffer.get(1);
        }
        if (storeCenterOfBuoyancy != null) {
            storeCenterOfBuoyancy.set(floatBuffer.get(2), floatBuffer.get(3), floatBuffer.get(4));
        }
    }

    // Shadow the native method from the target class so we can call it.
    @Shadow(remap = false)
    private static native void getSubmergedVolume(
            long shapeVa, long comTransformVa, FloatBuffer floatBuffer,
            double baseX, double baseY, double baseZ);
}