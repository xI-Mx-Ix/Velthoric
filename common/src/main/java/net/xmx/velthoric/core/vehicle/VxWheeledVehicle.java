/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.vehicle;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.ETransmissionMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.xmx.velthoric.core.mounting.input.VxMountInput;
import net.xmx.velthoric.core.physics.VxJoltBridge;
import net.xmx.velthoric.core.body.VxRemovalReason;
import net.xmx.velthoric.core.network.synchronization.VxDataSerializers;
import net.xmx.velthoric.core.network.synchronization.VxSynchronizedData;
import net.xmx.velthoric.core.network.synchronization.accessor.VxServerAccessor;
import net.xmx.velthoric.core.body.registry.VxBodyType;
import net.xmx.velthoric.core.vehicle.config.VxWheeledVehicleConfig;
import net.xmx.velthoric.core.vehicle.module.VxSteeringModule;
import net.xmx.velthoric.core.vehicle.module.transmission.VxAutomaticTransmissionModule;
import net.xmx.velthoric.core.vehicle.module.transmission.VxManualTransmissionModule;
import net.xmx.velthoric.core.vehicle.module.transmission.VxTransmissionModule;
import net.xmx.velthoric.core.vehicle.part.definition.VxWheelDefinition;
import net.xmx.velthoric.core.vehicle.part.impl.VxVehicleWheel;
import net.xmx.velthoric.core.vehicle.part.slot.VehicleWheelSlot;
import net.xmx.velthoric.core.vehicle.sync.VxVehicleSerializers;
import net.xmx.velthoric.core.vehicle.sync.VxVehicleWheelState;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An abstract implementation for vehicles that operate on wheels.
 * <p>
 * This class serves as the bridge between the high-level {@link VxVehicle} concepts
 * and the low-level Jolt {@link VehicleConstraint}. It manages the powertrain input logic
 * via the {@link VxTransmissionModule} and synchronizes physics state to clients.
 * <p>
 * Key responsibilities:
 * <ul>
 *     <li>Managing the Jolt {@link VehicleConstraint} lifecycle.</li>
 *     <li>Initializing wheels based on configuration slots.</li>
 *     <li>Delegating input processing to the transmission module.</li>
 *     <li>Synchronizing RPM, Gear, and visual wheel states to the client.</li>
 * </ul>
 *
 * @author xI-Mx-Ix
 */
public abstract class VxWheeledVehicle extends VxVehicle {

    // --- Network Synchronization Accessors ---

    /**
     * Synchronizes the current engine RPM to clients for audio and UI.
     */
    public static final VxServerAccessor<Float> SYNC_RPM =
            VxServerAccessor.create(VxWheeledVehicle.class, VxDataSerializers.FLOAT);

    /**
     * Synchronizes the current gear index to clients.
     * <p>
     * Values: 0 (Neutral), -1 (Reverse), 1+ (Forward Gears).
     */
    public static final VxServerAccessor<Integer> SYNC_GEAR =
            VxServerAccessor.create(VxWheeledVehicle.class, VxDataSerializers.INTEGER);

    /**
     * Synchronizes the smoothed throttle input [0..1] for animation (pedals).
     */
    public static final VxServerAccessor<Float> SYNC_THROTTLE =
            VxServerAccessor.create(VxWheeledVehicle.class, VxDataSerializers.FLOAT);

    /**
     * Synchronizes the smoothed steering input [-1..1] for animation (steering wheel).
     */
    public static final VxServerAccessor<Float> SYNC_STEER =
            VxServerAccessor.create(VxWheeledVehicle.class, VxDataSerializers.FLOAT);

    /**
     * Synchronizes the visual state (rotation, compression, steering) of all wheels.
     * Used for smooth client-side interpolation of wheel parts.
     */
    public static final VxServerAccessor<List<VxVehicleWheelState>> SYNC_WHEELS =
            VxServerAccessor.create(VxWheeledVehicle.class, VxVehicleSerializers.WHEEL_STATES);

    // --- Components ---

    /**
     * The transmission logic module.
     * Handles the translation of driver input into physics commands (Gear, Clutch, Forward/Brake).
     */
    protected VxTransmissionModule transmissionModule;

    /**
     * Helper for interpolating steering input over time to prevent jerky movement.
     */
    protected final VxSteeringModule steeringHelper = new VxSteeringModule(4.0f);

    /**
     * A typed list of wheel parts associated with this vehicle.
     * Maintained for efficient iteration during physics steps.
     */
    protected final List<VxVehicleWheel> wheels = new ArrayList<>();

    // --- Physics Objects ---

    /**
     * The main Jolt constraint governing the vehicle physics.
     * Null on the client or if the body is not physically active.
     */
    protected VehicleConstraint constraint;

    /**
     * Helper object for performing collision queries for the wheels.
     */
    protected VehicleCollisionTester collisionTester;

    /**
     * Configuration container for the vehicle constraint.
     */
    protected VehicleConstraintSettings constraintSettings;

    // --- Input State ---

    /**
     * The current raw input state received from the driver.
     */
    protected VxMountInput currentInput = VxMountInput.NEUTRAL;

