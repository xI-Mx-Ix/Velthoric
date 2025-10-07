/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.constraint.manager;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.std.StringStream;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.constraint.VxConstraint;
import net.xmx.velthoric.physics.constraint.persistence.VxConstraintStorage;
import net.xmx.velthoric.physics.object.manager.VxObjectManager;
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle of physics constraints within a physics world.
 * This includes creation, activation, deactivation, and persistence.
 *
 * @author xI-Mx-Ix
 */
public class VxConstraintManager {

    private final VxPhysicsWorld world;
    private final VxObjectManager objectManager;
    private final VxConstraintStorage constraintStorage;
    private final VxDependencyDataSystem dataSystem;
    private final Map<UUID, VxConstraint> activeConstraints = new ConcurrentHashMap<>();

    public VxConstraintManager(VxObjectManager objectManager) {
        this.objectManager = objectManager;
        this.world = objectManager.getPhysicsWorld();
        this.constraintStorage = new VxConstraintStorage(world.getLevel(), this);
        this.dataSystem = new VxDependencyDataSystem(this);
    }

    public void initialize() {
        constraintStorage.initialize();
    }

    public void saveData() {
        activeConstraints.values().forEach(constraintStorage::storeConstraint);
        constraintStorage.saveDirtyRegions();
    }

    public void shutdown() {
        saveData();
        activeConstraints.clear();
        dataSystem.clear();
        constraintStorage.shutdown();
    }

    /**
     * Saves all constraints associated with a given chunk.
     * A constraint is considered to be in a chunk if its first body is in that chunk.
     *
     * @param pos The position of the chunk.
     */
    public void saveConstraintsInChunk(ChunkPos pos) {
        long chunkKey = pos.toLong();
        for (VxConstraint constraint : activeConstraints.values()) {
            VxBody body1 = objectManager.getObject(constraint.getBody1Id());
            if (body1 != null) {
                int index = body1.getDataStoreIndex();
                if (index != -1 && objectManager.getDataStore().chunkKey[index] == chunkKey) {
                    constraintStorage.storeConstraint(constraint);
                }
            }
        }
    }

    /**
     * Handles the unloading of all constraints within a specific chunk.
     * This removes the constraints from the active simulation.
     *
     * @param pos The position of the chunk being unloaded.
     */
    public void onChunkUnload(ChunkPos pos) {
        List<UUID> toRemove = new ArrayList<>();
        long chunkKey = pos.toLong();

        for (VxConstraint constraint : activeConstraints.values()) {
            VxBody body1 = objectManager.getObject(constraint.getBody1Id());
            if (body1 != null) {
                int index = body1.getDataStoreIndex();
                if (index != -1 && objectManager.getDataStore().chunkKey[index] == chunkKey) {
                    toRemove.add(constraint.getConstraintId());
                }
            }
        }

        for (UUID id : toRemove) {
            // Remove from simulation, but do not discard the data from storage.
            removeConstraint(id, false);
        }
    }

    /**
     * Creates a new constraint and schedules it for activation once its dependent bodies are loaded.
     *
     * @param settings The Jolt settings for the constraint.
     * @param body1Id  The UUID of the first body.
     * @param body2Id  The UUID of the second body.
     * @return The newly created VxConstraint instance, or null if inputs are invalid.
     */
    @Nullable
    public VxConstraint createConstraint(TwoBodyConstraintSettings settings, UUID body1Id, UUID body2Id) {
        if (settings == null || body1Id == null || body2Id == null) {
            return null;
        }
        UUID constraintId = UUID.randomUUID();
        VxConstraint constraint = new VxConstraint(constraintId, body1Id, body2Id, settings);
        dataSystem.addPendingConstraint(constraint);
        return constraint;
    }

    /**
     * Adds a constraint from persistent storage to be processed for activation.
     * @param constraint The constraint loaded from storage.
     */
    public void addConstraintFromStorage(VxConstraint constraint) {
        dataSystem.addPendingConstraint(constraint);
    }

