/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle;

import com.github.stephengold.joltjni.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.xmx.velthoric.physics.body.manager.VxJoltBridge;
import net.xmx.velthoric.physics.body.manager.VxRemovalReason;
import net.xmx.velthoric.physics.body.registry.VxBodyType;
import net.xmx.velthoric.physics.body.sync.VxSynchronizedData;
import net.xmx.velthoric.physics.body.type.VxRigidBody;
import net.xmx.velthoric.physics.mounting.VxMountable;
import net.xmx.velthoric.physics.mounting.input.VxMountInput;
import net.xmx.velthoric.physics.vehicle.component.VxSteering;
import net.xmx.velthoric.physics.vehicle.component.VxVehicleEngine;
import net.xmx.velthoric.physics.vehicle.component.VxVehicleTransmission;
import net.xmx.velthoric.physics.vehicle.component.VxVehicleWheel;
import net.xmx.velthoric.physics.vehicle.config.VxVehicleConfig;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The core modular vehicle class.
 * This class serves as the base for all physics-driven vehicles, handling
 * component management (engine, transmission, wheels), input processing,
 * and Jolt physics synchronization.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxVehicle extends VxRigidBody implements VxMountable {

    // --- Components ---
    protected VxVehicleConfig config;
    protected VxVehicleEngine engine;
    protected VxVehicleTransmission transmission;
    protected final List<VxVehicleWheel> wheels = new ArrayList<>();
    protected final VxSteering steeringHelper = new VxSteering(2.0f);

    // --- Physics ---
    protected VehicleConstraint constraint;
    protected VehicleCollisionTester collisionTester;
    protected VehicleConstraintSettings constraintSettings;

    // --- State ---
    private boolean isStateDirty = false;
    private float speedKmh = 0.0f;
    private float inputThrottle = 0.0f;
    private float inputSteer = 0.0f;

    // --- Input Tracking ---
    /**
     * The current input state received from the driver.
     */
    protected VxMountInput currentInput = VxMountInput.NEUTRAL;

    // Debounce flags to prevent rapid toggling of gears per tick
    private boolean wasShiftUpPressed = false;
    private boolean wasShiftDownPressed = false;

    /**
     * Server-side constructor.
     *
     * @param type  The body type.
     * @param world The physics world.
     * @param id    The unique ID.
     */
    public VxVehicle(VxBodyType<? extends VxVehicle> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
        this.config = createConfig();
        this.initializeComponents();
    }

    /**
     * Client-side constructor.
     *
     * @param type The body type.
     * @param id   The unique ID.
     */
    @Environment(EnvType.CLIENT)
    public VxVehicle(VxBodyType<? extends VxVehicle> type, UUID id) {
        super(type, id);
        this.config = createConfig();
        this.initializeComponents();
    }

    /**
     * Creates the specific configuration for this vehicle type.
     *
     * @return A new instance of {@link VxVehicleConfig}.
     */
    protected abstract VxVehicleConfig createConfig();

    /**
     * Defines the collision tester to be used by this vehicle.
     *
     * @return The collision tester instance.
     */
    protected abstract VehicleCollisionTester createCollisionTester();

    /**
     * Creates the Jolt Constraint Settings from the vehicle configuration.
     * Must be implemented by subclasses to set up the correct controller (Car/Motorcycle).
     *
     * @param body The Jolt body.
     * @return The configured constraint settings.
     */
    protected abstract VehicleConstraintSettings createConstraintSettings(Body body);

    /**
     * Hooks up the specific Jolt controller reference after the constraint is created.
     */
    protected abstract void updateJoltControllerReference();

    /**
     * Initializes components based on the configuration.
     */
    private void initializeComponents() {
        for (VxVehicleConfig.WheelInfo info : config.getWheels()) {
            WheelSettingsWv ws = new WheelSettingsWv();
            ws.setPosition(info.position());
            ws.setRadius(info.radius());
            ws.setWidth(info.width());
            // Default suspension settings, usually fine-tuned in config too
            ws.setSuspensionMinLength(0.2f);
            ws.setSuspensionMaxLength(0.5f);
            wheels.add(new VxVehicleWheel(ws, info.powered(), info.steerable()));
        }

        // Default components, usually overridden by specific vehicle implementations (Car/Motorcycle)
        this.engine = new VxVehicleEngine(500f, 800f, 7000f);
        this.transmission = new VxVehicleTransmission(new float[]{3.5f, 2.0f, 1.4f, 1.0f}, -3.0f);
    }

    @Override
    public void onBodyAdded(VxPhysicsWorld world) {
        super.onBodyAdded(world);

        Body body = VxJoltBridge.INSTANCE.getJoltBody(world, getBodyId());
        if (body == null) {
            return;
        }

        // 1. Create the Collision Tester
        this.collisionTester = createCollisionTester();

        // 2. Create the Constraint Settings based on the body and config
        this.constraintSettings = createConstraintSettings(body);

        // 3. Add wheels from our modular component list to the Jolt settings
        for (VxVehicleWheel wheel : this.wheels) {
            this.constraintSettings.addWheels(wheel.getSettings());
        }

        // 4. Create the actual VehicleConstraint
        this.constraint = new VehicleConstraint(body, this.constraintSettings);
        this.constraint.setVehicleCollisionTester(this.collisionTester);

        // 5. Register with physics system
        world.getPhysicsSystem().addConstraint(constraint);
        world.getPhysicsSystem().addStepListener(constraint.getStepListener());

        // 6. Update the controller reference in subclasses
        this.updateJoltControllerReference();
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
        if (constraintSettings != null) {
            constraintSettings.close();
            constraintSettings = null;
        }
        if (collisionTester != null) {
            collisionTester.close();
            collisionTester = null;
        }
    }

    @Override
    public void physicsTick(VxPhysicsWorld world) {
        super.physicsTick(world);
        if (constraint == null) return;

        // 1. Check if we have any driver input.
        // If the player tries to steer, accelerate, or shift gears (non-neutral input),
        // we must ensure the physics body is awake (Active) to process these changes.
        if (!this.currentInput.equals(VxMountInput.NEUTRAL)) {
            BodyInterface bodyInterface = world.getPhysicsSystem().getBodyInterface();
            // Force the body to wake up if it is currently sleeping
            if (!bodyInterface.isActive(getBodyId())) {
                bodyInterface.activateBody(getBodyId());
            }
        }

        float dt = 1.0f / 20.0f;

        // 2. Process Inputs & Steering Interpolation
        this.processDriverInput(dt);

        // 3. Physics Simulation Interface
        Body joltBody = VxJoltBridge.INSTANCE.getJoltBody(world, getBodyId());

        // We check isActive() here. Thanks to step 1, this will now be true if the player pressed a key.
        if (joltBody != null && joltBody.isActive()) {

            // Calculate Speed in KM/H
            Vec3 vel = joltBody.getLinearVelocity();
            this.speedKmh = vel.length() * 3.6f;

            // Apply Transmission Logic (Time based)
            transmission.update(dt);

            // Handle Gear Shifting (Edge detection on input flags)
            boolean shiftUp = currentInput.hasAction(VxMountInput.FLAG_SHIFT_UP);
            if (shiftUp && !wasShiftUpPressed) {
                transmission.shiftUp();
            }
            wasShiftUpPressed = shiftUp;

            boolean shiftDown = currentInput.hasAction(VxMountInput.FLAG_SHIFT_DOWN);
            if (shiftDown && !wasShiftDownPressed) {
                transmission.shiftDown();
            }
            wasShiftDownPressed = shiftDown;

            // Calculate average Wheel RPM for Engine simulation
            float avgWheelRpm = 0;
            int poweredCount = 0;
            for (int i = 0; i < wheels.size(); i++) {
                if (wheels.get(i).isPowered()) {
                    // Convert Rad/s to RPM ( approx * 9.55 )
                    avgWheelRpm += Math.abs(constraint.getWheel(i).getAngularVelocity()) * 9.55f;
                    poweredCount++;
                }
            }
            if (poweredCount > 0) avgWheelRpm /= poweredCount;

            // Update Engine Physics
            engine.update(Math.abs(inputThrottle), avgWheelRpm, transmission.getCurrentRatio(), !transmission.isShifting());

            // 4. Sync Physics State to Local Wheel Components (for later networking & rendering)
            for (int i = 0; i < wheels.size(); i++) {
                Wheel w = constraint.getWheel(i);
                wheels.get(i).updatePhysicsState(w.getRotationAngle(), w.getSteerAngle(), w.getSuspensionLength(), w.hasContact());
            }

            // 5. Update Jolt Controller Input (Throttle, Brake, etc.)
            this.updateJoltController();

            // Mark for network sync
            this.isStateDirty = true;
        }
    }

    /**
     * Processes raw driver input, applying smoothing and steering interpolation.
     *
     * @param dt The time delta.
     */
    protected void processDriverInput(float dt) {
        // Direct mapping from the normalized input (-1.0 to 1.0)
        float targetThrottle = currentInput.getForwardAmount();

        // Simple smoothing for throttle to prevent jerky movement
        this.inputThrottle = Mth.lerp(dt * 5.0f, inputThrottle, targetThrottle);

        float targetSteer = currentInput.getRightAmount();

        steeringHelper.setTargetAngle(targetSteer);
        steeringHelper.update(dt);
        this.inputSteer = steeringHelper.getCurrentAngle();
    }

    /**
     * Applies the calculated inputs to the underlying Jolt Vehicle Controller.
     * Must be implemented by subclasses (Car/Motorcycle) as they utilize different controllers.
     */
    protected abstract void updateJoltController();

    @Override
    public void handleDriverInput(ServerPlayer player, VxMountInput input) {
        // Store the input; processing happens during the physics tick.
        this.currentInput = input;
    }

    // --- Client Synchronization ---

    /**
     * Updates the vehicle state based on data received from the server.
     *
     * @param speed    The current speed in km/h.
     * @param rpm      The current engine RPM.
     * @param gear     The current gear index.
     * @param throttle The current throttle value.
     * @param steer    The current steering angle.
     */
    public void syncStateFromServer(float speed, float rpm, int gear, float throttle, float steer) {
        this.speedKmh = speed;
        this.engine.setSynchronizedRpm(rpm);
        this.transmission.setSynchronizedGear(gear);
        this.inputThrottle = throttle;
        this.inputSteer = steer;
    }

    @Override
    protected void defineSyncData(VxSynchronizedData.Builder builder) {
        // No-Op
    }

    // --- Getters ---

    public VxVehicleEngine getEngine() {
        return engine;
    }

    public VxVehicleTransmission getTransmission() {
        return transmission;
    }

    public List<VxVehicleWheel> getWheels() {
        return wheels;
    }

    public float getSpeedKmh() {
        return speedKmh;
    }

    public float getInputThrottle() {
        return inputThrottle;
    }

    public float getInputSteer() {
        return inputSteer;
    }

    public boolean isVehicleStateDirty() {
        return isStateDirty;
    }

    public void clearVehicleStateDirty() {
        this.isStateDirty = false;
    }

    public VxVehicleConfig getConfig() {
        return config;
    }
}