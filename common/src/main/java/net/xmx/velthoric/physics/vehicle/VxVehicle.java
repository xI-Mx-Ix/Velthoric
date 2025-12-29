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
import net.minecraft.world.phys.AABB;
import net.xmx.velthoric.bridge.mounting.VxMountable;
import net.xmx.velthoric.bridge.mounting.input.VxMountInput;
import net.xmx.velthoric.bridge.mounting.seat.VxSeat;
import net.xmx.velthoric.physics.body.manager.VxJoltBridge;
import net.xmx.velthoric.physics.body.manager.VxRemovalReason;
import net.xmx.velthoric.physics.body.registry.VxBodyType;
import net.xmx.velthoric.physics.body.network.synchronization.VxSynchronizedData;
import net.xmx.velthoric.physics.body.type.VxRigidBody;
import net.xmx.velthoric.physics.vehicle.component.VxSteering;
import net.xmx.velthoric.physics.vehicle.component.VxVehicleEngine;
import net.xmx.velthoric.physics.vehicle.component.VxVehicleTransmission;
import net.xmx.velthoric.physics.vehicle.data.VxVehicleData;
import net.xmx.velthoric.physics.vehicle.data.component.VxSeatDefinition;
import net.xmx.velthoric.physics.vehicle.data.component.VxWheelDefinition;
import net.xmx.velthoric.physics.vehicle.data.slot.VehicleSeatSlot;
import net.xmx.velthoric.physics.vehicle.data.slot.VehicleWheelSlot;
import net.xmx.velthoric.physics.vehicle.part.VxPart;
import net.xmx.velthoric.physics.vehicle.part.impl.VxVehicleSeat;
import net.xmx.velthoric.physics.vehicle.part.impl.VxVehicleWheel;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.joml.Vector3f;

import java.util.*;

/**
 * The core modular vehicle class.
 * <p>
 * This class serves as the base for all physics-driven vehicles (Cars, Motorcycles, etc.).
 * It manages the lifecycle of the engine, transmission, wheels, and seats using a
 * data-driven architecture defined by {@link VxVehicleData}.
 * <p>
 * It acts as the bridge between the high-level game logic (mounting, inputs, rendering)
 * and the low-level Jolt physics simulation (constraints, forces).
 *
 * @author xI-Mx-Ix
 */
public abstract class VxVehicle extends VxRigidBody implements VxMountable {

    // --- Core Data ---

    /**
     * The static definition data for this vehicle.
     * Contains chassis configuration, engine specs, and slot definitions.
     */
    protected final VxVehicleData vehicleData;

    // --- Logical Components ---

    /**
     * The simulation component for the engine (RPM, Torque).
     */
    protected VxVehicleEngine engine;

    /**
     * The simulation component for the transmission (Gear ratios, Shifting).
     */
    protected VxVehicleTransmission transmission;

    /**
     * Helper for smoothing steering inputs.
     */
    protected final VxSteering steeringHelper = new VxSteering(2.0f);

    // --- Part Management ---

    /**
     * A map of all parts (wheels, seats) by their UUID for fast network lookups.
     */
    protected final Map<UUID, VxPart> parts = new HashMap<>();

    /**
     * An ordered list of parts for deterministic iteration (rendering/raycasting).
     */
    protected final List<VxPart> partList = new ArrayList<>();

    /**
     * A typed list of wheels for efficient physics step updates.
     */
    protected final List<VxVehicleWheel> wheels = new ArrayList<>();

    /**
     * A typed list of seats for efficient iteration during interaction or mounting updates.
     */
    protected final List<VxVehicleSeat> seats = new ArrayList<>();

    // --- Physics ---

    /**
     * The main Jolt constraint that handles vehicle physics (suspension, friction).
     */
    protected VehicleConstraint constraint;

    /**
     * Handles collision detection logic for the vehicle wheels (Raycast vs CylinderCast).
     */
    protected VehicleCollisionTester collisionTester;

    /**
     * Configuration settings passed to the Jolt physics engine.
     */
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
     * @param data  The data defining this vehicle's properties.
     */
    public VxVehicle(VxBodyType<? extends VxVehicle> type, VxPhysicsWorld world, UUID id, VxVehicleData data) {
        super(type, world, id);
        this.vehicleData = data;
        this.initializeComponents();
    }

    /**
     * Client-side constructor.
     *
     * @param type The body type.
     * @param id   The unique ID.
     * @param data The data defining this vehicle's properties.
     */
    @Environment(EnvType.CLIENT)
    public VxVehicle(VxBodyType<? extends VxVehicle> type, UUID id, VxVehicleData data) {
        super(type, id);
        this.vehicleData = data;
        this.initializeComponents();
    }

