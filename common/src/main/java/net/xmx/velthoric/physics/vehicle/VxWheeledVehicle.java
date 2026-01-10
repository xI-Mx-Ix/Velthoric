/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.ETransmissionMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.xmx.velthoric.bridge.mounting.input.VxMountInput;
import net.xmx.velthoric.physics.body.VxJoltBridge;
import net.xmx.velthoric.physics.body.VxRemovalReason;
import net.xmx.velthoric.physics.body.network.synchronization.VxDataSerializers;
import net.xmx.velthoric.physics.body.network.synchronization.VxSynchronizedData;
import net.xmx.velthoric.physics.body.network.synchronization.accessor.VxServerAccessor;
import net.xmx.velthoric.physics.body.registry.VxBodyType;
import net.xmx.velthoric.physics.vehicle.module.VxSteeringModule;
import net.xmx.velthoric.physics.vehicle.module.VxEngineModule;
import net.xmx.velthoric.physics.vehicle.module.VxTransmissionModule;
import net.xmx.velthoric.physics.vehicle.config.VxWheeledVehicleConfig;
import net.xmx.velthoric.physics.vehicle.part.definition.VxWheelDefinition;
import net.xmx.velthoric.physics.vehicle.part.impl.VxVehicleWheel;
import net.xmx.velthoric.physics.vehicle.part.slot.VehicleWheelSlot;
import net.xmx.velthoric.physics.vehicle.sync.VxVehicleSerializers;
import net.xmx.velthoric.physics.vehicle.sync.VxVehicleWheelState;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An abstract class for vehicles that move on wheels using an engine and transmission.
 * <p>
 * This class extends the generic {@link VxVehicle} to add logic specific to
 * Jolt's Vehicle Constraint system (Cars, Motorcycles). It handles:
 * <ul>
 *     <li>Powertrain simulation (Engine & Transmission).</li>
 *     <li>Wheel management and physics updates.</li>
 *     <li>Jolt VehicleConstraint lifecycle.</li>
 *     <li>Driver input processing (Throttle, Brake, Steer).</li>
 * </ul>
 *
 * @author xI-Mx-Ix
 */
public abstract class VxWheeledVehicle extends VxVehicle {

    // --- Synchronization Accessors ---

    public static final VxServerAccessor<Float> SYNC_RPM =
            VxServerAccessor.create(VxWheeledVehicle.class, VxDataSerializers.FLOAT);

    public static final VxServerAccessor<Integer> SYNC_GEAR =
            VxServerAccessor.create(VxWheeledVehicle.class, VxDataSerializers.INTEGER);

    public static final VxServerAccessor<Float> SYNC_THROTTLE =
            VxServerAccessor.create(VxWheeledVehicle.class, VxDataSerializers.FLOAT);

    public static final VxServerAccessor<Float> SYNC_STEER =
            VxServerAccessor.create(VxWheeledVehicle.class, VxDataSerializers.FLOAT);

    public static final VxServerAccessor<List<VxVehicleWheelState>> SYNC_WHEELS =
            VxServerAccessor.create(
                    VxWheeledVehicle.class,
                    VxVehicleSerializers.WHEEL_STATES
            );

    // --- Logical Components ---

    protected VxEngineModule engine;
    protected VxTransmissionModule transmission;

    /**
     * Helper for smoothing steering inputs.
     */
    protected final VxSteeringModule steeringHelper = new VxSteeringModule(2.0f);

    /**
     * A typed list of wheels for efficient physics step updates.
     */
    protected final List<VxVehicleWheel> wheels = new ArrayList<>();

    // --- Physics ---

    protected VehicleConstraint constraint;
    protected VehicleCollisionTester collisionTester;
    protected VehicleConstraintSettings constraintSettings;

    // --- Input State ---

    /**
     * The current input state received from the driver.
     */
    protected VxMountInput currentInput = VxMountInput.NEUTRAL;

    // Internal state tracking
    private float inputThrottle = 0.0f;
    private float inputSteer = 0.0f;

    // Debounce flags for shifting
    private boolean wasShiftUpPressed = false;
    private boolean wasShiftDownPressed = false;