    /**
     * Activates a constraint by creating its Jolt counterpart and adding it to the physics system.
     * This method is executed on the physics thread.
     *
     * @param constraint The constraint to activate.
     */
    protected void activateConstraint(VxConstraint constraint) {
        world.execute(() -> {
            VxBody body1 = objectManager.getObject(constraint.getBody1Id());
            VxBody body2 = objectManager.getObject(constraint.getBody2Id());

            if (body1 == null || body2 == null || body1.getBodyId() == 0 || body2.getBodyId() == 0) {
                dataSystem.addPendingConstraint(constraint);
                return;
            }

            try (TwoBodyConstraintSettings loadedSettings = deserializeSettings(constraint)) {
                if (loadedSettings == null) {
                    VxMainClass.LOGGER.error("Failed to deserialize settings for constraint {}", constraint.getConstraintId());
                    return;
                }

                boolean wasCreatedWithWorldSpace = getConstraintSpace(loadedSettings) == EConstraintSpace.WorldSpace;

                TwoBodyConstraint joltConstraint = world.getBodyInterface()
                        .createConstraint(loadedSettings, body1.getBodyId(), body2.getBodyId());

                if (joltConstraint == null) {
                    VxMainClass.LOGGER.error("Failed to create Jolt constraint for {}", constraint.getConstraintId());
                    return;
                }

                // If the constraint was defined in world space, Jolt converts it to local space.
                // We must save the new local space definition to ensure it reloads correctly.
                if (wasCreatedWithWorldSpace) {
                    try (ConstraintSettingsRef settingsRef = joltConstraint.getConstraintSettings();
                         ConstraintSettings canonicalSettingsRaw = settingsRef.getPtr()) {
                        if (canonicalSettingsRaw instanceof TwoBodyConstraintSettings canonicalSettings) {
                            constraint.updateSettingsData(canonicalSettings);
                        }
                    }
                }

                world.getPhysicsSystem().addConstraint(joltConstraint);
                constraint.setJoltConstraint(joltConstraint);
                activeConstraints.put(constraint.getConstraintId(), constraint);

            } catch (Exception e) {
                VxMainClass.LOGGER.error("Exception during constraint activation for {}", constraint.getConstraintId(), e);
            }
        });
    }

    /**
     * Deserializes constraint settings from a VxConstraint's byte data using Jolt's object stream.
     *
     * @param constraint The constraint containing the data and subtype.
     * @return The deserialized TwoBodyConstraintSettings object, or null on failure.
     */
    @Nullable
    private TwoBodyConstraintSettings deserializeSettings(VxConstraint constraint) {
        String settingsString = new String(constraint.getSettingsData(), StandardCharsets.ISO_8859_1);
        try (StringStream stringStream = new StringStream(settingsString);
             TwoBodyConstraintSettingsRef settingsRef = new TwoBodyConstraintSettingsRef()) {

            if (ObjectStreamIn.sReadObject(stringStream, settingsRef)) {
                return settingsRef.getPtr();
            } else {
                VxMainClass.LOGGER.error("Jolt ObjectStreamIn failed to read constraint settings for {}", constraint.getConstraintId());
                return null;
            }
        }
    }

    /**
     * Gets the EConstraintSpace from a TwoBodyConstraintSettings object.
     * Returns LocalSpace for types that don't have a space property.
     *
     * @param settings The settings object.
     * @return The EConstraintSpace.
     */
    private EConstraintSpace getConstraintSpace(TwoBodyConstraintSettings settings) {
        if (settings instanceof ConeConstraintSettings s) return s.getSpace();
        if (settings instanceof DistanceConstraintSettings s) return s.getSpace();
        if (settings instanceof FixedConstraintSettings s) return s.getSpace();
        if (settings instanceof GearConstraintSettings s) return s.getSpace();
        if (settings instanceof HingeConstraintSettings s) return s.getSpace();
        if (settings instanceof PointConstraintSettings s) return s.getSpace();
        if (settings instanceof PulleyConstraintSettings s) return s.getSpace();
        if (settings instanceof RackAndPinionConstraintSettings s) return s.getSpace();
        if (settings instanceof SixDofConstraintSettings s) return s.getSpace();
        if (settings instanceof SliderConstraintSettings s) return s.getSpace();
        if (settings instanceof SwingTwistConstraintSettings s) return s.getSpace();
        return EConstraintSpace.LocalToBodyCom; // Default for types without this property
    }

    public void removeConstraint(UUID constraintId, boolean discardData) {
        VxConstraint constraint = activeConstraints.remove(constraintId);
        if (constraint != null && constraint.getJoltConstraint() != null) {
            world.execute(() -> {
                world.getPhysicsSystem().removeConstraint(constraint.getJoltConstraint());
                constraint.getJoltConstraint().close();
            });
        }
        if (discardData) {
            constraintStorage.removeData(constraintId);
        }
    }

    public void removeConstraintsForObject(UUID bodyId, boolean discardData) {
        activeConstraints.values().stream()
                .filter(c -> c.getBody1Id().equals(bodyId) || c.getBody2Id().equals(bodyId))
                .forEach(c -> removeConstraint(c.getConstraintId(), discardData));
        dataSystem.removeForBody(bodyId);
    }

    public boolean hasActiveConstraint(UUID id) {
        return activeConstraints.containsKey(id);
    }

    public VxConstraintStorage getConstraintStorage() {
        return constraintStorage;
    }

    public VxObjectManager getObjectManager() {
        return objectManager;
    }

    public VxPhysicsWorld getWorld() {
        return world;
    }

    public VxDependencyDataSystem getDataSystem() {
        return dataSystem;
    }
}