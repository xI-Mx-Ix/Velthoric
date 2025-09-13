/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.constraint.manager;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.constraint.VxConstraint;
import net.xmx.velthoric.physics.constraint.persistence.VxConstraintStorage;
import net.xmx.velthoric.physics.constraint.serializer.ConstraintSerializerRegistry;
import net.xmx.velthoric.physics.constraint.serializer.IVxConstraintSerializer;
import net.xmx.velthoric.physics.object.VxAbstractBody;
import net.xmx.velthoric.physics.object.manager.VxObjectManager;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VxConstraintManager {

    private final VxPhysicsWorld world;
    private final VxObjectManager objectManager;
    private final VxConstraintStorage constraintStorage;
    private final DependencyDataSystem dataSystem;
    private final Map<UUID, VxConstraint> activeConstraints = new ConcurrentHashMap<>();

    public VxConstraintManager(VxObjectManager objectManager) {
        this.objectManager = objectManager;
        this.world = objectManager.getPhysicsWorld();
        this.constraintStorage = new VxConstraintStorage(world.getLevel(), this);
        this.dataSystem = new DependencyDataSystem(this);
        ConstraintSerializerRegistry.initialize();
    }

    public void initialize(VxPhysicsWorld world) {
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

    public void addConstraintFromStorage(VxConstraint constraint) {
        dataSystem.addPendingConstraint(constraint);
    }

    @SuppressWarnings("unchecked")
    protected void activateConstraint(VxConstraint constraint) {
        world.execute(() -> {
            VxAbstractBody body1 = objectManager.getObject(constraint.getBody1Id());
            VxAbstractBody body2 = objectManager.getObject(constraint.getBody2Id());

            if (body1 == null || body2 == null || body1.getBodyId() == 0 || body2.getBodyId() == 0) {
                dataSystem.addPendingConstraint(constraint);
                return;
            }

            Optional<IVxConstraintSerializer<?>> rawSerializerOpt = ConstraintSerializerRegistry.get(constraint.getSubType());
            if (rawSerializerOpt.isEmpty()) {
                VxMainClass.LOGGER.error("No serializer found for constraint type {} (ID: {})", constraint.getSubType(), constraint.getConstraintId());
                return;
            }
            IVxConstraintSerializer<TwoBodyConstraintSettings> serializer = (IVxConstraintSerializer<TwoBodyConstraintSettings>) rawSerializerOpt.get();

            ByteBuf buffer = Unpooled.wrappedBuffer(constraint.getSettingsData());
            TwoBodyConstraintSettings loadedSettings = serializer.load(buffer);

            try (loadedSettings) {
                buffer.release();
                boolean wasCreatedWithWorldSpace = isCreatedWithWorldSpace(loadedSettings);
                TwoBodyConstraint joltConstraint = world.getBodyInterface()
                        .createConstraint(loadedSettings, body1.getBodyId(), body2.getBodyId());

                if (joltConstraint == null) {
                    VxMainClass.LOGGER.error("Failed to create Jolt constraint for {}", constraint.getConstraintId());
                    return;
                }

                if (wasCreatedWithWorldSpace) {
                    try (ConstraintSettingsRef settingsRef = joltConstraint.getConstraintSettings()) {
                        try (ConstraintSettings canonicalSettingsRaw = settingsRef.getPtr()) {
                            if (canonicalSettingsRaw instanceof TwoBodyConstraintSettings canonicalSettings) {
                                constraint.updateSettingsData(canonicalSettings);
                            }
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

    private static boolean isCreatedWithWorldSpace(TwoBodyConstraintSettings loadedSettings) {
        boolean wasCreatedWithWorldSpace = false;

        if (loadedSettings instanceof ConeConstraintSettings s) {
            wasCreatedWithWorldSpace = (s.getSpace() == EConstraintSpace.WorldSpace);
        } else if (loadedSettings instanceof DistanceConstraintSettings s) {
            wasCreatedWithWorldSpace = (s.getSpace() == EConstraintSpace.WorldSpace);
        } else if (loadedSettings instanceof FixedConstraintSettings s) {
            wasCreatedWithWorldSpace = (s.getSpace() == EConstraintSpace.WorldSpace);
        } else if (loadedSettings instanceof GearConstraintSettings s) {
            wasCreatedWithWorldSpace = (s.getSpace() == EConstraintSpace.WorldSpace);
        } else if (loadedSettings instanceof HingeConstraintSettings s) {
            wasCreatedWithWorldSpace = (s.getSpace() == EConstraintSpace.WorldSpace);
        } else if (loadedSettings instanceof PointConstraintSettings s) {
            wasCreatedWithWorldSpace = (s.getSpace() == EConstraintSpace.WorldSpace);
        } else if (loadedSettings instanceof PulleyConstraintSettings s) {
            wasCreatedWithWorldSpace = (s.getSpace() == EConstraintSpace.WorldSpace);
        } else if (loadedSettings instanceof RackAndPinionConstraintSettings s) {
            wasCreatedWithWorldSpace = (s.getSpace() == EConstraintSpace.WorldSpace);
        } else if (loadedSettings instanceof SixDofConstraintSettings s) {
            wasCreatedWithWorldSpace = (s.getSpace() == EConstraintSpace.WorldSpace);
        } else if (loadedSettings instanceof SliderConstraintSettings s) {
            wasCreatedWithWorldSpace = (s.getSpace() == EConstraintSpace.WorldSpace);
        } else if (loadedSettings instanceof SwingTwistConstraintSettings s) {
            wasCreatedWithWorldSpace = (s.getSpace() == EConstraintSpace.WorldSpace);
        }
        return wasCreatedWithWorldSpace;
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

    public DependencyDataSystem getDataSystem() {
        return dataSystem;
    }
}
