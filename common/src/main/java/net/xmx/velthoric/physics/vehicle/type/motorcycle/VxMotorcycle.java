/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.type.motorcycle;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.MotorcycleController;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.VehicleCollisionTester;
import com.github.stephengold.joltjni.VehicleConstraintSettings;
import com.github.stephengold.joltjni.operator.Op;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.body.manager.VxJoltBridge;
import net.xmx.velthoric.physics.body.manager.VxRemovalReason;
import net.xmx.velthoric.physics.body.registry.VxBodyType;
import net.xmx.velthoric.physics.body.sync.VxDataAccessor;
import net.xmx.velthoric.physics.body.sync.VxDataSerializers;
import net.xmx.velthoric.physics.body.sync.VxSynchronizedData;
import net.xmx.velthoric.physics.mounting.input.VxMountInput;
import net.xmx.velthoric.physics.vehicle.VxSteering;
import net.xmx.velthoric.physics.vehicle.VxVehicle;
import net.xmx.velthoric.physics.vehicle.controller.VxMotorcycleController;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;

/**
 * An abstract base class for motorcycle-like vehicles. It defines common logic
 * for input handling, including smooth steering and an intuitive model for
 * acceleration, braking, and reversing.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxMotorcycle extends VxVehicle {

    public static final VxDataAccessor<Vec3> DATA_CHASSIS_HALF_EXTENTS = VxDataAccessor.create(VxMotorcycle.class, VxDataSerializers.VEC3);

    private VxMotorcycleController controller;
    private float previousForward = 1.0f;
    private final VxSteering steering = new VxSteering(4.0f);
    private VxMountInput currentInput = VxMountInput.NEUTRAL;

    /**
     * Server-side constructor.
     */
    protected VxMotorcycle(VxBodyType<? extends VxMotorcycle> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
        this.constraintSettings = createConstraintSettings();
        this.collisionTester = createCollisionTester();
    }

    /**
     * Client-side constructor.
     */
    @Environment(EnvType.CLIENT)
    protected VxMotorcycle(VxBodyType<? extends VxMotorcycle> type, UUID id) {
        super(type, id);
    }

    protected abstract VehicleConstraintSettings createConstraintSettings();
    protected abstract VehicleCollisionTester createCollisionTester();

    @Override
    public void onBodyAdded(VxPhysicsWorld world) {
        super.onBodyAdded(world);
        if (constraint.getController() instanceof MotorcycleController joltController) {
            this.controller = new VxMotorcycleController(joltController);
        } else {
            throw new IllegalStateException("VxMotorcycle requires a MotorcycleController.");
        }
    }

    @Override
    public void onBodyRemoved(VxPhysicsWorld world, VxRemovalReason reason) {
        super.onBodyRemoved(world, reason);
        this.controller = null;
    }

    @Override
    public void physicsTick(VxPhysicsWorld world) {
        super.physicsTick(world);
        if (this.controller == null || this.physicsWorld == null) {
            return;
        }

        // Interpolate steering angle every tick.
        final float tickDelta = 1.0f / 20.0f; // Assumes a fixed 20 TPS physics tick rate.
        this.steering.update(tickDelta);

        // Calculate throttle and brake inputs based on last received state.
        float forwardInput = 0.0f;
        float brakeInput = 0.0f;

        if (this.currentInput.isForward()) {
            forwardInput = 1.0f;
        } else if (this.currentInput.isBackward()) {
            forwardInput = -1.0f;
        }

        // If the player attempts to switch from moving forward to reverse (or vice-versa),
        // the vehicle must first come to a stop by braking.
        if (previousForward * forwardInput < 0.0f) {
            Body joltBody = VxJoltBridge.INSTANCE.getJoltBody(this.physicsWorld, this.getBodyId());
            if(joltBody == null) return;

            // Get vehicle velocity in the body's local space to check forward/backward movement.
            Vec3 localVelocity = Op.star(joltBody.getRotation().conjugated(), joltBody.getLinearVelocity());
            float zVelocity = localVelocity.getZ();

            // Check if the vehicle is still moving significantly in the opposite direction.
            if ((forwardInput > 0.0f && zVelocity < -0.1f) || (forwardInput < 0.0f && zVelocity > 0.1f)) {
                // Apply brake instead of throttle until the vehicle stops.
                forwardInput = 0.0f;
                brakeInput = 1.0f;
            } else {
                // Once stopped, accept the new direction.
                previousForward = forwardInput;
            }
        }

        float handBrakeInput = this.currentInput.isUp() ? 1.0f : 0.0f;

        // Apply interpolated steering and other inputs to the physics controller.
        physicsWorld.getPhysicsSystem().getBodyInterface().activateBody(this.getBodyId());
        this.controller.setInput(forwardInput, this.steering.getCurrentAngle(), brakeInput, handBrakeInput);
    }

    @Override
    protected void defineSyncData(VxSynchronizedData.Builder builder) {
        super.defineSyncData(builder);
        builder.define(DATA_CHASSIS_HALF_EXTENTS, new Vec3(0.2f, 0.3f, 0.4f));
    }

    @Override
    public void onStopMounting(ServerPlayer player) {
        if (this.controller != null) {
            this.controller.setInput(0.0f, 0.0f, 0.0f, 0.0f);
        }
        this.currentInput = VxMountInput.NEUTRAL;
        this.steering.reset();
        this.previousForward = 1.0f;
    }

    /**
     * Handles driver input by updating the target state, which is then processed in physicsTick.
     *
     * @param player The player driving the vehicle.
     * @param input  The current state of the player's inputs.
     */
    @Override
    public void handleDriverInput(ServerPlayer player, VxMountInput input) {
        this.currentInput = input;

        // Determine the target steering direction from player input.
        float targetRight = 0.0f;
        if (input.isRight()) {
            targetRight = 1.0f;
        } else if (input.isLeft()) {
            targetRight = -1.0f;
        }
        this.steering.setTargetAngle(targetRight);
    }

    @Override
    public void writePersistenceData(VxByteBuf buf) {
        super.writePersistenceData(buf);
        DATA_CHASSIS_HALF_EXTENTS.getSerializer().write(buf, this.getSyncData(DATA_CHASSIS_HALF_EXTENTS));
    }

    @Override
    public void readPersistenceData(VxByteBuf buf) {
        super.readPersistenceData(buf);
        this.setSyncData(DATA_CHASSIS_HALF_EXTENTS, DATA_CHASSIS_HALF_EXTENTS.getSerializer().read(buf));
    }

    public Vec3 getChassisHalfExtents() {
        return this.getSyncData(DATA_CHASSIS_HALF_EXTENTS);
    }

    public VxMotorcycleController getController() {
        return controller;
    }
}