    // --- Abstract Methods (Implementation Specific) ---

    /**
     * Resolves a wheel definition ID to an actual definition object.
     * Subclasses or registries must implement this to provide the visual/physical wheel data.
     *
     * @param wheelId The resource ID of the wheel.
     * @return The definition, or null.
     */
    protected abstract VxWheelDefinition resolveWheelDefinition(String wheelId);

    /**
     * Resolves a seat definition ID to an actual definition object.
     *
     * @param seatId The resource ID of the seat.
     * @return The definition, or null.
     */
    protected abstract VxSeatDefinition resolveSeatDefinition(String seatId);

    /**
     * Defines the collision tester to be used by this vehicle.
     *
     * @return The collision tester instance.
     */
    protected abstract VehicleCollisionTester createCollisionTester();

    /**
     * Creates the Jolt Constraint Settings from the vehicle data.
     *
     * @param body The Jolt body.
     * @return The configured constraint settings.
     */
    protected abstract VehicleConstraintSettings createConstraintSettings(Body body);

    /**
     * Hooks up the specific Jolt controller reference after the constraint is created.
     * This is necessary because Jolt separates Car and Motorcycle controllers.
     */
    protected abstract void updateJoltControllerReference();

    // --- Initialization Logic ---

    /**
     * Initializes all logical components (Powertrain, Wheels, Seats) based on {@link #vehicleData}.
     * This is called immediately during construction.
     */
    private void initializeComponents() {
        // 1. Initialize Powertrain (Engine & Transmission)
        this.initializePowertrain();

        // 2. Initialize Wheels
        this.initializeWheels();

        // 3. Initialize Seats
        this.initializeSeats();
    }

    private void initializePowertrain() {
        this.engine = new VxVehicleEngine(
                vehicleData.getEngine().getMaxTorque(),
                vehicleData.getEngine().getMinRpm(),
                vehicleData.getEngine().getMaxRpm()
        );

        this.transmission = new VxVehicleTransmission(
                vehicleData.getTransmission().getGearRatios(),
                vehicleData.getTransmission().getReverseRatio(),
                vehicleData.getTransmission().getSwitchTime()
        );
    }

    private void initializeWheels() {
        // Resolve the default wheel type for this vehicle
        VxWheelDefinition defaultDef = resolveWheelDefinition(vehicleData.getDefaultWheelId());

        // Safety fallback if the registry lookup fails
        if (defaultDef == null) {
            defaultDef = VxWheelDefinition.missing();
        }

        // Mount a wheel for every slot defined in the chassis data
        for (VehicleWheelSlot slot : vehicleData.getWheelSlots()) {
            this.mountWheel(slot, defaultDef);
        }
    }

    private void initializeSeats() {
        // Resolve the default seat type for this vehicle
        VxSeatDefinition defaultDef = resolveSeatDefinition(vehicleData.getDefaultSeatId());

        // Safety fallback
        if (defaultDef == null) {
            defaultDef = VxSeatDefinition.missing();
        }

        // Mount a seat for every slot defined in the chassis data
        for (VehicleSeatSlot slot : vehicleData.getSeatSlots()) {
            this.mountSeat(slot, defaultDef);
        }
    }

    /**
     * Mounts a wheel to the vehicle based on the slot configuration and a definition.
     * This method creates the physical settings and the logical part.
     *
     * @param slot The chassis slot configuration.
     * @param def  The physical/visual wheel definition.
     */
    private void mountWheel(VehicleWheelSlot slot, VxWheelDefinition def) {
        WheelSettingsWv settings = new WheelSettingsWv();
        Vector3f pos = slot.getPosition();

        // Position & Suspension (Derived from Slot)
        settings.setPosition(new com.github.stephengold.joltjni.Vec3(pos.x, pos.y, pos.z));
        settings.setSuspensionMinLength(slot.getSuspensionMinLength());
        settings.setSuspensionMaxLength(slot.getSuspensionMaxLength());
        settings.getSuspensionSpring().setFrequency(slot.getSuspensionFrequency());
        settings.getSuspensionSpring().setDamping(slot.getSuspensionDamping());
        settings.setMaxBrakeTorque(slot.getMaxBrakeTorque());

        // Steering Logic (Derived from Slot)
        if (slot.isSteerable()) {
            settings.setMaxSteerAngle((float) Math.toRadians(slot.getMaxSteerAngleDegrees()));
        } else {
            settings.setMaxSteerAngle(0f);
        }

        // Physical Dimensions (Derived from Definition)
        settings.setRadius(def.radius());
        settings.setWidth(def.width());

        // Create the logical Part
        VxVehicleWheel wheel = new VxVehicleWheel(this, slot.getName(), settings, slot, def);

        // Register internally
        this.wheels.add(wheel);
        this.addPart(wheel);
    }

