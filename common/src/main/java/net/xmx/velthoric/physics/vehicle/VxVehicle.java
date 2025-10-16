/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.VehicleCollisionTester;
import com.github.stephengold.joltjni.VehicleCollisionTesterCastCylinder;
import com.github.stephengold.joltjni.VehicleConstraint;
import com.github.stephengold.joltjni.VehicleConstraintSettings;
import com.github.stephengold.joltjni.Wheel;
import com.github.stephengold.joltjni.WheelSettingsWv;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Mth;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.body.client.VxRenderState;
import net.xmx.velthoric.physics.body.manager.VxBodyDataStore;
import net.xmx.velthoric.physics.body.manager.VxJoltBridge;
import net.xmx.velthoric.physics.body.manager.VxRemovalReason;
import net.xmx.velthoric.physics.body.registry.VxBodyType;
import net.xmx.velthoric.physics.body.sync.VxDataAccessor;
import net.xmx.velthoric.physics.body.sync.VxDataSerializers;
import net.xmx.velthoric.physics.body.type.VxRigidBody;
import net.xmx.velthoric.physics.mounting.VxMountable;
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
 * interpolation for smooth rendering. It also manages vehicle state synchronization like speed.
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
    private boolean vehicleStateDirty = false;

    /**
     * Represents the vehicle's speed in km/h.
     * On the server, this value is calculated directly from the physics simulation each tick.
     * On the client, this value is updated via network packets from the server to ensure synchronization.
     */
    protected float speedKmh = 0.0f;

    // --- Client-Side Fields ---
    @Environment(EnvType.CLIENT)
    private VxWheelRenderState[] prevWheelStates;
    @Environment(EnvType.CLIENT)
    private VxWheelRenderState[] targetWheelStates;
    @Environment(EnvType.CLIENT)
    protected List<VxWheelRenderState> interpolatedWheelStates;


    /**
     * Server-side constructor.
     *
     * @param type  The body type from the registry.
     * @param world The physics world this body belongs to.
     * @param id    The unique identifier for this body.
     */
    protected VxVehicle(VxBodyType<? extends VxVehicle> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
    }

    /**
     * Client-side constructor.
     *
     * @param type The body type from the registry.
     * @param id   The unique identifier for this body.
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
        if (constraint != null) {
            int index = this.getDataStoreIndex();
            if (index == -1) {
                return; // Cannot update if not properly registered in the data store.
            }

            VxBodyDataStore dataStore = this.physicsWorld.getBodyManager().getDataStore();

            // Calculate current speed in km/h from the body's linear velocity in the data store.
            float vx = dataStore.velX[index];
            float vy = dataStore.velY[index];
            float vz = dataStore.velZ[index];
            float speedMs = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
            this.speedKmh = speedMs * 3.6f;

            if (!wheels.isEmpty()) {
                updateWheelStates();
            }

            // If the vehicle is active in the physics simulation, mark its state as dirty
            // to ensure its state (like speed and wheel rotation) is sent to clients.
            if (dataStore.isActive[index]) {
                this.markVehicleStateDirty();
            }
        }
    }

    private void updateWheelStates() {
        for (int i = 0; i < wheels.size(); i++) {
            VxWheel vxWheel = wheels.get(i);
            Wheel joltWheel = constraint.getWheel(i);

            vxWheel.setRotationAngle(joltWheel.getRotationAngle());
            vxWheel.setSteerAngle(joltWheel.getSteerAngle());
            vxWheel.setSuspensionLength(joltWheel.getSuspensionLength());
            vxWheel.setHasContact(joltWheel.hasContact());
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
     * Updates the target vehicle state on the client. This is called by the network layer
     * when vehicle state updates are received from the server.
     *
     * @param speedKmh The new speed in kilometers per hour.
     */
    @Environment(EnvType.CLIENT)
    public void updateVehicleState(float speedKmh) {
        this.speedKmh = speedKmh;
    }

    /**
     * Updates the target state for a specific wheel on the client. This is called by the network layer
     * when wheel state updates are received.
     *
     * @param wheelIndex The index of the wheel to update.
     * @param rotation   The new rotation angle.
     * @param steer      The new steer angle.
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

    /**
     * Gets the synchronized wheel settings. Client-side only.
     *
     * @return A list of wheel settings.
     */
    @Environment(EnvType.CLIENT)
    public List<WheelSettingsWv> getWheelSettings() {
        return this.getSyncData(DATA_WHEELS_SETTINGS);
    }

    /**
     * Gets the client-side list of interpolated wheel states for rendering.
     *
     * @return A list of wheel render states.
     */
    @Environment(EnvType.CLIENT)
    public List<VxWheelRenderState> getInterpolatedWheelStates() {
        return interpolatedWheelStates;
    }

    /**
     * Marks the vehicle's dynamic state as dirty, flagging it for network synchronization.
     */
    public void markVehicleStateDirty() {
        this.vehicleStateDirty = true;
    }

    /**
     * Checks if the vehicle's dynamic state is dirty and needs to be synchronized.
     *
     * @return True if the state is dirty, false otherwise.
     */
    public boolean isVehicleStateDirty() {
        return vehicleStateDirty;
    }

    /**
     * Clears the dirty flag for the vehicle's dynamic state.
     */
    public void clearVehicleStateDirty() {
        this.vehicleStateDirty = false;
    }

    /**
     * Gets the vehicle's speed in kilometers per hour.
     * On the server, this value is calculated from the physics simulation.
     * On the client, this value is synchronized from the server.
     *
     * @return The speed in km/h.
     */
    public float getSpeedKmh() {
        return this.speedKmh;
    }

    /**
     * Gets the list of wheels attached to this vehicle.
     *
     * @return The list of VxWheel instances.
     */
    public List<VxWheel> getWheels() {
        return wheels;
    }
}