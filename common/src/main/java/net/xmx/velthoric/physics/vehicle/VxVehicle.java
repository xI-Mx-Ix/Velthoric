/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle;

import com.github.stephengold.joltjni.*;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.mounting.VxMountable;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.manager.VxRemovalReason;
import net.xmx.velthoric.physics.object.sync.VxDataAccessor;
import net.xmx.velthoric.physics.object.sync.VxDataSerializers;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import net.xmx.velthoric.physics.vehicle.wheel.VxWheel;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Abstract base class for all vehicle physics objects. This class manages the
 * Jolt VehicleConstraint and wheels. The management of the specific vehicle
 * controller is delegated to subclasses.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxVehicle extends VxRigidBody implements VxMountable {

    public static final VxDataAccessor<List<WheelSettingsWv>> DATA_WHEELS_SETTINGS = VxDataAccessor.create(VxVehicle.class, VxDataSerializers.WHEEL_SETTINGS_LIST);

    protected List<VxWheel> wheels = Collections.emptyList();
    protected VehicleConstraintSettings constraintSettings;
    protected VehicleCollisionTester collisionTester;

    protected VehicleConstraint constraint;
    private boolean wheelsDirty = false;

    protected VxVehicle(VxObjectType<? extends VxVehicle> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
    }

    @Override
    protected void defineSyncData() {
        this.synchronizedData.define(DATA_WHEELS_SETTINGS, Collections.emptyList());
    }

    @Override
    public void onBodyAdded(VxPhysicsWorld world) {
        super.onBodyAdded(world);

        Body body = getBody();
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