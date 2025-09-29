/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import net.minecraft.util.Mth;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.client.VxRenderState;
import net.xmx.velthoric.physics.object.client.body.VxClientRigidBody;
import net.xmx.velthoric.physics.vehicle.wheel.WheelRenderState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * @author xI-Mx-Ix
 */
public abstract class VxClientVehicle extends VxClientRigidBody {

    private WheelRenderState[] prevWheelStates;
    private WheelRenderState[] targetWheelStates;
    protected final List<WheelRenderState> interpolatedWheelStates;

    protected VxClientVehicle(UUID id, VxClientObjectManager manager, int dataStoreIndex, EBodyType objectType) {
        super(id, manager, dataStoreIndex, objectType);
        this.prevWheelStates = new WheelRenderState[0];
        this.targetWheelStates = new WheelRenderState[0];
        this.interpolatedWheelStates = new ArrayList<>();
    }

    @Override
    protected void defineSyncData() {
        // Define synchronized data accessors that mirror the server-side VxVehicle
        this.synchronizedData.define(VxVehicle.DATA_CHASSIS_HALF_EXTENTS, new Vec3());
        this.synchronizedData.define(VxVehicle.DATA_WHEELS_SETTINGS, Collections.emptyList());
    }

    /**
     * Updates the target state for a specific wheel. This is called by the network layer
     * when wheel state updates are received.
     *
     * @param wheelIndex The index of the wheel to update.
     * @param rotation The new rotation angle.
     * @param steer The new steer angle.
     * @param suspension The new suspension length.
     */
    public void updateWheelState(int wheelIndex, float rotation, float steer, float suspension) {
        if (wheelIndex < 0 || wheelIndex >= targetWheelStates.length) return;
        this.prevWheelStates[wheelIndex] = this.targetWheelStates[wheelIndex];
        this.targetWheelStates[wheelIndex] = new WheelRenderState(rotation, steer, suspension);
    }

    /**
     * Calculates the interpolated transformation for the vehicle body and its wheels for smooth rendering.
     */
    @Override
    public void calculateRenderState(float partialTicks, VxRenderState outState, RVec3 tempPos, Quat tempRot) {
        super.calculateRenderState(partialTicks, outState, tempPos, tempRot);

        List<WheelSettingsWv> currentWheelSettings = getWheelSettings();
        int wheelCount = currentWheelSettings.size();

        // Resize wheel state arrays if the number of wheels has changed
        if (wheelCount != targetWheelStates.length) {
            this.prevWheelStates = new WheelRenderState[wheelCount];
            this.targetWheelStates = new WheelRenderState[wheelCount];
            this.interpolatedWheelStates.clear();
            WheelRenderState initial = new WheelRenderState(0, 0, 0);
            for (int i = 0; i < wheelCount; i++) {
                this.prevWheelStates[i] = initial;
                this.targetWheelStates[i] = initial;
                this.interpolatedWheelStates.add(initial);
            }
        }

        if (targetWheelStates.length > 0) {
            for (int i = 0; i < targetWheelStates.length; i++) {
                WheelRenderState prev = prevWheelStates[i];
                WheelRenderState target = targetWheelStates[i];

                if (prev == null || target == null) continue;

                float rot = Mth.lerp(partialTicks, prev.rotationAngle(), target.rotationAngle());
                float steer = Mth.lerp(partialTicks, prev.steerAngle(), target.steerAngle());
                float susp = Mth.lerp(partialTicks, prev.suspensionLength(), target.suspensionLength());

                interpolatedWheelStates.set(i, new WheelRenderState(rot, steer, susp));
            }
        }
    }

    /**
     * @return The chassis half extents, retrieved from synchronized data.
     */
    public Vec3 getChassisHalfExtents() {
        return this.getSyncData(VxVehicle.DATA_CHASSIS_HALF_EXTENTS);
    }

    /**
     * @return The list of wheel settings, retrieved from synchronized data.
     */
    public List<WheelSettingsWv> getWheelSettings() {
        return this.getSyncData(VxVehicle.DATA_WHEELS_SETTINGS);
    }

    /**
     * @return The list of interpolated wheel states for rendering.
     */
    public List<WheelRenderState> getInterpolatedWheelStates() {
        return interpolatedWheelStates;
    }
}