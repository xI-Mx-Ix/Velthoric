/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.std.StringStream;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.manager.VxRemovalReason;
import net.xmx.velthoric.physics.object.sync.VxDataAccessor;
import net.xmx.velthoric.physics.object.sync.VxDataSerializer;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import net.xmx.velthoric.physics.vehicle.wheel.VxWheel;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Abstract base class for all vehicle physics objects. This class manages the
 * Jolt VehicleConstraint and wheels. The management of the specific vehicle
 * controller is delegated to subclasses.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxVehicle extends VxRigidBody {

    private static class VehicleDataSerializers {
        public static final VxDataSerializer<WheelSettingsWv> WHEEL_SETTINGS_WV = new VxDataSerializer<>() {
            @Override public void write(VxByteBuf buf, WheelSettingsWv value) {
                try (StringStream stream = new StringStream()) {
                    value.saveBinaryState(new StreamOutWrapper(stream));
                    buf.writeByteArray(stream.str().getBytes());
                }
            }
            @Override public WheelSettingsWv read(VxByteBuf buf) {
                WheelSettingsWv settings = new WheelSettingsWv();
                byte[] bytes = buf.readByteArray();
                try (StringStream stream = new StringStream(new String(bytes))) {
                    settings.restoreBinaryState(new StreamInWrapper(stream));
                }
                return settings;
            }
            @Override public WheelSettingsWv copy(WheelSettingsWv value) {
                WheelSettingsWv newSettings = new WheelSettingsWv();
                try (StringStream stream = new StringStream()) {
                    value.saveBinaryState(new StreamOutWrapper(stream));
                    byte[] bytes = stream.str().getBytes();
                    try (StringStream readStream = new StringStream(new String(bytes))) {
                        newSettings.restoreBinaryState(new StreamInWrapper(readStream));
                    }
                }
                return newSettings;
            }
        };

        public static final VxDataSerializer<List<WheelSettingsWv>> WHEEL_SETTINGS_LIST = new VxDataSerializer<>() {
            @Override public void write(VxByteBuf buf, List<WheelSettingsWv> value) {
                buf.writeVarInt(value.size());
                for (WheelSettingsWv settings : value) {
                    WHEEL_SETTINGS_WV.write(buf, settings);
                }
            }
            @Override public List<WheelSettingsWv> read(VxByteBuf buf) {
                int size = buf.readVarInt();
                List<WheelSettingsWv> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    list.add(WHEEL_SETTINGS_WV.read(buf));
                }
                return list;
            }
            @Override public List<WheelSettingsWv> copy(List<WheelSettingsWv> value) {
                List<WheelSettingsWv> newList = new ArrayList<>(value.size());
                for (WheelSettingsWv settings : value) {
                    newList.add(WHEEL_SETTINGS_WV.copy(settings));
                }
                return newList;
            }
        };
    }

    public static final VxDataAccessor<List<WheelSettingsWv>> DATA_WHEELS_SETTINGS = VxDataAccessor.create(VxVehicle.class, VehicleDataSerializers.WHEEL_SETTINGS_LIST);

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
        List<WheelSettingsWv> wheelSettings = this.getSyncData(DATA_WHEELS_SETTINGS);
        buf.writeVarInt(wheelSettings.size());
        for (WheelSettingsWv setting : wheelSettings) {
            try (StringStream stream = new StringStream()) {
                setting.saveBinaryState(new StreamOutWrapper(stream));
                buf.writeByteArray(stream.str().getBytes());
            }
        }
    }

    @Override
    public void readPersistenceData(VxByteBuf buf) {
        int wheelCount = buf.readVarInt();
        if (wheelCount == this.wheels.size()) {
            for (VxWheel wheel : wheels) {
                byte[] bytes = buf.readByteArray();
                try (StringStream stream = new StringStream(new String(bytes))) {
                    wheel.getSettings().restoreBinaryState(new StreamInWrapper(stream));
                }
            }
            this.setSyncData(DATA_WHEELS_SETTINGS, this.wheels.stream().map(VxWheel::getSettings).collect(Collectors.toList()));
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