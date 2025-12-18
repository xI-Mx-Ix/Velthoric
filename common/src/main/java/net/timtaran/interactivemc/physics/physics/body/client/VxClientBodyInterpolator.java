/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.physics.body.client;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import net.minecraft.util.Mth;
import net.timtaran.interactivemc.physics.math.VxOperations;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Handles the interpolation and extrapolation of physics body states for smooth rendering.
 * This class calculates the visual state of a body at a specific render time based on
 * the two most recent states received from the server.
 *
 * @author xI-Mx-Ix
 */
public class VxClientBodyInterpolator {

    /** The maximum time in seconds to extrapolate a body's position forward if no new state has arrived. */
    private static final float MAX_EXTRAPOLATION_SECONDS = 1.25f;
    /** The minimum squared velocity required to perform extrapolation, to prevent jitter when stopping. */
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
        for (UUID id : store.getAllPhysicsIds()) {
            Integer i = store.getIndexForId(id);
            if (i == null) continue;

            if (store.state1_timestamp[i] == 0) continue;

            store.prev_posX[i] = store.render_posX[i];
            store.prev_posY[i] = store.render_posY[i];
            store.prev_posZ[i] = store.render_posZ[i];
            store.prev_rotX[i] = store.render_rotX[i];
            store.prev_rotY[i] = store.render_rotY[i];
            store.prev_rotZ[i] = store.render_rotZ[i];
            store.prev_rotW[i] = store.render_rotW[i];

            if (store.render_vertexData[i] != null) {
                if (store.prev_vertexData[i] == null || store.prev_vertexData[i].length != store.render_vertexData[i].length) {
                    store.prev_vertexData[i] = new float[store.render_vertexData[i].length];
                }
                System.arraycopy(store.render_vertexData[i], 0, store.prev_vertexData[i], 0, store.render_vertexData[i].length);
            } else {
                store.prev_vertexData[i] = null;
            }

            calculateInterpolatedState(store, i, renderTimestamp);

            if (!store.render_isInitialized[i]) {
                store.prev_posX[i] = store.render_posX[i];
                store.prev_posY[i] = store.render_posY[i];
                store.prev_posZ[i] = store.render_posZ[i];
                store.prev_rotX[i] = store.render_rotX[i];
                store.prev_rotY[i] = store.render_rotY[i];
                store.prev_rotZ[i] = store.render_rotZ[i];
                store.prev_rotW[i] = store.render_rotW[i];

                if (store.render_vertexData[i] != null) {
                    if (store.prev_vertexData[i] == null || store.prev_vertexData[i].length != store.render_vertexData[i].length) {
                        store.prev_vertexData[i] = new float[store.render_vertexData[i].length];
                    }
                    System.arraycopy(store.render_vertexData[i], 0, store.prev_vertexData[i], 0, store.render_vertexData[i].length);
                }
                store.render_isInitialized[i] = true;
            }
        }
    }

    /**
     * Calculates the interpolated or extrapolated state for a single body.
     *
     * @param store           The data store.
     * @param i               The index of the body in the store.
     * @param renderTimestamp The target render time.
     */
    private void calculateInterpolatedState(VxClientBodyDataStore store, int i, long renderTimestamp) {
        // If the body is inactive on the server, just snap to its final state.
        if (!store.state1_isActive[i]) {
            setRenderStateToLatest(store, i);
            return;
        }

        long fromTime = store.state0_timestamp[i];
        long toTime = store.state1_timestamp[i];

        if (fromTime == 0 || toTime <= fromTime) {
            setRenderStateToLatest(store, i);
            return;
        }

        long timeDiff = toTime - fromTime;
        double alpha = (double) (renderTimestamp - fromTime) / timeDiff;

        if (alpha > 1.0) {
            // Extrapolation: The render time is past the latest known state.
            double extrapolationTime = (double) (renderTimestamp - toTime) / 1_000_000_000.0;
            float velX = store.state1_velX[i];
            float velY = store.state1_velY[i];
            float velZ = store.state1_velZ[i];
            float velSq = velX * velX + velY * velY + velZ * velZ;

            // Only extrapolate if the time is within limits and the body has significant velocity.
            // This prevents overshooting when the body is meant to be stopping.
            if (extrapolationTime < MAX_EXTRAPOLATION_SECONDS && velSq > EXTRAPOLATION_VELOCITY_THRESHOLD_SQ) {
                store.render_posX[i] = store.state1_posX[i] + velX * extrapolationTime;
                store.render_posY[i] = store.state1_posY[i] + velY * extrapolationTime;
                store.render_posZ[i] = store.state1_posZ[i] + velZ * extrapolationTime;
            } else {
                // If extrapolating too far or velocity is negligible, clamp to the last known position.
                store.render_posX[i] = store.state1_posX[i];
                store.render_posY[i] = store.state1_posY[i];
                store.render_posZ[i] = store.state1_posZ[i];
            }
            // Do not extrapolate rotation, as it can be unstable. Just use the latest.
            store.render_rotX[i] = store.state1_rotX[i];
            store.render_rotY[i] = store.state1_rotY[i];
            store.render_rotZ[i] = store.state1_rotZ[i];
            store.render_rotW[i] = store.state1_rotW[i];
            store.render_vertexData[i] = store.state1_vertexData[i];
            return;
        }

        // Clamp alpha to prevent interpolating backwards.
        alpha = Math.max(0.0, alpha);

        // Interpolation: The render time is between the two known states.
        // Using double precision for position interpolation.
        store.render_posX[i] = store.state0_posX[i] + alpha * (store.state1_posX[i] - store.state0_posX[i]);
        store.render_posY[i] = store.state0_posY[i] + alpha * (store.state1_posY[i] - store.state0_posY[i]);
        store.render_posZ[i] = store.state0_posZ[i] + alpha * (store.state1_posZ[i] - store.state0_posZ[i]);

        // Rotations use float math as per Jolt Quat implementation
        float alphaF = (float) alpha;
        tempFromRot.set(store.state0_rotX[i], store.state0_rotY[i], store.state0_rotZ[i], store.state0_rotW[i]);
        tempToRot.set(store.state1_rotX[i], store.state1_rotY[i], store.state1_rotZ[i], store.state1_rotW[i]);
        VxOperations.slerp(tempFromRot, tempToRot, alphaF, tempRenderRot);
        store.render_rotX[i] = tempRenderRot.getX();
        store.render_rotY[i] = tempRenderRot.getY();
        store.render_rotZ[i] = tempRenderRot.getZ();
        store.render_rotW[i] = tempRenderRot.getW();

        float[] fromVerts = store.state0_vertexData[i];
        float[] toVerts = store.state1_vertexData[i];
        if (fromVerts != null && toVerts != null && fromVerts.length == toVerts.length) {
            if (store.render_vertexData[i] == null || store.render_vertexData[i].length != toVerts.length) {
                store.render_vertexData[i] = new float[toVerts.length];
            }
            for (int j = 0; j < toVerts.length; j++) {
                store.render_vertexData[i][j] = Mth.lerp(alphaF, fromVerts[j], toVerts[j]);
            }
        } else {
            store.render_vertexData[i] = toVerts != null ? toVerts : fromVerts;
        }
    }

    /**
     * Sets the render state directly to the latest known state (state1).
     * Used when interpolation is not possible or desired.
     *
     * @param store The data store.
     * @param i     The index of the body.
     */
    private void setRenderStateToLatest(VxClientBodyDataStore store, int i) {
        store.render_posX[i] = store.state1_posX[i];
        store.render_posY[i] = store.state1_posY[i];
        store.render_posZ[i] = store.state1_posZ[i];
        store.render_rotX[i] = store.state1_rotX[i];
        store.render_rotY[i] = store.state1_rotY[i];
        store.render_rotZ[i] = store.state1_rotZ[i];
        store.render_rotW[i] = store.state1_rotW[i];
        store.render_vertexData[i] = store.state1_vertexData[i] != null ? store.state1_vertexData[i] : store.state0_vertexData[i];
    }

    /**
     * Calculates the final, interpolated state for rendering within a single frame.
     * This interpolates between the previous frame's render state and the current frame's
     * target render state, using the partial tick as the alpha.
     *
     * @param store        The data store.
     * @param i            The index of the body.
     * @param partialTicks The fraction of a tick that has passed since the last full tick.
     * @param outPos       The RVec3 object to store the resulting position in.
     * @param outRot       The Quat object to store the resulting rotation in.
     */
    public void interpolateFrame(VxClientBodyDataStore store, int i, float partialTicks, RVec3 outPos, Quat outRot) {
        interpolatePosition(store, i, partialTicks, outPos);
        interpolateRotation(store, i, partialTicks, outRot);
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
        // Use double precision for the position interpolation to maintain accuracy
        double x = store.prev_posX[i] + (double) partialTicks * (store.render_posX[i] - store.prev_posX[i]);
        double y = store.prev_posY[i] + (double) partialTicks * (store.render_posY[i] - store.prev_posY[i]);
        double z = store.prev_posZ[i] + (double) partialTicks * (store.render_posZ[i] - store.prev_posZ[i]);
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
        tempFromRot.set(store.prev_rotX[i], store.prev_rotY[i], store.prev_rotZ[i], store.prev_rotW[i]);
        tempToRot.set(store.render_rotX[i], store.render_rotY[i], store.render_rotZ[i], store.render_rotW[i]);
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
        float[] prevVerts = store.prev_vertexData[i];
        float[] currVerts = store.render_vertexData[i];

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