    /**
     * Server-side constructor.
     */
    public VxWheeledVehicle(VxBodyType<? extends VxWheeledVehicle> type, VxPhysicsWorld world, UUID id, VxWheeledVehicleConfig config) {
        super(type, world, id, config);
        this.initializePowertrain();
        this.initializeWheels();
    }

    /**
     * Client-side constructor.
     */
    @Environment(EnvType.CLIENT)
    public VxWheeledVehicle(VxBodyType<? extends VxWheeledVehicle> type, UUID id, VxWheeledVehicleConfig config) {
        super(type, id, config);
        this.initializePowertrain();
        this.initializeWheels();
    }

    /**
     * Overrides the return type of getConfig to return the specific wheeled config.
     */
    @Override
    public VxWheeledVehicleConfig getConfig() {
        return (VxWheeledVehicleConfig) super.getConfig();
    }

    // --- Abstract Methods ---

    /**
     * Resolves a wheel definition ID to an actual definition object.
     */
    protected abstract VxWheelDefinition resolveWheelDefinition(String wheelId);

    /**
     * Defines the collision tester (CylinderCast vs RayCast).
     */
    protected abstract VehicleCollisionTester createCollisionTester();

    /**
     * Creates the Jolt Constraint Settings from the vehicle config.
     */
    protected abstract VehicleConstraintSettings createConstraintSettings(Body body);

    /**
     * Hooks up the specific Jolt controller reference (Car vs Motorcycle).
     */
    protected abstract void updateJoltControllerReference();

    // --- Initialization ---

    private void initializePowertrain() {
        VxWheeledVehicleConfig cfg = getConfig();
        this.engine = new VxEngineModule(
                cfg.getEngine().getMaxTorque(),
                cfg.getEngine().getMinRpm(),
                cfg.getEngine().getMaxRpm()
        );

        this.transmission = new VxTransmissionModule(
                cfg.getTransmission().getGearRatios(),
                cfg.getTransmission().getReverseRatio(),
                cfg.getTransmission().getSwitchTime()
        );

        // Set initial synced values if on server
        if (this.physicsWorld != null) {
            this.setServerData(SYNC_RPM, cfg.getEngine().getMinRpm());
        }
    }

    private void initializeWheels() {
        VxWheeledVehicleConfig cfg = getConfig();
        VxWheelDefinition defaultDef = resolveWheelDefinition(cfg.getDefaultWheelId());

        if (defaultDef == null) {
            defaultDef = VxWheelDefinition.missing();
        }

        for (VehicleWheelSlot slot : cfg.getWheelSlots()) {
            this.mountWheel(slot, defaultDef);
        }
    }

    private void mountWheel(VehicleWheelSlot slot, VxWheelDefinition def) {
        WheelSettingsWv settings = new WheelSettingsWv();
        Vector3f pos = slot.getPosition();

        // Physical Setup
        settings.setPosition(new com.github.stephengold.joltjni.Vec3(pos.x, pos.y, pos.z));
        settings.setSuspensionMinLength(slot.getSuspensionMinLength());
        settings.setSuspensionMaxLength(slot.getSuspensionMaxLength());
        settings.getSuspensionSpring().setFrequency(slot.getSuspensionFrequency());
        settings.getSuspensionSpring().setDamping(slot.getSuspensionDamping());
        settings.setMaxBrakeTorque(slot.getMaxBrakeTorque());

        if (slot.isSteerable()) {
            settings.setMaxSteerAngle((float) Math.toRadians(slot.getMaxSteerAngleDegrees()));
        } else {
            settings.setMaxSteerAngle(0f);
        }

        settings.setRadius(def.radius());
        settings.setWidth(def.width());

        // Create logical Part
        VxVehicleWheel wheel = new VxVehicleWheel(this, slot.getName(), settings, slot, def);
        this.wheels.add(wheel);
        this.addPart(wheel);
    }

    // --- Synchronization ---

    @Override
    protected void defineSyncData(VxSynchronizedData.Builder builder) {
        super.defineSyncData(builder); // Define SYNC_SPEED

        builder.define(SYNC_RPM, 0.0f);
        builder.define(SYNC_GEAR, 0);
        builder.define(SYNC_THROTTLE, 0.0f);
        builder.define(SYNC_STEER, 0.0f);
        builder.define(SYNC_WHEELS, new ArrayList<>());
    }

