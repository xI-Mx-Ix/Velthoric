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
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.math.VxOBB;
import net.xmx.velthoric.physics.body.client.VxRenderState;
import net.xmx.velthoric.physics.body.manager.VxJoltBridge;
import net.xmx.velthoric.physics.body.manager.VxRemovalReason;
import net.xmx.velthoric.physics.body.registry.VxBodyType;
import net.xmx.velthoric.physics.body.sync.VxSynchronizedData;
import net.xmx.velthoric.physics.body.type.VxRigidBody;
import net.xmx.velthoric.physics.mounting.VxMountable;
import net.xmx.velthoric.physics.mounting.input.VxMountInput;
import net.xmx.velthoric.physics.mounting.seat.VxSeat;
import net.xmx.velthoric.physics.vehicle.component.VxSteering;
import net.xmx.velthoric.physics.vehicle.component.VxVehicleEngine;
import net.xmx.velthoric.physics.vehicle.component.VxVehicleTransmission;
import net.xmx.velthoric.physics.vehicle.config.VxVehicleConfig;
import net.xmx.velthoric.physics.vehicle.part.VxPart;
import net.xmx.velthoric.physics.vehicle.part.impl.VxVehicleSeat;
import net.xmx.velthoric.physics.vehicle.part.impl.VxVehicleWheel;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.*;

