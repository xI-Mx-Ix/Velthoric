/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.constraint.manager;

import net.xmx.velthoric.physics.constraint.VxConstraint;
import net.xmx.velthoric.physics.body.manager.VxBodyManager;
import net.xmx.velthoric.physics.body.type.VxBody;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author xI-Mx-Ix
 */
public class VxDependencyDataSystem {

    private final VxConstraintManager constraintManager;
    private final Map<UUID, VxConstraint> pendingConstraints = new ConcurrentHashMap<>();

    private final Map<UUID, Set<UUID>> bodyToConstraintMap = new ConcurrentHashMap<>();

    public VxDependencyDataSystem(VxConstraintManager constraintManager) {
        this.constraintManager = constraintManager;
    }

    public void addPendingConstraint(VxConstraint constraint) {
        UUID constraintId = constraint.getConstraintId();
        pendingConstraints.put(constraintId, constraint);

        bodyToConstraintMap.computeIfAbsent(constraint.getBody1Id(), k -> ConcurrentHashMap.newKeySet()).add(constraintId);
        bodyToConstraintMap.computeIfAbsent(constraint.getBody2Id(), k -> ConcurrentHashMap.newKeySet()).add(constraintId);

        onDependencyLoaded(constraint.getBody1Id());
        onDependencyLoaded(constraint.getBody2Id());
    }

    public void onDependencyLoaded(UUID bodyId) {
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

            boolean body1Ready = body1 != null && body1.getBodyId() != 0;
            boolean body2Ready = body2 != null && body2.getBodyId() != 0;

            if (body1Ready && body2Ready) {
                if (pendingConstraints.remove(constraintId) != null) {
                    cleanup(constraintId);
                    constraintManager.activateConstraint(constraint);
                }
            }
        }
    }

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

    private void cleanup(UUID constraintId) {
        bodyToConstraintMap.values().forEach(set -> set.remove(constraintId));
    }

    public void clear() {
        pendingConstraints.clear();
        bodyToConstraintMap.clear();
    }
}