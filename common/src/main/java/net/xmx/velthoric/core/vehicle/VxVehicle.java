/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.vehicle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.phys.AABB;
import net.xmx.velthoric.core.mounting.VxMountable;
import net.xmx.velthoric.core.mounting.seat.VxSeat;
import net.xmx.velthoric.core.physics.VxJoltBridge;
import net.xmx.velthoric.core.body.VxRemovalReason;
import net.xmx.velthoric.core.network.synchronization.VxDataSerializers;
import net.xmx.velthoric.core.network.synchronization.VxSynchronizedData;
import net.xmx.velthoric.core.network.synchronization.accessor.VxServerAccessor;
import net.xmx.velthoric.core.body.registry.VxBodyType;
import net.xmx.velthoric.core.body.type.VxRigidBody;
import net.xmx.velthoric.core.vehicle.config.VxVehicleConfig;
import net.xmx.velthoric.core.vehicle.part.VxPart;
import net.xmx.velthoric.core.vehicle.part.definition.VxSeatDefinition;
import net.xmx.velthoric.core.vehicle.part.impl.VxVehicleSeat;
import net.xmx.velthoric.core.vehicle.part.slot.VehicleSeatSlot;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import org.joml.Vector3f;

import java.util.*;

/**
 * The core vehicle base class.
 * <p>
 * This class serves as the foundation for all physics-driven vehicles (Cars, Boats, Planes, Tanks).
 * It manages the fundamental lifecycle of the vehicle, including:
 * <ul>
 *     <li>Component parts management (via {@link VxPart}).</li>
 *     <li>Seating and mounting logic (via {@link VxSeat}).</li>
 *     <li>Base network synchronization (Speed).</li>
 * </ul>
 * <p>
 * <b>Note:</b> This class is agnostic to the method of propulsion. It does not know about
 * engines, wheels, or tracks. Subclasses like {@link VxWheeledVehicle} add those specifics.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxVehicle extends VxRigidBody implements VxMountable {

    /**
     * Synchronizes the vehicle's linear speed in km/h.
     * This is common to all vehicle types.
     */
    public static final VxServerAccessor<Float> SYNC_SPEED = VxServerAccessor.create(VxVehicle.class, VxDataSerializers.FLOAT);

    /**
     * The configuration for this vehicle instance.
     * Contains static data like mass, dimensions, and slots.
     */
    protected final VxVehicleConfig config;

    // --- Part Management ---

    /**
     * A map of all parts (wheels, seats, etc.) by their UUID for fast network lookups.
     */
    protected final Map<UUID, VxPart> parts = new HashMap<>();

    /**
     * An ordered list of parts for deterministic iteration (rendering/raycasting).
     */
    protected final List<VxPart> partList = new ArrayList<>();

    /**
     * A typed list of seats for efficient iteration during interaction or mounting updates.
     */
    protected final List<VxVehicleSeat> seats = new ArrayList<>();

    // --- State ---

    /**
     * The current speed in Kilometers per Hour.
     */
    private float speedKmh = 0.0f;

    /**
     * Server-side constructor.
     *
     * @param type   The body type registry entry.
     * @param world  The physics world.
     * @param id     The unique entity ID.
     * @param config The vehicle configuration data.
     */
    public VxVehicle(VxBodyType<? extends VxVehicle> type, VxPhysicsWorld world, UUID id, VxVehicleConfig config) {
        super(type, world, id);
        this.config = config;
        this.initializeBaseComponents();
    }

    /**
     * Client-side constructor.
     *
     * @param type   The body type registry entry.
     * @param id     The unique entity ID.
     * @param config The vehicle configuration data.
     */
    @Environment(EnvType.CLIENT)
    public VxVehicle(VxBodyType<? extends VxVehicle> type, UUID id, VxVehicleConfig config) {
        super(type, id);
        this.config = config;
        this.initializeBaseComponents();
    }

    // --- Abstract Methods ---

    /**
     * Resolves a seat definition ID to an actual definition object.
     * Subclasses or registries must implement this to provide the seat data.
     *
     * @param seatId The resource ID of the seat.
     * @return The definition, or null if not found.
     */
    protected abstract VxSeatDefinition resolveSeatDefinition(String seatId);

    // --- Initialization Logic ---

    /**
     * Initializes base components defined in {@link VxVehicleConfig}.
     * Primarily handles seat mounting.
     */
    private void initializeBaseComponents() {
        // Resolve the default seat type for this vehicle
        VxSeatDefinition defaultDef = resolveSeatDefinition(config.getDefaultSeatId());

        // Safety fallback
        if (defaultDef == null) {
            defaultDef = VxSeatDefinition.missing();
        }

        // Mount a seat for every slot defined in the config
        for (VehicleSeatSlot slot : config.getSeatSlots()) {
            this.mountSeat(slot, defaultDef);
        }
    }

    /**
     * Mounts a seat to the vehicle based on the slot configuration and a definition.
     * <p>
     * This creates both the logical seat (for mounting) and the visual part.
     *
     * @param slot The seat slot configuration.
     * @param def  The seat definition.
     */
    protected void mountSeat(VehicleSeatSlot slot, VxSeatDefinition def) {
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

    // --- Interface Implementation ---

    /**
     * Called by the MountingManager when the body is added to the world.
     * Populates the seat builder using the pre-initialized seats.
     */
    @Override
    public void defineSeats(VxSeat.Builder builder) {
        for (VxVehicleSeat seatPart : this.seats) {
            builder.addSeat(seatPart.getSeatData());
        }
    }

    // --- Synchronization ---

    @Override
    protected void defineSyncData(VxSynchronizedData.Builder builder) {
        // Define base synchronized data (Speed)
        builder.define(SYNC_SPEED, 0.0f);
    }

    @Override
    public void onSyncedDataUpdated(VxServerAccessor<?> accessor) {
        if (accessor.equals(SYNC_SPEED)) {
            this.speedKmh = getSynchronizedData().get(SYNC_SPEED);
        }
    }

    // --- Physics Lifecycle ---

    /**
     * Executes the physics logic for this vehicle every tick.
     * Base implementation updates the speed calculation.
     *
     * @param world The physics world.
     */
    @Override
    public void onPhysicsTick(VxPhysicsWorld world) {
        super.onPhysicsTick(world);

        // Base physics updates (Speed calculation)
        var body = VxJoltBridge.INSTANCE.getJoltBody(world, getBodyId());
        if (body != null && body.isActive()) {
            this.speedKmh = body.getLinearVelocity().length() * 3.6f;
            setServerData(SYNC_SPEED, this.speedKmh);
        }
    }

    @Override
    public void onBodyRemoved(VxPhysicsWorld world, VxRemovalReason reason) {
        super.onBodyRemoved(world, reason);
        // Seats are automatically removed by the MountingManager based on the body UUID.
    }

    // --- Runtime Equipment Swapping ---

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

    // --- Getters ---

    public float getSpeedKmh() {
        return speedKmh;
    }

    public VxVehicleConfig getConfig() {
        return config;
    }

    /**
     * Gets a list of all parts attached to this vehicle.
     *
     * @return The unmodifiable list of parts.
     */
    public List<VxPart> getParts() {
        return Collections.unmodifiableList(this.partList);
    }
}