/**
 * The core modular vehicle class.
 * This class serves as the base for all physics-driven vehicles, handling
 * component management (engine, transmission, wheels), input processing,
 * and Jolt physics synchronization.
 * <p>
 * It also acts as the central manager for {@link VxPart}s, delegating interaction logic
 * and organizing the modular structure of the vehicle.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxVehicle extends VxRigidBody implements VxMountable {

    // --- Components ---
    protected VxVehicleConfig config;
    protected VxVehicleEngine engine;
    protected VxVehicleTransmission transmission;
    protected final VxSteering steeringHelper = new VxSteering(2.0f);

    /**
     * The modular parts that make up this vehicle.
     * Includes wheels, seats, doors, etc.
     * Mapped by their unique UUID for O(1) network lookup.
     */
    protected final Map<UUID, VxPart> parts = new HashMap<>();

    /**
     * An ordered list of parts, used for consistent iteration during rendering or raycasting.
     */
    protected final List<VxPart> partList = new ArrayList<>();

    // Helper list for efficient physics updates; these are also contained in 'parts'.
    protected final List<VxVehicleWheel> wheels = new ArrayList<>();

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
        this.initializeSeats();
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
        this.initializeSeats();
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
     * Ensures that default components are only created if the specific vehicle implementation
     * has not already defined them.
     */
    private void initializeComponents() {
        List<VxVehicleConfig.WheelInfo> configWheels = config.getWheels();

        for (int i = 0; i < configWheels.size(); i++) {
            VxVehicleConfig.WheelInfo info = configWheels.get(i);
            WheelSettingsWv ws = new WheelSettingsWv();
            ws.setPosition(info.position());
            ws.setRadius(info.radius());
            ws.setWidth(info.width());
            // Sets default suspension limits, which may be adjusted later in onBodyAdded.
            ws.setSuspensionMinLength(0.2f);
            ws.setSuspensionMaxLength(0.5f);

            // Generate a deterministic name for the wheel based on its index.
            // This ensures the UUID generated inside VxPart is identical on Client and Server.
            String wheelName = "wheel_" + i;

            VxVehicleWheel wheel = new VxVehicleWheel(this, wheelName, ws, info.powered(), info.steerable());
            this.wheels.add(wheel);
            this.addPart(wheel);
        }

        // Initialize a default engine only if one has not been provided by the subclass configuration.
        if (this.engine == null) {
            this.engine = new VxVehicleEngine(500f, 800f, 7000f);
        }

        // Initialize a default transmission only if one has not been provided by the subclass configuration.
        if (this.transmission == null) {
            this.transmission = new VxVehicleTransmission(new float[]{3.5f, 2.0f, 1.4f, 1.0f}, -3.0f);
        }
    }

    /**
     * Initializes seats defined in {@link #defineSeats(VxSeat.Builder)} and adds them as parts.
     */
    private void initializeSeats() {
        VxSeat.Builder builder = new VxSeat.Builder();
        defineSeats(builder);
        List<VxSeat> seats = builder.build();
        for (VxSeat seat : seats) {
            this.addPart(new VxVehicleSeat(this, seat));
        }
    }

    /**
     * Adds a generic part to the vehicle.
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

    /**
     * Performs a raycast against all parts of this vehicle to determine if the player
     * is interacting with a specific component (e.g., clicking a seat or door).
     *
     * @param start        The ray start position (eye pos).
     * @param end          The ray end position (reach vector).
     * @param vehicleState The interpolated render state of the vehicle for accurate OBB placement.
     * @return An Optional containing the hit part, or empty if none hit.
     */
    public Optional<VxPart> pickPart(Vec3 start, Vec3 end, VxRenderState vehicleState) {
        VxPart closestPart = null;
        double closestDistSq = Double.MAX_VALUE;

        // Iterate over the list for deterministic order
        for (VxPart part : this.partList) {
            VxOBB partOBB = part.getGlobalOBB(vehicleState);
            Optional<Vec3> hit = partOBB.clip(start, end);

            if (hit.isPresent()) {
                double distSq = start.distanceToSqr(hit.get());
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    closestPart = part;
                }
            }
        }
        return Optional.ofNullable(closestPart);
    }

    public List<VxPart> getParts() {
        return partList;
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

        // 1. Check if we have any driver input.
        if (!this.currentInput.equals(VxMountInput.NEUTRAL)) {
            BodyInterface bodyInterface = world.getPhysicsSystem().getBodyInterface();
            if (!bodyInterface.isActive(getBodyId())) {
                bodyInterface.activateBody(getBodyId());
            }
        }

        float dt = 1.0f / 20.0f;

        // 2. Process Inputs & Steering Interpolation
        this.processDriverInput(dt);

        // 3. Physics Simulation Interface
        Body joltBody = VxJoltBridge.INSTANCE.getJoltBody(world, getBodyId());

        if (joltBody != null && joltBody.isActive()) {

            // Calculate Speed in KM/H
            com.github.stephengold.joltjni.Vec3 vel = joltBody.getLinearVelocity();
            this.speedKmh = vel.length() * 3.6f;

            // --- Transmission Logic ---
            if (config.getTransmissionMode() == ETransmissionMode.Manual) {
                // We handle shift timers and player inputs in Java.
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
                // Jolt handles shifting internally. We ignore player shift inputs.
                // We must synchronize the 'transmission' object with Jolt so the HUD displays the correct gear.
                if (constraint.getController() instanceof WheeledVehicleController wvc) {
                    // Get the current gear index calculated by the physics engine
                    int joltGear = wvc.getTransmission().getCurrentGear();
                    transmission.setSynchronizedGear(joltGear);
                }

                // Reset edge detection flags to prevent buffered inputs when switching back to manual
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
            // If Automatic, we assume "isClutched" is true (engaged) unless Jolt is shifting (which we can't easily read here),
            // so we pass 'true' for simplicity, or check transmission.isShifting() if in Manual.
            boolean clutchEngaged = (config.getTransmissionMode() == ETransmissionMode.Auto) || !transmission.isShifting();

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
     * Since MotorcycleController inherits from WheeledVehicleController in Jolt,
     * this logic is shared across both vehicle types to adhere to DRY principles.
     */
    protected void updateJoltController() {
        if (constraint == null) return;

        // Check if the controller is a WheeledVehicleController (Parent of Car & Motorcycle controllers)
        if (constraint.getController() instanceof WheeledVehicleController controller) {

            float throttle = getInputThrottle();
            float brake = 0.0f;

            // Determine if we should brake based on gear and throttle direction
            int gear = transmission.getGear();
            if (gear > 0 && throttle < 0) {
                brake = Math.abs(throttle);
                throttle = 0;
            } else if (gear == -1 && throttle > 0) {
                brake = throttle;
                throttle = 0;
            }

            // Hill hold / Auto brake when stopping
            if (throttle == 0 && brake == 0 && Math.abs(getSpeedKmh()) < 1.0f) {
                brake = 0.5f;
            }

            float handbrake = currentInput.hasAction(VxMountInput.FLAG_HANDBRAKE) ? 1.0f : 0.0f;

            // Apply basic inputs (Throttle, Steer, Brake, Handbrake)
            controller.setDriverInput(throttle, getInputSteer(), brake, handbrake);

            // Apply manual transmission state if configured
            if (config.getTransmissionMode() == ETransmissionMode.Manual) {
                // Calculate clutch engagement: 0.0f if currently shifting, 1.0f otherwise
                float clutch = transmission.isShifting() ? 0.0f : 1.0f;

                // Sync the logical Java gear state to the Jolt physics controller
                // We must access the transmission component explicitly to set the gear
                controller.getTransmission().set(gear, clutch);
            }
        }
    }

    @Override
    public void handleDriverInput(ServerPlayer player, VxMountInput input) {
        // Store the input; processing happens during the physics tick.
        this.currentInput = input;

        int bodyId = getBodyId();
        if (bodyId != 0) this.physicsWorld.getPhysicsSystem().getBodyInterface().activateBody(bodyId);
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