    /**
     * Mounts a seat to the vehicle based on the slot configuration and a definition.
     * <p>
     * This method creates the logical seat data and registers it internally.
     * The actual registration with the MountingManager happens later via {@link #defineSeats(VxSeat.Builder)}.
     *
     * @param slot The chassis slot configuration.
     * @param def  The visual seat definition.
     */
    private void mountSeat(VehicleSeatSlot slot, VxSeatDefinition def) {
        // Create the logical interaction AABB based on the definition size
        Vector3f size = def.size();
        AABB aabb = new AABB(-size.x / 2, -size.y / 2, -size.z / 2, size.x / 2, size.y / 2, size.z / 2);

        // Create the seat logic object (handles mounting interactions)
        VxSeat seatLogic = new VxSeat(this.getPhysicsId(), slot.getName(), aabb, slot.getPosition(), slot.isDriver());

        // Create the vehicle part (handles visual rendering and definition state)
        VxVehicleSeat seatPart = new VxVehicleSeat(this, seatLogic, slot, def);

        // Register internally to the vehicle structure
        this.seats.add(seatPart);
        this.addPart(seatPart);
    }

    /**
     * Implementation of the VxMountable interface method.
     * <p>
     * This method is called by the MountingManager when the body is added to the world.
     * We populate the builder using the seats that were already created in the constructor.
     *
     * @param builder The seat builder to populate.
     */
    @Override
    public void defineSeats(VxSeat.Builder builder) {
        for (VxVehicleSeat seatPart : this.seats) {
            builder.addSeat(seatPart.getSeatData());
        }
    }

    /**
     * Adds a generic part to the vehicle and registers it for lookup.
     *
     * @param part The part to add.
     */
    public void addPart(VxPart part) {
        this.parts.put(part.getId(), part);
        this.partList.add(part);
    }

    /**
     * Retrieves a part by its unique UUID.
     * Used by the packet handler to find the part to interact with.
     *
     * @param partId The UUID of the part.
     * @return The part, or null if not found.
     */
    public VxPart getPart(UUID partId) {
        return this.parts.get(partId);
    }

    // --- Physics Lifecycle ---

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