    @Override
    public void onSyncedDataUpdated(VxServerAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);

        if (accessor.equals(SYNC_RPM)) {
            this.engine.setSynchronizedRpm(getSynchronizedData().get(SYNC_RPM));

        } else if (accessor.equals(SYNC_GEAR)) {
            this.transmission.setSynchronizedGear(getSynchronizedData().get(SYNC_GEAR));

        } else if (accessor.equals(SYNC_THROTTLE)) {
            this.inputThrottle = getSynchronizedData().get(SYNC_THROTTLE);

        } else if (accessor.equals(SYNC_STEER)) {
            this.inputSteer = getSynchronizedData().get(SYNC_STEER);

        } else if (accessor.equals(SYNC_WHEELS)) {
            // Distribute wheel state to interpolation targets on the client
            List<VxVehicleWheelState> states = getSynchronizedData().get(SYNC_WHEELS);
            int count = Math.min(states.size(), wheels.size());
            for (int i = 0; i < count; i++) {
                wheels.get(i).updateClientTarget(
                        states.get(i).rotation(),
                        states.get(i).steer(),
                        states.get(i).suspension()
                );
            }
        }
    }

    // --- Physics Lifecycle ---

    @Override
    public void onBodyAdded(VxPhysicsWorld world) {
        super.onBodyAdded(world);

        Body body = VxJoltBridge.INSTANCE.getJoltBody(world, getBodyId());
        if (body == null) return;

        // 1. Create Collision Tester & Constraint Settings
        this.collisionTester = createCollisionTester();
        this.constraintSettings = createConstraintSettings(body);

        // 2. Add wheels to Jolt settings
        for (VxVehicleWheel wheel : this.wheels) {
            this.constraintSettings.addWheels(wheel.getSettings());
        }

        // 3. Create Constraint
        this.constraint = new VehicleConstraint(body, this.constraintSettings);
        this.constraint.setVehicleCollisionTester(this.collisionTester);

        // 4. Register
        world.getPhysicsSystem().addConstraint(constraint);
        world.getPhysicsSystem().addStepListener(constraint.getStepListener());

        this.updateJoltControllerReference();
    }

    @Override
    public void onBodyRemoved(VxPhysicsWorld world, VxRemovalReason reason) {
        super.onBodyRemoved(world, reason);

        // Clean up Jolt resources
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
    public void onPhysicsTick(VxPhysicsWorld world) {
        super.onPhysicsTick(world);
        if (constraint == null) return;

        // Wake up body if input exists
        if (!this.currentInput.equals(VxMountInput.NEUTRAL)) {
            BodyInterface bodyInterface = world.getPhysicsSystem().getBodyInterface();
            if (!bodyInterface.isActive(getBodyId())) {
                bodyInterface.activateBody(getBodyId());
            }
        }

        float dt = 1.0f / 60.0f;
        this.processDriverInput(dt);

        Body joltBody = VxJoltBridge.INSTANCE.getJoltBody(world, getBodyId());
        if (joltBody != null && joltBody.isActive()) {

            // --- Transmission Logic ---
            if (getConfig().getTransmission().getMode() == ETransmissionMode.Manual) {
                transmission.update(dt);

                boolean shiftUp = currentInput.hasAction(VxMountInput.FLAG_SHIFT_UP);
                if (shiftUp && !wasShiftUpPressed) transmission.shiftUp();
                wasShiftUpPressed = shiftUp;

                boolean shiftDown = currentInput.hasAction(VxMountInput.FLAG_SHIFT_DOWN);
                if (shiftDown && !wasShiftDownPressed) transmission.shiftDown();
                wasShiftDownPressed = shiftDown;
            } else {
                // Automatic: Sync generic transmission with Jolt controller state
                if (constraint.getController() instanceof WheeledVehicleController wvc) {
                    transmission.setSynchronizedGear(wvc.getTransmission().getCurrentGear());
                }
                wasShiftUpPressed = false;
                wasShiftDownPressed = false;
            }

            // --- Engine Physics ---
            // Calculate average wheel RPM to feedback into engine
            float avgWheelRpm = 0;
            int poweredCount = 0;
            for (int i = 0; i < wheels.size(); i++) {
                if (wheels.get(i).isPowered()) {
                    avgWheelRpm += Math.abs(constraint.getWheel(i).getAngularVelocity()) * 9.55f;
                    poweredCount++;
                }
            }
            if (poweredCount > 0) avgWheelRpm /= poweredCount;

            // Determine if clutch is engaged (Always engaged for Auto, depends on shift timer for Manual)
            boolean clutchEngaged = (getConfig().getTransmission().getMode() == ETransmissionMode.Auto) || !transmission.isShifting();
            engine.update(Math.abs(inputThrottle), avgWheelRpm, transmission.getCurrentRatio(), clutchEngaged);

            // --- Wheel State Sync ---
            List<VxVehicleWheelState> wheelStates = new ArrayList<>(wheels.size());
            for (int i = 0; i < wheels.size(); i++) {
                Wheel w = constraint.getWheel(i);

                // Update local component
                wheels.get(i).updatePhysicsState(w.getRotationAngle(), w.getSteerAngle(), w.getSuspensionLength(), w.hasContact());

                // Collect for network
                wheelStates.add(new VxVehicleWheelState(w.getRotationAngle(), w.getSteerAngle(), w.getSuspensionLength()));
            }

            // --- Send Data to Clients ---
            setServerData(SYNC_RPM, engine.getRpm());
            setServerData(SYNC_GEAR, transmission.getGear());
            setServerData(SYNC_THROTTLE, this.inputThrottle);
            setServerData(SYNC_STEER, this.inputSteer);
            setServerData(SYNC_WHEELS, wheelStates);

            // --- Apply Inputs to Jolt ---
            this.updateJoltController();
        }
    }

    /**
     * Applies the calculated inputs to the underlying Jolt Vehicle Controller.
     */
    protected void updateJoltController() {
        if (constraint == null) return;

        if (constraint.getController() instanceof WheeledVehicleController controller) {
            float throttle = inputThrottle;
            float brake = 0.0f;
            int gear = transmission.getGear();

            // Smart Gas/Brake logic based on gear
            if (gear > 0 && throttle < 0) {
                brake = Math.abs(throttle);
                throttle = 0;
            } else if (gear == -1 && throttle > 0) {
                brake = throttle;
                throttle = 0;
            }

            // Auto-brake at standstill
            if (throttle == 0 && brake == 0 && Math.abs(getSpeedKmh()) < 1.0f) {
                brake = 1.0f;
            }

            float handbrake = currentInput.hasAction(VxMountInput.FLAG_HANDBRAKE) ? 1.0f : 0.0f;
            controller.setDriverInput(Math.abs(throttle), inputSteer, brake, handbrake);

            // Manual Clutch logic
            if (getConfig().getTransmission().getMode() == ETransmissionMode.Manual) {
                float clutch = transmission.isShifting() ? 0.0f : 1.0f;
                controller.getTransmission().set(gear, clutch);
            }
        }
    }

    /**
     * Smooths raw inputs.
     */
    protected void processDriverInput(float dt) {
        float targetThrottle = currentInput.getForwardAmount();
        this.inputThrottle = Mth.lerp(dt * 5.0f, inputThrottle, targetThrottle);

        float targetSteer = currentInput.getRightAmount();
        steeringHelper.setTargetAngle(targetSteer);
        steeringHelper.update(dt);
        this.inputSteer = steeringHelper.getCurrentAngle();
    }

    @Override
    public void handleDriverInput(ServerPlayer player, VxMountInput input) {
        this.currentInput = input;
        int bodyId = getBodyId();
        if (bodyId != 0) this.physicsWorld.getPhysicsSystem().getBodyInterface().activateBody(bodyId);
    }

    /**
     * Runtime method to swap a wheel definition on a specific slot.
     */
    public void equipWheel(String slotName, VxWheelDefinition newDef) {
        for (VxVehicleWheel wheel : this.wheels) {
            if (wheel.getSlot().getName().equals(slotName)) {
                wheel.setDefinition(newDef);
                wheel.getSettings().setRadius(newDef.radius());
                wheel.getSettings().setWidth(newDef.width());
                return;
            }
        }
    }

    public List<VxVehicleWheel> getWheels() {
        return wheels;
    }

    public VxEngineModule getEngine() {
        return engine;
    }

    public VxTransmissionModule getTransmission() {
        return transmission;
    }
}