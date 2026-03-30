/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.client;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import net.minecraft.util.Mth;
import net.xmx.velthoric.math.VxOperations;
import org.jetbrains.annotations.Nullable;

/**
 * Handles the interpolation and extrapolation of physics body states for smooth rendering.
 * This class calculates the visual state of a body at a specific render time based on
 * the two most recent states received from the server.
 *
 * @author xI-Mx-Ix
 */
public class VxClientBodyInterpolator {

    /**
     * The maximum time in seconds to extrapolate a body's position forward if no new state has arrived.
     */
    private static final float MAX_EXTRAPOLATION_SECONDS = 1.25f;

    /**
     * The minimum squared velocity required to perform extrapolation, to prevent jitter when stopping.
     */
    private static final float EXTRAPOLATION_VELOCITY_THRESHOLD_SQ = 0.0001f;

    // Temporary quaternion objects to avoid allocations during calculations.
    private final Quat tempFromRot = new Quat();
    private final Quat tempToRot = new Quat();
    private final Quat tempRenderRot = new Quat();

    /**
     * Updates the interpolation target states for all bodies in the data store.
     * This is called once per client tick.
     *
     * @param store           The data store containing all body states.
     * @param renderTimestamp The target time to calculate the render state for.
     */
    public void updateInterpolationTargets(VxClientBodyDataStore store, long renderTimestamp) {
        VxClientBodyDataContainer c = store.clientCurrent();
        final int capacity = c.getCapacity();

        // Direct array iteration avoids iterator allocation and pointer chasing
        for (int i = 0; i < capacity; i++) {

            // Fast-fail check: Skip slots that are not initialized.
            // This boolean check is faster than a Map.get() or Set iteration.
            if (!c.render_isInitialized[i]) {
                continue;
            }

            // Ensure we have a valid target state from the server before interpolating
            if (c.state1_timestamp[i] == 0) {
                continue;
            }

            // 1. Backup the current render state to 'prev' arrays for frame interpolation.
            c.prev_posX[i] = c.posX[i];
            c.prev_posY[i] = c.posY[i];
            c.prev_posZ[i] = c.posZ[i];
            c.prev_rotX[i] = c.rotX[i];
            c.prev_rotY[i] = c.rotY[i];
            c.prev_rotZ[i] = c.rotZ[i];
            c.prev_rotW[i] = c.rotW[i];

            // 2. Backup vertex data if it exists (Soft Bodies)
            float[] currentVerts = c.vertexData[i];
            if (currentVerts != null) {
                float[] prevVerts = c.prev_vertexData[i];
                if (prevVerts == null || prevVerts.length != currentVerts.length) {
                    prevVerts = new float[currentVerts.length];
                    c.prev_vertexData[i] = prevVerts;
                }
                System.arraycopy(currentVerts, 0, prevVerts, 0, currentVerts.length);
            } else {
                c.prev_vertexData[i] = null;
            }

            // 3. Calculate the new target state
            calculateInterpolatedState(c, i, renderTimestamp);
        }
    }