    /**
     * Internally tracked smoothed throttle for logic and sync.
     */
    private float smoothedThrottle = 0.0f;

    /**
     * Internally tracked smoothed steering for logic and sync.
     */
    private float smoothedSteer = 0.0f;

    /**
     * Server-side constructor.
     *
     * @param type   The registered body type.
     * @param world  The physics world instance.
     * @param id     The unique identifier.
     * @param config The vehicle configuration.
     */
    public VxWheeledVehicle(VxBodyType<? extends VxWheeledVehicle> type, VxPhysicsWorld world, UUID id, VxWheeledVehicleConfig config) {
        super(type, world, id, config);
        this.initializeComponents();
    }

    /**
     * Client-side constructor.
     *
     * @param type   The registered body type.
     * @param id     The unique identifier.
     * @param config The vehicle configuration.
     */
    @Environment(EnvType.CLIENT)
    public VxWheeledVehicle(VxBodyType<? extends VxWheeledVehicle> type, UUID id, VxWheeledVehicleConfig config) {
        super(type, id, config);
        this.initializeComponents();
    }

    /**
     * {@inheritDoc}
     * Overridden to return the specific wheeled configuration type.
     */
    @Override
    public VxWheeledVehicleConfig getConfig() {
        return (VxWheeledVehicleConfig) super.getConfig();
    }

    // --- Abstract Hooks ---

    /**
     * Resolves the wheel definition for a given ID from the registry.
     *
     * @param wheelId The resource ID of the wheel.
     * @return The definition, or null if missing.
     */
    protected abstract VxWheelDefinition resolveWheelDefinition(String wheelId);

    /**
     * Creates the collision tester specific to the vehicle type.
     * <p>
     * Motorcycles typically use cylinder casting with a smaller width, while cars
     * might use standard ray casting or cylinder casting matching wheel width.
     *
     * @return The configured collision tester.
     */
    protected abstract VehicleCollisionTester createCollisionTester();

    /**
     * Generates the Jolt constraint settings from the configuration.
     * This defines the engine, transmission ratios, and differential setup.
     *
     * @param body The Jolt body instance to attach to.
     * @return The fully configured constraint settings.
     */
    protected abstract VehicleConstraintSettings createConstraintSettings(Body body);

    /**
     * Called after constraint creation to store type-specific Jolt controller references.
     * e.g., {@code MotorcycleController} vs {@code WheeledVehicleController}.
     */
    protected abstract void updateJoltControllerReference();

    // --- Initialization ---

    /**
     * Initializes logical components such as wheels and the transmission module.
     * Called by the constructor.
     */
    private void initializeComponents() {
        VxWheeledVehicleConfig cfg = getConfig();

        // 1. Initialize Wheels from Config
        VxWheelDefinition defaultDef = resolveWheelDefinition(cfg.getDefaultWheelId());
        if (defaultDef == null) defaultDef = VxWheelDefinition.missing();

        for (VehicleWheelSlot slot : cfg.getWheelSlots()) {
            this.mountWheel(slot, defaultDef);
        }

        // 2. Initialize Transmission Module based on config mode
        if (cfg.getTransmission().getMode() == ETransmissionMode.Auto) {
            this.transmissionModule = new VxAutomaticTransmissionModule();
        } else {
            this.transmissionModule = new VxManualTransmissionModule(
                    cfg.getTransmission().getSwitchTime(),
                    cfg.getTransmission().getGearRatios().length
            );
        }
    }

    /**
     * Mounts a wheel to the vehicle logic.
     * Creates the Jolt settings and the Velthoric part entity.
     *
     * @param slot The chassis slot configuration.
     * @param def  The definition of the wheel item installed.
     */
    private void mountWheel(VehicleWheelSlot slot, VxWheelDefinition def) {
        WheelSettingsWv settings = new WheelSettingsWv();
        Vector3f pos = slot.getPosition();

        // Transfer physical properties to Jolt settings
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

        // Create logical Part and add to lists
        VxVehicleWheel wheel = new VxVehicleWheel(this, slot.getName(), settings, slot, def);
        this.wheels.add(wheel);
        this.addPart(wheel);
    }

    // --- Synchronization ---

    @Override
    protected void defineSyncData(VxSynchronizedData.Builder builder) {
        super.defineSyncData(builder); // Defines Speed sync
        builder.define(SYNC_RPM, 0.0f);
        builder.define(SYNC_GEAR, 0);
        builder.define(SYNC_THROTTLE, 0.0f);
        builder.define(SYNC_STEER, 0.0f);
        builder.define(SYNC_WHEELS, new ArrayList<>());
    }

    /**
     * Called when synchronized data is received from the server (on Client).
     * Used to push visual state updates to the wheel parts.
     */
    @Override
    public void onSyncedDataUpdated(VxServerAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);

