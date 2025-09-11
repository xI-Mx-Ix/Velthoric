/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.client;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import net.minecraft.util.Mth;
import net.xmx.velthoric.math.VxOperations;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Handles the interpolation and extrapolation of physics object states for smooth rendering.
 * This class calculates the visual state of an object at a specific render time based on
 * the two most recent states received from the server.
 *
 * @author xI-Mx-Ix
 */
public class VxClientObjectInterpolator {

    /** The maximum time in seconds to extrapolate an object's position forward if no new state has arrived. */
    private static final float MAX_EXTRAPOLATION_SECONDS = 0.25f;

    // Temporary quaternion objects to avoid allocations during calculations.
    private final Quat tempFromRot = new Quat();
    private final Quat tempToRot = new Quat();
    private final Quat tempRenderRot = new Quat();

    /**
     * Updates the interpolation target states for all objects in the data store.
     * This is called once per client tick.
     *
     * @param store           The data store containing all object states.
     * @param renderTimestamp The target time to calculate the render state for.
     */
    public void updateInterpolationTargets(VxClientObjectDataStore store, long renderTimestamp) {
        for (UUID id : store.getAllObjectIds()) {
            Integer i = store.getIndexForId(id);
            if (i == null) continue;

            // Skip objects that haven't received any updates yet.
            if (store.state1_timestamp[i] == 0) continue;

            // Shift the current render state to the "previous" render state buffer.
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

            // Calculate the new target render state.
            calculateInterpolatedState(store, i, renderTimestamp);

            // On the very first update, initialize the "previous" state to the current state to prevent a visual jump.
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
     * Calculates the interpolated or extrapolated state for a single object.
     *
     * @param store           The data store.
     * @param i               The index of the object in the store.
     * @param renderTimestamp The target render time.
     */
    private void calculateInterpolatedState(VxClientObjectDataStore store, int i, long renderTimestamp) {
        // If the object is inactive on the server, just snap to its final state.
        if (!store.state1_isActive[i]) {
            setRenderStateToLatest(store, i);
            return;
        }

        long fromTime = store.state0_timestamp[i];
        long toTime = store.state1_timestamp[i];

        // If timestamps are invalid or not in order, snap to the latest state.
        if (fromTime == 0 || toTime <= fromTime) {
            setRenderStateToLatest(store, i);
            return;
        }

        long timeDiff = toTime - fromTime;
        // Alpha is the interpolation factor, from 0.0 (at fromTime) to 1.0 (at toTime).
        float alpha = (float) (renderTimestamp - fromTime) / timeDiff;

        if (alpha > 1.0f) {
            // Extrapolation: The render time is past the latest known state.
            float extrapolationTime = (float) (renderTimestamp - toTime) / 1_000_000_000.0f;

            if (extrapolationTime < MAX_EXTRAPOLATION_SECONDS) {
                // Predict future position based on the last known velocity.
                store.render_posX[i] = store.state1_posX[i] + store.state1_velX[i] * extrapolationTime;
                store.render_posY[i] = store.state1_posY[i] + store.state1_velY[i] * extrapolationTime;
                store.render_posZ[i] = store.state1_posZ[i] + store.state1_velZ[i] * extrapolationTime;
            } else {
                // If extrapolating too far, just clamp to the last known position.
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
        if (alpha < 0.0f) {
            alpha = 0.0f;
        }

        // Interpolation: The render time is between the two known states.
        // Linearly interpolate position.
        store.render_posX[i] = Mth.lerp(alpha, store.state0_posX[i], store.state1_posX[i]);
        store.render_posY[i] = Mth.lerp(alpha, store.state0_posY[i], store.state1_posY[i]);
        store.render_posZ[i] = Mth.lerp(alpha, store.state0_posZ[i], store.state1_posZ[i]);

        // Spherically interpolate rotation for correct, shortest-path rotation.
        tempFromRot.set(store.state0_rotX[i], store.state0_rotY[i], store.state0_rotZ[i], store.state0_rotW[i]);
        tempToRot.set(store.state1_rotX[i], store.state1_rotY[i], store.state1_rotZ[i], store.state1_rotW[i]);
        VxOperations.slerp(tempFromRot, tempToRot, alpha, tempRenderRot);
        store.render_rotX[i] = tempRenderRot.getX();
        store.render_rotY[i] = tempRenderRot.getY();
        store.render_rotZ[i] = tempRenderRot.getZ();
        store.render_rotW[i] = tempRenderRot.getW();

        // Linearly interpolate soft body vertex data.
        float[] fromVerts = store.state0_vertexData[i];
        float[] toVerts = store.state1_vertexData[i];
        if (fromVerts != null && toVerts != null && fromVerts.length == toVerts.length) {
            if (store.render_vertexData[i] == null || store.render_vertexData[i].length != toVerts.length) {
                store.render_vertexData[i] = new float[toVerts.length];
            }
            for (int j = 0; j < toVerts.length; j++) {
                store.render_vertexData[i][j] = Mth.lerp(alpha, fromVerts[j], toVerts[j]);
            }
        } else {
            // If one of the vertex arrays is missing, just use the one that exists.
            store.render_vertexData[i] = toVerts != null ? toVerts : fromVerts;
        }
    }

    /**
     * Sets the render state directly to the latest known state (state1).
     * Used when interpolation is not possible or desired.
     *
     * @param store The data store.
     * @param i     The index of the object.
     */
    private void setRenderStateToLatest(VxClientObjectDataStore store, int i) {
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
     * @param i            The index of the object.
     * @param partialTicks The fraction of a tick that has passed since the last full tick.
     * @param outPos       The RVec3 object to store the resulting position in.
     * @param outRot       The Quat object to store the resulting rotation in.
     */
    public void interpolateFrame(VxClientObjectDataStore store, int i, float partialTicks, RVec3 outPos, Quat outRot) {
        outPos.set(
                Mth.lerp(partialTicks, store.prev_posX[i], store.render_posX[i]),
                Mth.lerp(partialTicks, store.prev_posY[i], store.render_posY[i]),
                Mth.lerp(partialTicks, store.prev_posZ[i], store.render_posZ[i])
        );

        tempFromRot.set(store.prev_rotX[i], store.prev_rotY[i], store.prev_rotZ[i], store.prev_rotW[i]);
        tempToRot.set(store.render_rotX[i], store.render_rotY[i], store.render_rotZ[i], store.render_rotW[i]);
        VxOperations.slerp(tempFromRot, tempToRot, partialTicks, outRot);
    }

    /**
     * Calculates the final, interpolated vertex data for a soft body for the current frame.
     *
     * @param store        The data store.
     * @param i            The index of the object.
     * @param partialTicks The partial tick value.
     * @return An array of interpolated vertex positions, or null if not applicable.
     */
    public float @Nullable [] getInterpolatedVertexData(VxClientObjectDataStore store, int i, float partialTicks) {
        float[] prevVerts = store.prev_vertexData[i];
        float[] currVerts = store.render_vertexData[i];

        if (currVerts == null) {
            return null;
        }

        // If there's no previous data, we can't interpolate, so return the current data.
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