    /**
     * Calculates the interpolated or extrapolated state for a single body.
     * The result is written to the base arrays ({@code posX}, {@code rotX}, etc.).
     *
     * @param c               The body data container.
     * @param i               The index of the body in the store.
     * @param renderTimestamp The target render time.
     */
    private void calculateInterpolatedState(VxClientBodyDataContainer c, int i, long renderTimestamp) {
        // If the body is inactive on the server, just snap to its final state.
        if (!c.state1_isActive[i]) {
            setRenderStateToLatest(c, i);
            return;
        }

        long fromTime = c.state0_timestamp[i];
        long toTime = c.state1_timestamp[i];

        // If we don't have valid history, snap to latest.
        if (fromTime == 0 || toTime <= fromTime) {
            setRenderStateToLatest(c, i);
            return;
        }

        long timeDiff = toTime - fromTime;
        double alpha = (double) (renderTimestamp - fromTime) / timeDiff;

        if (alpha > 1.0) {
            // --- Extrapolation Case ---
            // The render time is past the latest known state.
            double extrapolationTime = (double) (renderTimestamp - toTime) / 1_000_000_000.0;
            float velX = c.state1_velX[i];
            float velY = c.state1_velY[i];
            float velZ = c.state1_velZ[i];
            float velSq = velX * velX + velY * velY + velZ * velZ;

            // Only extrapolate if the time is within limits and the body has significant velocity.
            // This prevents overshooting when the body is meant to be stopping.
            if (extrapolationTime < MAX_EXTRAPOLATION_SECONDS && velSq > EXTRAPOLATION_VELOCITY_THRESHOLD_SQ) {
                c.posX[i] = c.state1_posX[i] + velX * extrapolationTime;
                c.posY[i] = c.state1_posY[i] + velY * extrapolationTime;
                c.posZ[i] = c.state1_posZ[i] + velZ * extrapolationTime;
            } else {
                // If extrapolating too far or velocity is negligible, clamp to the last known position.
                c.posX[i] = c.state1_posX[i];
                c.posY[i] = c.state1_posY[i];
                c.posZ[i] = c.state1_posZ[i];
            }
            // Do not extrapolate rotation, as it can be unstable. Just use the latest.
            c.rotX[i] = c.state1_rotX[i];
            c.rotY[i] = c.state1_rotY[i];
            c.rotZ[i] = c.state1_rotZ[i];
            c.rotW[i] = c.state1_rotW[i];
            c.vertexData[i] = c.state1_vertexData[i];
            return;
        }

        // Clamp alpha to prevent interpolating backwards.
        alpha = Math.max(0.0, alpha);

        // --- Interpolation Case ---
        // The render time is between the two known states.

        // Position: Linear interpolation (Lerp)
        c.posX[i] = c.state0_posX[i] + alpha * (c.state1_posX[i] - c.state0_posX[i]);
        c.posY[i] = c.state0_posY[i] + alpha * (c.state1_posY[i] - c.state0_posY[i]);
        c.posZ[i] = c.state0_posZ[i] + alpha * (c.state1_posZ[i] - c.state0_posZ[i]);

        // Rotation: Spherical Linear Interpolation (Slerp)
        float alphaF = (float) alpha;
        tempFromRot.set(c.state0_rotX[i], c.state0_rotY[i], c.state0_rotZ[i], c.state0_rotW[i]);
        tempToRot.set(c.state1_rotX[i], c.state1_rotY[i], c.state1_rotZ[i], c.state1_rotW[i]);
        VxOperations.slerp(tempFromRot, tempToRot, alphaF, tempRenderRot);

        c.rotX[i] = tempRenderRot.getX();
        c.rotY[i] = tempRenderRot.getY();
        c.rotZ[i] = tempRenderRot.getZ();
        c.rotW[i] = tempRenderRot.getW();

        // Vertex Data: Linear Interpolation
        float[] fromVerts = c.state0_vertexData[i];
        float[] toVerts = c.state1_vertexData[i];
        if (fromVerts != null && toVerts != null && fromVerts.length == toVerts.length) {
            if (c.vertexData[i] == null || c.vertexData[i].length != toVerts.length) {
                c.vertexData[i] = new float[toVerts.length];
            }
            for (int j = 0; j < toVerts.length; j++) {
                c.vertexData[i][j] = Mth.lerp(alphaF, fromVerts[j], toVerts[j]);
            }
        } else {
            c.vertexData[i] = toVerts != null ? toVerts : fromVerts;
        }
    }

    /**
     * Sets the render state directly to the latest known state (state1).
     * Used when interpolation is not possible or desired.
     *
     * @param c The body data container.
     * @param i The index of the body.
     */
    private void setRenderStateToLatest(VxClientBodyDataContainer c, int i) {
        c.posX[i] = c.state1_posX[i];
        c.posY[i] = c.state1_posY[i];
        c.posZ[i] = c.state1_posZ[i];
        c.rotX[i] = c.state1_rotX[i];
        c.rotY[i] = c.state1_rotY[i];
        c.rotZ[i] = c.state1_rotZ[i];
        c.rotW[i] = c.state1_rotW[i];
        c.vertexData[i] = c.state1_vertexData[i] != null ? c.state1_vertexData[i] : c.state0_vertexData[i];
    }

