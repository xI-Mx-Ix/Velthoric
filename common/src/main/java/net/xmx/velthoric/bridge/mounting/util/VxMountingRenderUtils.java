/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.bridge.mounting.util;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import net.minecraft.world.entity.Entity;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.body.client.VxClientBodyDataStore;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;
import net.xmx.velthoric.bridge.mounting.entity.VxMountingEntity;
import net.xmx.velthoric.physics.world.VxClientPhysicsWorld;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A utility singleton for accessing client-side physics body information,
 * particularly for rendering purposes. This centralizes the logic for retrieving
 * interpolated transforms of entities mounted on physics bodies.
 *
 * @author xI-Mx-Ix
 */
public enum VxMountingRenderUtils {
    INSTANCE;

    // Reusable objects to avoid allocations per-frame.
    private final RVec3 tempPosition = new RVec3();
    private final Quat tempRotation = new Quat();

    /**
     * Retrieves the interpolated transform of the physics body associated with a mounting proxy.
     *
     * @param proxy The mounting proxy entity.
     * @param partialTicks The fraction of a tick for interpolation.
     * @param transformOut The VxTransform object to store the result in.
     * @return An Optional containing the populated VxTransform if the associated body is
     *         found and initialized; otherwise, an empty Optional.
     */
    public Optional<VxTransform> getInterpolatedTransform(VxMountingEntity proxy, float partialTicks, VxTransform transformOut) {
        return proxy.getPhysicsId().flatMap(id ->
                getValidBodyIndex(id).map(index -> {
                    VxClientBodyManager manager = VxClientPhysicsWorld.getInstance().getBodyManager();
                    manager.getInterpolator().interpolateFrame(
                            manager.getStore(),
                            index,
                            partialTicks,
                            this.tempPosition,
                            this.tempRotation
                    );
                    transformOut.set(this.tempPosition, this.tempRotation);
                    return transformOut;
                })
        );
    }

    /**
     * Executes a given action if the entity's vehicle is a valid physics body,
     * providing the body's interpolated rotation to the action.
     *
     * @param entity The entity to check.
     * @param partialTicks The fraction of a tick for interpolation.
     * @param action A consumer that accepts the interpolated rotation (Quat).
     */
    public void ifMountedOnBody(Entity entity, float partialTicks, Consumer<Quat> action) {
        if (!(entity.getVehicle() instanceof VxMountingEntity proxy)) {
            return;
        }

        proxy.getPhysicsId().flatMap(this::getValidBodyIndex).ifPresent(index -> {
            VxClientBodyManager manager = VxClientPhysicsWorld.getInstance().getBodyManager();
            manager.getInterpolator().interpolateRotation(
                    manager.getStore(),
                    index,
                    partialTicks,
                    this.tempRotation
            );
            action.accept(this.tempRotation);
        });
    }

    /**
     * Retrieves the interpolated rotation of a physics body associated with a proxy entity.
     *
     * @param proxy The mounting proxy entity.
     * @param partialTicks The fraction of a tick for interpolation.
     * @return An Optional containing the interpolated rotation quaternion.
     */
    public Optional<Quat> getInterpolatedRotation(VxMountingEntity proxy, float partialTicks) {
        return proxy.getPhysicsId().flatMap(this::getValidBodyIndex).map(index -> {
            VxClientBodyManager manager = VxClientPhysicsWorld.getInstance().getBodyManager();
            manager.getInterpolator().interpolateRotation(manager.getStore(), index, partialTicks, this.tempRotation);
            return this.tempRotation;
        });
    }

    /**
     * Checks for a valid and initialized physics body by its ID and returns its data store index.
     *
     * @param bodyId The UUID of the physics body.
     * @return An Optional containing the index if the body is valid, otherwise an empty Optional.
     */
    private Optional<Integer> getValidBodyIndex(UUID bodyId) {
        VxClientBodyManager manager = VxClientPhysicsWorld.getInstance().getBodyManager();
        VxClientBodyDataStore store = manager.getStore();
        Integer index = store.getIndexForId(bodyId);

        if (index == null || !store.render_isInitialized[index]) {
            return Optional.empty();
        }
        return Optional.of(index);
    }
}