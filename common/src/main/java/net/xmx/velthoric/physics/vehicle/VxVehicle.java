/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle;

import com.github.stephengold.joltjni.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Mth;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.mounting.VxMountable;
import net.xmx.velthoric.physics.body.manager.VxJoltBridge;
import net.xmx.velthoric.physics.body.registry.VxBodyType;
import net.xmx.velthoric.physics.body.client.VxRenderState;
import net.xmx.velthoric.physics.body.manager.VxRemovalReason;
import net.xmx.velthoric.physics.body.sync.VxDataAccessor;
import net.xmx.velthoric.physics.body.sync.VxDataSerializers;
import net.xmx.velthoric.physics.body.type.VxRigidBody;
import net.xmx.velthoric.physics.vehicle.wheel.VxWheel;
import net.xmx.velthoric.physics.vehicle.wheel.VxWheelRenderState;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Abstract base class for all vehicle physics bodies. This class manages the
 * Jolt VehicleConstraint and wheels, and on the client side, handles wheel state
 * interpolation for smooth rendering.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxVehicle extends VxRigidBody implements VxMountable {

    public static final VxDataAccessor<List<WheelSettingsWv>> DATA_WHEELS_SETTINGS = VxDataAccessor.create(VxVehicle.class, VxDataSerializers.WHEEL_SETTINGS_LIST);

    // --- Server-Side Fields ---
    protected List<VxWheel> wheels = Collections.emptyList();
    protected VehicleConstraintSettings constraintSettings;
    protected VehicleCollisionTester collisionTester;
    protected VehicleConstraint constraint;
    private boolean wheelsDirty = false;

    // --- Client-Side Fields ---
    @Environment(EnvType.CLIENT)
    private VxWheelRenderState[] prevWheelStates;
    @Environment(EnvType.CLIENT)
    private VxWheelRenderState[] targetWheelStates;
    @Environment(EnvType.CLIENT)
    protected List<VxWheelRenderState> interpolatedWheelStates;


    /**
     * Server-side constructor.
     */
    protected VxVehicle(VxBodyType<? extends VxVehicle> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
    }

    /**
     * Client-side constructor.
     */
    @Environment(EnvType.CLIENT)
    protected VxVehicle(VxBodyType<? extends VxVehicle> type, UUID id) {
        super(type, id);
        this.prevWheelStates = new VxWheelRenderState[0];
        this.targetWheelStates = new VxWheelRenderState[0];
        this.interpolatedWheelStates = new ArrayList<>();
    }

    @Override
    protected void defineSyncData() {
        this.synchronizedData.define(DATA_WHEELS_SETTINGS, Collections.emptyList());
    }

    @Override
    public void onBodyAdded(VxPhysicsWorld world) {
        super.onBodyAdded(world);

        Body body = VxJoltBridge.INSTANCE.getJoltBody(world, this.getBodyId());
        if (body == null || constraintSettings == null) {
            throw new IllegalStateException("Vehicle cannot be initialized without a body or constraint settings.");
        }

        this.constraint = new VehicleConstraint(body, constraintSettings);

        if (this.collisionTester == null) {
            this.collisionTester = new VehicleCollisionTesterCastCylinder(body.getObjectLayer());
        }
        this.constraint.setVehicleCollisionTester(collisionTester);

        world.getPhysicsSystem().addConstraint(constraint);
        world.getPhysicsSystem().addStepListener(constraint.getStepListener());
    }

    @Override
    public void onBodyRemoved(VxPhysicsWorld world, VxRemovalReason reason) {
        super.onBodyRemoved(world, reason);
        if (constraint != null) {
            world.getPhysicsSystem().removeStepListener(constraint.getStepListener());
            world.getPhysicsSystem().removeConstraint(constraint);
            constraint.close();
            constraint = null;
        }
        if (collisionTester != null) {
            collisionTester.close();
            collisionTester = null;
        }
    }

    @Override
    public void physicsTick(VxPhysicsWorld world) {
        super.physicsTick(world);
        if (constraint != null && !wheels.isEmpty()) {
            updateWheelStates();
        }
    }

    private void updateWheelStates() {
        boolean changed = false;
        for (int i = 0; i < wheels.size(); i++) {
            VxWheel vxWheel = wheels.get(i);
            Wheel joltWheel = constraint.getWheel(i);

            vxWheel.setRotationAngle(joltWheel.getRotationAngle());
            vxWheel.setSteerAngle(joltWheel.getSteerAngle());
            vxWheel.setSuspensionLength(joltWheel.getSuspensionLength());
            vxWheel.setHasContact(joltWheel.hasContact());
            changed = true;
        }
        if (changed) {
            markWheelsDirty();
        }
    }

    @Override
    public void writePersistenceData(VxByteBuf buf) {
        DATA_WHEELS_SETTINGS.getSerializer().write(buf, this.getSyncData(DATA_WHEELS_SETTINGS));
    }

    @Override
    public void readPersistenceData(VxByteBuf buf) {
        List<WheelSettingsWv> newSettings = DATA_WHEELS_SETTINGS.getSerializer().read(buf);
        if (newSettings.size() == this.wheels.size()) {
            List<WheelSettingsWv> wheelSettingsList = new ArrayList<>(wheels.size());
            for (int i = 0; i < wheels.size(); i++) {
                WheelSettingsWv settings = newSettings.get(i);
                this.wheels.get(i).setSettings(settings);
                wheelSettingsList.add(settings);
            }
            this.setSyncData(DATA_WHEELS_SETTINGS, wheelSettingsList);
        }
    }

    /**
     * Updates the target state for a specific wheel. This is called by the network layer
     * when wheel state updates are received. Client-side only.
     *
     * @param wheelIndex The index of the wheel to update.
     * @param rotation The new rotation angle.
     * @param steer The new steer angle.
     * @param suspension The new suspension length.
     */
    @Environment(EnvType.CLIENT)
    public void updateWheelState(int wheelIndex, float rotation, float steer, float suspension) {
        if (wheelIndex < 0 || wheelIndex >= targetWheelStates.length) return;
        this.prevWheelStates[wheelIndex] = this.targetWheelStates[wheelIndex];
        this.targetWheelStates[wheelIndex] = new VxWheelRenderState(rotation, steer, suspension);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void calculateRenderState(float partialTicks, VxRenderState outState, RVec3 tempPos, Quat tempRot) {
        super.calculateRenderState(partialTicks, outState, tempPos, tempRot);

        List<WheelSettingsWv> currentWheelSettings = getWheelSettings();
        int wheelCount = currentWheelSettings.size();

        if (wheelCount != targetWheelStates.length) {
            this.prevWheelStates = new VxWheelRenderState[wheelCount];
            this.targetWheelStates = new VxWheelRenderState[wheelCount];
            this.interpolatedWheelStates.clear();
            VxWheelRenderState initial = new VxWheelRenderState(0, 0, 0);
            for (int i = 0; i < wheelCount; i++) {
                this.prevWheelStates[i] = initial;
                this.targetWheelStates[i] = initial;
                this.interpolatedWheelStates.add(initial);
            }
        }

        if (targetWheelStates.length > 0) {
            for (int i = 0; i < targetWheelStates.length; i++) {
                VxWheelRenderState prev = prevWheelStates[i];
                VxWheelRenderState target = targetWheelStates[i];

                if (prev == null || target == null) continue;

                float rot = Mth.lerp(partialTicks, prev.rotationAngle(), target.rotationAngle());
                float steer = Mth.lerp(partialTicks, prev.steerAngle(), target.steerAngle());
                float susp = Mth.lerp(partialTicks, prev.suspensionLength(), target.suspensionLength());

                interpolatedWheelStates.set(i, new VxWheelRenderState(rot, steer, susp));
            }
        }
    }

    @Environment(EnvType.CLIENT)
    public List<WheelSettingsWv> getWheelSettings() {
        return this.getSyncData(DATA_WHEELS_SETTINGS);
    }

    @Environment(EnvType.CLIENT)
    public List<VxWheelRenderState> getInterpolatedWheelStates() {
        return interpolatedWheelStates;
    }

    public void markWheelsDirty() {
        this.wheelsDirty = true;
    }

    public boolean areWheelsDirty() {
        return wheelsDirty;
    }

    public void clearWheelsDirty() {
        this.wheelsDirty = false;
    }

    public List<VxWheel> getWheels() {
        return wheels;
    }
}