    /**
     * Calculates the final, interpolated state for rendering within a single frame.
     * This interpolates between the previous frame's render state ({@code prev_}) and the
     * current frame's target render state ({@code posX/Y/Z}), using the partial tick as the alpha.
     *
     * @param store        The data store.
     * @param i            The index of the body.
     * @param partialTicks The fraction of a tick that has passed since the last full tick.
     * @param outPos       The RVec3 object to store the resulting position in.
     * @param outRot       The Quat object to store the resulting rotation in.
     */
    public void interpolateFrame(VxClientBodyDataStore store, int i, float partialTicks, RVec3 outPos, Quat outRot) {
        VxClientBodyDataContainer c = store.clientCurrent();
        interpolatePosition(c, i, partialTicks, outPos);
        interpolateRotation(c, i, partialTicks, outRot);
    }

    /**
     * Calculates the final, interpolated position for rendering within a single frame.
     *
     * @param store        The data store.
     * @param i            The index of the body.
     * @param partialTicks The fraction of a tick that has passed since the last full tick.
     * @param outPos       The RVec3 object to store the resulting position in.
     */
    public void interpolatePosition(VxClientBodyDataStore store, int i, float partialTicks, RVec3 outPos) {
        VxClientBodyDataContainer c = store.clientCurrent();
        interpolatePosition(c, i, partialTicks, outPos);
    }

    /**
     * Performs position interpolation between the current and previous render state.
     *
     * @param c            The data container.
     * @param i            The index of the body.
     * @param partialTicks The partial tick alpha value.
     * @param outPos       The output vector.
     */
    private void interpolatePosition(VxClientBodyDataContainer c, int i, float partialTicks, RVec3 outPos) {
        // Use double precision for the position interpolation to maintain accuracy
        double x = c.prev_posX[i] + (double) partialTicks * (c.posX[i] - c.prev_posX[i]);
        double y = c.prev_posY[i] + (double) partialTicks * (c.posY[i] - c.prev_posY[i]);
        double z = c.prev_posZ[i] + (double) partialTicks * (c.posZ[i] - c.prev_posZ[i]);
        outPos.set(x, y, z);
    }

    /**
     * Calculates the final, interpolated rotation for rendering within a single frame.
     *
     * @param store        The data store.
     * @param i            The index of the body.
     * @param partialTicks The fraction of a tick that has passed since the last full tick.
     * @param outRot       The Quat object to store the resulting rotation in.
     */
    public void interpolateRotation(VxClientBodyDataStore store, int i, float partialTicks, Quat outRot) {
        VxClientBodyDataContainer c = store.clientCurrent();
        interpolateRotation(c, i, partialTicks, outRot);
    }

    /**
     * Performs rotation interpolation between the current and previous render state.
     *
     * @param c            The data container.
     * @param i            The index of the body.
     * @param partialTicks The partial tick alpha value.
     * @param outRot       The output quaternion.
     */
    private void interpolateRotation(VxClientBodyDataContainer c, int i, float partialTicks, Quat outRot) {
        tempFromRot.set(c.prev_rotX[i], c.prev_rotY[i], c.prev_rotZ[i], c.prev_rotW[i]);
        tempToRot.set(c.rotX[i], c.rotY[i], c.rotZ[i], c.rotW[i]);
        VxOperations.slerp(tempFromRot, tempToRot, partialTicks, outRot);
    }

    /**
     * Calculates the final, interpolated vertex data for a soft body for the current frame.
     *
     * @param store        The data store.
     * @param i            The index of the body.
     * @param partialTicks The partial tick value.
     * @return An array of interpolated vertex positions, or null if not applicable.
     */
    public float @Nullable [] getInterpolatedVertexData(VxClientBodyDataStore store, int i, float partialTicks) {
        VxClientBodyDataContainer c = store.clientCurrent();
        float[] prevVerts = c.prev_vertexData[i];
        float[] currVerts = c.vertexData[i];

        if (currVerts == null) {
            return null;
        }

        if (prevVerts == null || prevVerts.length != currVerts.length) {
            return currVerts;
        }

        float[] outVerts = new float[currVerts.length];
        for (int j = 0; j < currVerts.length; j++) {
            outVerts[j] = Mth.lerp(partialTicks, prevVerts[j], currVerts[j]);
        }
        return outVerts;
    }
}