        if (accessor.equals(SYNC_WHEELS)) {
            List<VxVehicleWheelState> states = getSynchronizedData().get(SYNC_WHEELS);
            int count = Math.min(states.size(), wheels.size());
            for (int i = 0; i < count; i++) {
                // Update client interpolation targets for rendering
                wheels.get(i).updateClientTarget(
                        states.get(i).rotation(),
                        states.get(i).steer(),
                        states.get(i).suspension()
                );
            }
        }
    }

    // --- Physics Lifecycle ---

    /**
     * Called when the body is successfully added to the physics world.
     * Initializes the Jolt vehicle constraint and collision tester.
     */
    @Override
    public void onBodyAdded(VxPhysicsWorld world) {
        super.onBodyAdded(world);

        Body body = VxJoltBridge.INSTANCE.getJoltBody(world, getBodyId());
        if (body == null) return;

        // 1. Create native Jolt objects
        this.collisionTester = createCollisionTester();
        this.constraintSettings = createConstraintSettings(body);

        // 2. Add wheels to Jolt settings
        for (VxVehicleWheel wheel : this.wheels) {
            this.constraintSettings.addWheels(wheel.getSettings());
        }

        // 3. Create and configure Constraint
        this.constraint = new VehicleConstraint(body, this.constraintSettings);
        this.constraint.setVehicleCollisionTester(this.collisionTester);

        // 4. Register with system
        world.getPhysicsSystem().addConstraint(constraint);
        world.getPhysicsSystem().addStepListener(constraint.getStepListener());

        // 5. Initialize controller cache
        this.updateJoltControllerReference();
    }

    /**
     * Called when the body is removed from the physics world.
     * Cleans up native Jolt resources to prevent memory leaks.
     */
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

    /**
     * Main physics update loop.
     * Handles input processing, transmission logic, and state synchronization.
     */
    @Override
    public void onPhysicsTick(VxPhysicsWorld world) {
        super.onPhysicsTick(world);
        if (constraint == null) return;

        // Keep body awake if player is providing input
        if (!this.currentInput.equals(VxMountInput.NEUTRAL)) {
            BodyInterface bodyInterface = world.getPhysicsSystem().getBodyInterface();
            if (!bodyInterface.isActive(getBodyId())) {
                bodyInterface.activateBody(getBodyId());
            }
        }

        Body joltBody = VxJoltBridge.INSTANCE.getJoltBody(world, getBodyId());

        // Only run logic if the physics body is valid and active
        if (joltBody != null && joltBody.isActive() && constraint.getController() instanceof WheeledVehicleController controller) {

            float dt = 1.0f / 60.0f; // Fixed timestep approximation

            // 1. Process and Smooth Driver Inputs
            float targetThrottle = currentInput.getForwardAmount();
            this.smoothedThrottle = Mth.lerp(dt * 5.0f, smoothedThrottle, targetThrottle);

            float targetSteer = currentInput.getRightAmount();
            steeringHelper.setTargetAngle(targetSteer);
            steeringHelper.update(dt);
            this.smoothedSteer = steeringHelper.getCurrentAngle();

            // Create a temporary input state with smoothed values if strictly necessary,
            // but currently passing raw flags + smoothed steering happens inside the module logic.

            // 2. Determine signed forward speed for transmission logic
            // Dot product of velocity vector and vehicle forward vector gives signed speed.
            Vec3 vel = joltBody.getLinearVelocity();
            Vec3 forward = constraint.getVehicleBody().getRotation().rotateAxisZ(); // Assuming Z is forward
            float speedSigned = vel.dot(forward);

            // 3. Update Transmission Logic
            // This applies the calculated inputs directly to the Jolt controller
            transmissionModule.update(dt, currentInput, speedSigned, controller);

            // 4. Retrieve State from Jolt for Synchronization
            // Jolt manages the actual engine RPM and automatic gear selection
            VehicleEngine engine = controller.getEngine();
            int currentGear = transmissionModule.getDisplayGear(); // Use logic gear for manual, or Jolt gear for auto
            float currentRpm = engine.getCurrentRpm();

            // 5. Update Wheel Parts
            List<VxVehicleWheelState> wheelStates = new ArrayList<>(wheels.size());
            for (int i = 0; i < wheels.size(); i++) {
                Wheel w = constraint.getWheel(i);

                // Update server-side logic state (for hitboxes etc)
                wheels.get(i).updatePhysicsState(w.getRotationAngle(), w.getSteerAngle(), w.getSuspensionLength(), w.hasContact());

                // Collect visual state for network
                wheelStates.add(new VxVehicleWheelState(w.getRotationAngle(), w.getSteerAngle(), w.getSuspensionLength()));
            }

            // 6. Send Data to Clients
            setServerData(SYNC_RPM, currentRpm);
            setServerData(SYNC_GEAR, currentGear);
            setServerData(SYNC_THROTTLE, smoothedThrottle);
            setServerData(SYNC_STEER, smoothedSteer);
            setServerData(SYNC_WHEELS, wheelStates);
        }
    }

    /**
     * Handles input updates received from the controlling player.
     *
     * @param player The player sending the input.
     * @param input  The new input state.
     */
    @Override
    public void handleDriverInput(ServerPlayer player, VxMountInput input) {
        this.currentInput = input;
    }

    /**
     * Gets the list of wheel parts.
     *
     * @return The list of wheels.
     */
    public List<VxVehicleWheel> getWheels() {
        return wheels;
    }
}