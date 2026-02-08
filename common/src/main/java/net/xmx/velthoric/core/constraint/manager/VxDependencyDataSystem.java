/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.constraint.manager;

import net.xmx.velthoric.core.constraint.VxConstraint;
import net.xmx.velthoric.core.body.manager.VxBodyManager;
import net.xmx.velthoric.core.body.type.VxBody;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages pending constraints and their body dependencies.
 * A constraint will only be activated once all of its associated bodies are loaded into the physics world.
 *
 * @author xI-Mx-Ix
 */
public class VxDependencyDataSystem {

    private final VxConstraintManager constraintManager;
    private final Map<UUID, VxConstraint> pendingConstraints = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> bodyToConstraintMap = new ConcurrentHashMap<>();

    public VxDependencyDataSystem(VxConstraintManager constraintManager) {
        this.constraintManager = constraintManager;
    }

    /**
     * Adds a constraint to the pending queue and registers its body dependencies.
     * It immediately checks if the dependencies are already met.
     * @param constraint The constraint to add.
     */
    public void addPendingConstraint(VxConstraint constraint) {
        UUID constraintId = constraint.getConstraintId();
        pendingConstraints.put(constraintId, constraint);

        // Register dependencies only for non-world bodies.
        if (!constraint.getBody1Id().equals(VxConstraintManager.WORLD_BODY_ID)) {
            bodyToConstraintMap.computeIfAbsent(constraint.getBody1Id(), k -> ConcurrentHashMap.newKeySet()).add(constraintId);
        }
        if (!constraint.getBody2Id().equals(VxConstraintManager.WORLD_BODY_ID)) {
            bodyToConstraintMap.computeIfAbsent(constraint.getBody2Id(), k -> ConcurrentHashMap.newKeySet()).add(constraintId);
        }

        // Check if dependencies are already met.
        onDependencyLoaded(constraint.getBody1Id());
        if (!constraint.getBody1Id().equals(constraint.getBody2Id())) {
            onDependencyLoaded(constraint.getBody2Id());
        }
    }

    /**
     * Called when a body is loaded into the world. Checks all pending constraints
     * that depend on this body to see if they can now be activated.
     * @param bodyId The UUID of the body that was loaded.
     */
    public void onDependencyLoaded(UUID bodyId) {
        if (bodyId.equals(VxConstraintManager.WORLD_BODY_ID)) {
            return; // The world is not a dependency, so no constraints are waiting for it.
        }

        Set<UUID> affectedConstraints = bodyToConstraintMap.get(bodyId);
        if (affectedConstraints == null || affectedConstraints.isEmpty()) {
            return;
        }

        for (UUID constraintId : Set.copyOf(affectedConstraints)) {
            VxConstraint constraint = pendingConstraints.get(constraintId);
            if (constraint == null) {
                cleanup(constraintId);
                continue;
            }

            VxBodyManager bodyManager = constraintManager.getBodyManager();
            VxBody body1 = bodyManager.getVxBody(constraint.getBody1Id());
            VxBody body2 = bodyManager.getVxBody(constraint.getBody2Id());

            // A body is ready if it's loaded OR if it's the special world body ID.
            boolean body1Ready = (body1 != null && body1.getBodyId() != 0) || constraint.getBody1Id().equals(VxConstraintManager.WORLD_BODY_ID);
            boolean body2Ready = (body2 != null && body2.getBodyId() != 0) || constraint.getBody2Id().equals(VxConstraintManager.WORLD_BODY_ID);

            if (body1Ready && body2Ready) {
                if (pendingConstraints.remove(constraintId) != null) {
                    cleanup(constraintId);
                    constraintManager.activateConstraint(constraint);
                }
            }
        }
    }

    /**
     * Removes all pending constraints and dependency mappings associated with a specific body.
     * @param bodyId The UUID of the body being removed.
     */
    public void removeForBody(UUID bodyId) {
        Set<UUID> affectedConstraints = bodyToConstraintMap.remove(bodyId);
        if (affectedConstraints != null) {
            for (UUID constraintId : Set.copyOf(affectedConstraints)) {
                pendingConstraints.remove(constraintId);
                cleanup(constraintId);
            }
        }
    }

    public boolean isPending(UUID constraintId) {
        return pendingConstraints.containsKey(constraintId);
    }

    /**
     * Removes a constraint's ID from all dependency mappings.
     */
    private void cleanup(UUID constraintId) {
        bodyToConstraintMap.values().forEach(set -> set.remove(constraintId));
    }

    public void clear() {
        pendingConstraints.clear();
        bodyToConstraintMap.clear();
    }

    /**
     * Removes a specific constraint reference from the tracking system.
     * <p>
     * This method is called when a constraint is explicitly removed or destroyed.
     * It ensures the constraint is removed from the pending queue and that
     * neither of its attached bodies retains a dependency reference to it.
     *
     * @param constraint The constraint object to dereference.
     */
    public void removeConstraintReference(VxConstraint constraint) {
        if (constraint == null) return;

        UUID constraintId = constraint.getConstraintId();

        // 1. Remove from the pending queue if it exists there
        pendingConstraints.remove(constraintId);

        // 2. Efficiently remove from dependency maps using the known body IDs.
        // This is O(1) compared to the O(N) iteration of the generic cleanup() method.
        removeDependencyFromMap(constraint.getBody1Id(), constraintId);
        removeDependencyFromMap(constraint.getBody2Id(), constraintId);
    }

    /**
     * Helper to safely remove a constraint ID from a body's dependency set.
     */
    private void removeDependencyFromMap(UUID bodyId, UUID constraintId) {
        if (bodyId.equals(VxConstraintManager.WORLD_BODY_ID)) return;

        Set<UUID> dependencies = bodyToConstraintMap.get(bodyId);
        if (dependencies != null) {
            dependencies.remove(constraintId);

            // Optimization: Remove the entry entirely if no constraints depend on this body anymore
            if (dependencies.isEmpty()) {
                bodyToConstraintMap.remove(bodyId);
            }
        }
    }
}