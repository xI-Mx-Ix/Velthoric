package net.xmx.vortex.physics.constraint.manager;

import net.xmx.vortex.physics.constraint.VxConstraint;
import net.xmx.vortex.physics.object.physicsobject.manager.VxObjectManager;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DependencyDataSystem {

    private final VxConstraintManager constraintManager;
    private final Map<UUID, VxConstraint> pendingConstraints = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> bodyToConstraintMap = new ConcurrentHashMap<>();

    public DependencyDataSystem(VxConstraintManager constraintManager) {
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

            VxObjectManager objectManager = constraintManager.getObjectManager();
            boolean body1Loaded = objectManager.getObjectContainer().hasObject(constraint.getBody1Id());
            boolean body2Loaded = objectManager.getObjectContainer().hasObject(constraint.getBody2Id());

            if (body1Loaded && body2Loaded) {
                if (pendingConstraints.remove(constraintId) != null) {
                    cleanup(constraintId);
                    constraintManager.activateConstraint(constraint);
                }
            }
        }
    }

    public void removeForBody(UUID bodyId) {
        Set<UUID> affectedConstraints = bodyToConstraintMap.get(bodyId);
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