        // Cleanup Jolt Physics constraints
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
        // Seats are automatically removed by the MountingManager based on the body UUID.
    }

    /**
     * Executes the physics logic for this vehicle every tick.
     * Handles speed calculation, transmission logic (Auto vs Manual), engine updates,
     * and synchronizes the physics state to the local component representation.
     *
     * @param world The physics world.
     */
    @Override
    public void onPhysicsTick(VxPhysicsWorld world) {
        super.onPhysicsTick(world);
        if (constraint == null) return;

        // 1. Check if we have any driver input to activate the body.
        if (!this.currentInput.equals(VxMountInput.NEUTRAL)) {
            BodyInterface bodyInterface = world.getPhysicsSystem().getBodyInterface();
            if (!bodyInterface.isActive(getBodyId())) {
                bodyInterface.activateBody(getBodyId());
            }
        }

        // Jolt typically steps at 60Hz. Using the correct delta time is crucial for transmission timers.
        float dt = 1.0f / 60.0f;

        // 2. Process Inputs & Steering Interpolation
        this.processDriverInput(dt);

        // 3. Physics Simulation Interface
        Body joltBody = VxJoltBridge.INSTANCE.getJoltBody(world, getBodyId());

        if (joltBody != null && joltBody.isActive()) {

            // Calculate Speed in KM/H
            com.github.stephengold.joltjni.Vec3 vel = joltBody.getLinearVelocity();
            this.speedKmh = vel.length() * 3.6f;

            // --- Transmission Logic ---
            if (vehicleData.getTransmission().getMode() == ETransmissionMode.Manual) {
                // Update transmission timers (clutch delay, etc.)
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

            } else {
                // Automatic: Jolt handles shifting internally.
                // We synchronize the 'transmission' object so the HUD displays the correct gear.
                if (constraint.getController() instanceof WheeledVehicleController wvc) {
                    int joltGear = wvc.getTransmission().getCurrentGear();
                    transmission.setSynchronizedGear(joltGear);
                }

                // Reset edge detection flags to prevent buffered inputs when switching modes
                wasShiftUpPressed = false;
                wasShiftDownPressed = false;
            }

            // Calculate average Wheel RPM for Engine simulation
            float avgWheelRpm = 0;
            int poweredCount = 0;
            for (int i = 0; i < wheels.size(); i++) {
                if (wheels.get(i).isPowered()) {
                    avgWheelRpm += Math.abs(constraint.getWheel(i).getAngularVelocity()) * 9.55f;
                    poweredCount++;
                }
            }
            if (poweredCount > 0) avgWheelRpm /= poweredCount;

            // Update Engine Physics
            // If Automatic, clutch is always engaged unless Jolt is shifting.
            boolean clutchEngaged = (vehicleData.getTransmission().getMode() == ETransmissionMode.Auto) || !transmission.isShifting();
            engine.update(Math.abs(inputThrottle), avgWheelRpm, transmission.getCurrentRatio(), clutchEngaged);

            // 4. Sync Physics State to Local Wheel Components
            for (int i = 0; i < wheels.size(); i++) {
                Wheel w = constraint.getWheel(i);
                wheels.get(i).updatePhysicsState(w.getRotationAngle(), w.getSteerAngle(), w.getSuspensionLength(), w.hasContact());
            }

            // 5. Update Jolt Controller Input
            this.updateJoltController();

            // Mark for network sync
            this.isStateDirty = true;
        }
    }

    /**
     * Applies the calculated inputs to the underlying Jolt Vehicle Controller.
     * <p>
     * Correctly maps Forward/Backward inputs to Gas/Brake depending on the current gear.
     */
    protected void updateJoltController() {
        if (constraint == null) return;

        // Check if the controller is a WheeledVehicleController (Parent of Car & Motorcycle controllers)
        if (constraint.getController() instanceof WheeledVehicleController controller) {

            float throttle = getInputThrottle(); // Raw input (-1.0 to 1.0)
            float brake = 0.0f;

            int gear = transmission.getGear();

            // --- Gas vs Brake Logic ---
            // Forward Gear (>0) but Input is Backward (<0) -> Brake
            if (gear > 0 && throttle < 0) {
                brake = Math.abs(throttle);
                throttle = 0;
            }
            // Reverse Gear (-1) but Input is Forward (>0) -> Brake
            else if (gear == -1 && throttle > 0) {
                brake = throttle;
                throttle = 0;
            }

            // Hill hold / Auto brake when stopping
            if (throttle == 0 && brake == 0 && Math.abs(getSpeedKmh()) < 1.0f) {
                brake = 1.0f; // Prevent sliding when idle
            }

            float handbrake = currentInput.hasAction(VxMountInput.FLAG_HANDBRAKE) ? 1.0f : 0.0f;

            // Apply calculated inputs.
            controller.setDriverInput(Math.abs(throttle), getInputSteer(), brake, handbrake);

            // Apply manual transmission state if configured
            if (vehicleData.getTransmission().getMode() == ETransmissionMode.Manual) {
                // Calculate clutch engagement: 0.0f if currently shifting, 1.0f otherwise
                float clutch = transmission.isShifting() ? 0.0f : 1.0f;

                // Sync the logical Java gear state to the Jolt physics controller
                controller.getTransmission().set(gear, clutch);
            }
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

    @Override
    public void handleDriverInput(ServerPlayer player, VxMountInput input) {
        // Store the input; processing happens during the physics tick.
        this.currentInput = input;

        int bodyId = getBodyId();
        if (bodyId != 0) this.physicsWorld.getPhysicsSystem().getBodyInterface().activateBody(bodyId);
    }

    // --- Runtime Equipment Swapping ---

    /**
     * Runtime method to swap a wheel definition on a specific slot.
     *
     * @param slotName The name of the slot (e.g., "front_left").
     * @param newDef   The new wheel definition to apply.
     */
    public void equipWheel(String slotName, VxWheelDefinition newDef) {
        for (VxVehicleWheel wheel : this.wheels) {
            if (wheel.getSlot().getName().equals(slotName)) {
                // Update Logical Definition (Visuals)
                wheel.setDefinition(newDef);

                // Update Physical Settings (Dimensions)
                wheel.getSettings().setRadius(newDef.radius());
                wheel.getSettings().setWidth(newDef.width());
                return;
            }
        }
    }

    /**
     * Runtime method to swap a seat definition on a specific slot.
     *
     * @param slotName The name of the seat slot.
     * @param newDef   The new seat definition to apply.
     */
    public void equipSeat(String slotName, VxSeatDefinition newDef) {
        for (VxVehicleSeat seat : this.seats) {
            if (seat.getSlot().getName().equals(slotName)) {
                seat.setDefinition(newDef);
                return;
            }
        }
    }

    // --- Sync & Getters ---

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
        // Implement default sync data if required
    }

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

    public VxVehicleData getData() {
        return vehicleData;
    }

    /**
     * Gets a list of all parts attached to this vehicle (wheels, seats, etc.).
     * This list is ordered and suitable for iteration during rendering or raycasting.
     *
     * @return The list of vehicle parts.
     */
    public List<VxPart> getParts() {
        return Collections.unmodifiableList(this.partList);
    }
}