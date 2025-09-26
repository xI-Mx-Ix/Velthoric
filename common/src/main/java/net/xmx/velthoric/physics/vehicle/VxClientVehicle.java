/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.StreamInWrapper;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.WheelSettingsWv;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import com.github.stephengold.joltjni.std.StringStream;
import net.minecraft.util.Mth;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.client.VxRenderState;
import net.xmx.velthoric.physics.object.client.body.VxClientRigidBody;
import net.xmx.velthoric.physics.vehicle.wheel.WheelRenderState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class VxClientVehicle extends VxClientRigidBody {

    protected Vec3 chassisHalfExtents;
    protected final List<WheelSettingsWv> wheelSettings;

    private WheelRenderState[] prevWheelStates;
    private WheelRenderState[] targetWheelStates;
    protected final List<WheelRenderState> interpolatedWheelStates;

    protected VxClientVehicle(UUID id, VxClientObjectManager manager, int dataStoreIndex, EBodyType objectType) {
        super(id, manager, dataStoreIndex, objectType);
        this.chassisHalfExtents = new Vec3();
        this.wheelSettings = new ArrayList<>();
        this.prevWheelStates = new WheelRenderState[0];
        this.targetWheelStates = new WheelRenderState[0];
        this.interpolatedWheelStates = new ArrayList<>();
    }

    @Override
    public void readSyncData(VxByteBuf buf) {
        this.chassisHalfExtents.set(buf.readFloat(), buf.readFloat(), buf.readFloat());
        int wheelCount = buf.readVarInt();

        if (this.wheelSettings.size() != wheelCount) {
            this.wheelSettings.clear();
            for (int i = 0; i < wheelCount; i++) {
                this.wheelSettings.add(new WheelSettingsWv());
            }
        }

        for (WheelSettingsWv setting : this.wheelSettings) {
            byte[] bytes = buf.readByteArray();
            try (StringStream stream = new StringStream(new String(bytes))) {
                setting.restoreBinaryState(new StreamInWrapper(stream));
            }
        }

        if (this.prevWheelStates.length != wheelCount) {
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
    }

    public void updateWheelState(int wheelIndex, float rotation, float steer, float suspension) {
        if (wheelIndex < 0 || wheelIndex >= targetWheelStates.length) return;
        this.prevWheelStates[wheelIndex] = this.targetWheelStates[wheelIndex];
        this.targetWheelStates[wheelIndex] = new WheelRenderState(rotation, steer, suspension);
    }

    @Override
    public void calculateRenderState(float partialTicks, VxRenderState outState, RVec3 tempPos, Quat tempRot) {
        super.calculateRenderState(partialTicks, outState, tempPos, tempRot);

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

    public Vec3 getChassisHalfExtents() {
        return chassisHalfExtents;
    }

    public List<WheelSettingsWv> getWheelSettings() {
        return wheelSettings;
    }

    public List<WheelRenderState> getInterpolatedWheelStates() {
        return interpolatedWheelStates;
    }
}