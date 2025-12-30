/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.manager;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.readonly.ConstBody;
import com.github.stephengold.joltjni.readonly.ConstSoftBodyMotionProperties;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.body.VxRemovalReason;
import net.xmx.velthoric.physics.body.type.VxBody;
import net.xmx.velthoric.physics.body.type.VxRigidBody;
import net.xmx.velthoric.physics.body.type.VxSoftBody;
import net.xmx.velthoric.physics.body.type.factory.VxRigidBodyFactory;
import net.xmx.velthoric.physics.body.type.factory.VxSoftBodyFactory;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.nio.FloatBuffer;

/**
 * A singleton bridge that handles all direct interactions with the Jolt physics library.
 * This class isolates Jolt-specific API calls, such as body creation, destruction, and
 * direct access, from the main physics management logic in {@link VxBodyManager}.
 *
 * @author xI-Mx-Ix
 */
public enum VxJoltBridge {
    INSTANCE;

    /**
     * Retrieves a writable {@link Body} for a given {@link VxBody} wrapper.
     * Acquires a write lock on the Jolt body.
     *
     * @param world The physics world containing the body.
     * @param body  The VxBody wrapper.
     * @return The writable {@link Body} if successful, otherwise null.
     */
    @Nullable
    public Body getJoltBody(VxPhysicsWorld world, VxBody body) {
        if (body == null) return null;
        return getJoltBody(world, body.getBodyId());
    }

    /**
     * Retrieves a read-only {@link ConstBody} for a given {@link VxBody} wrapper.
     * Acquires a read lock on the Jolt body.
     *
     * @param world The physics world containing the body.
     * @param body  The VxBody wrapper.
     * @return The read-only {@link ConstBody} if successful, otherwise null.
     */
    @Nullable
    public ConstBody getConstJoltBody(VxPhysicsWorld world, VxBody body) {
        if (body == null) return null;
        return getConstJoltBody(world, body.getBodyId());
    }

    /**
     * Retrieves a writable {@link Body} for a given Jolt body ID.
     * Acquires a write lock on the Jolt body.
     *
     * @param world  The physics world containing the body.
     * @param bodyId The Jolt body ID.
     * @return The writable {@link Body} if successful, otherwise null.
     */
    @Nullable
    public Body getJoltBody(VxPhysicsWorld world, int bodyId) {
        if (bodyId == 0) return null;

        try (BodyLockWrite lock = new BodyLockWrite(world.getPhysicsSystem().getBodyLockInterface(), bodyId)) {
            if (lock.succeededAndIsInBroadPhase()) {
                return lock.getBody();
            }
        }
        return null;
    }

    /**
     * Retrieves a read-only {@link ConstBody} for a given Jolt body ID.
     * Acquires a read lock on the Jolt body.
     *
     * @param world  The physics world containing the body.
     * @param bodyId The Jolt body ID.
     * @return The read-only {@link ConstBody} if successful, otherwise null.
     */
    @Nullable
    public ConstBody getConstJoltBody(VxPhysicsWorld world, int bodyId) {
        if (bodyId == 0) return null;

        try (BodyLockRead lock = new BodyLockRead(world.getPhysicsSystem().getBodyLockInterface(), bodyId)) {
            if (lock.succeededAndIsInBroadPhase()) {
                return lock.getBody();
            }
        }
        return null;
    }

    /**
     * Creates and adds a rigid body to the Jolt physics simulation.
     *
     * @param body            The rigid body wrapper.
     * @param manager         The body manager, used for cleanup on failure.
     * @param linearVelocity  The initial linear velocity (can be null).
     * @param angularVelocity The initial angular velocity (can be null).
     * @param activation      The initial activation state.
     */
    public void createAndAddJoltRigidBody(VxRigidBody body, VxBodyManager manager, @Nullable Vec3 linearVelocity, @Nullable Vec3 angularVelocity, EActivation activation, EMotionType motionType) {
        try {
            VxPhysicsWorld world = manager.getPhysicsWorld();
            VxServerBodyDataStore dataStore = manager.getDataStore();

            VxRigidBodyFactory factory = (shapeSettings, bcs) -> {
                try (ShapeResult shapeResult = shapeSettings.create()) {
                    if (shapeResult.hasError()) {
                        throw new IllegalStateException("Shape creation failed: " + shapeResult.getError());
                    }
                    try (ShapeRefC shapeRef = shapeResult.get()) {
                        int index = body.getDataStoreIndex();
                        bcs.setShape(shapeRef);
                        bcs.setPosition(dataStore.posX[index], dataStore.posY[index], dataStore.posZ[index]);
                        bcs.setRotation(new Quat(dataStore.rotX[index], dataStore.rotY[index], dataStore.rotZ[index], dataStore.rotW[index]));
                        if (linearVelocity != null) bcs.setLinearVelocity(linearVelocity);
                        if (angularVelocity != null) bcs.setAngularVelocity(angularVelocity);
                        bcs.setMotionType(EMotionType.Dynamic);

                        return world.getPhysicsSystem().getBodyInterface().createAndAddBody(bcs, activation);
                    }
                }
            };

            int bodyId = body.createJoltBody(factory);
            getJoltBody(world, bodyId).setMotionType(motionType);

            if (bodyId == Jolt.cInvalidBodyId) {
                VxMainClass.LOGGER.error("Jolt failed to create/add rigid body for {}", body.getPhysicsId());
                manager.removeBody(body.getPhysicsId(), VxRemovalReason.DISCARD); // Clean up failed addition.
                return;
            }
            body.setBodyId(bodyId);
            manager.registerJoltBodyId(bodyId, body);
            body.onBodyAdded(world);
            world.getConstraintManager().getDataSystem().onDependencyLoaded(body.getPhysicsId());

        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to create/add rigid body {}", body.getPhysicsId(), e);
            manager.removeBody(body.getPhysicsId(), VxRemovalReason.DISCARD); // Clean up on exception.
        }
    }

    /**
     * Creates and adds a soft body to the Jolt physics simulation.
     *
     * @param body            The soft body wrapper.
     * @param manager         The body manager, used for cleanup on failure.
     * @param linearVelocity  The initial linear velocity (can be null).
     * @param angularVelocity The initial angular velocity (can be null).
     * @param activation      The initial activation state.
     */
    public void createAndAddJoltSoftBody(VxSoftBody body, VxBodyManager manager, @Nullable Vec3 linearVelocity, @Nullable Vec3 angularVelocity, EActivation activation) {
        try {
            VxPhysicsWorld world = manager.getPhysicsWorld();
            VxServerBodyDataStore dataStore = manager.getDataStore();

            VxSoftBodyFactory factory = (sharedSettings, creationSettings) -> {
                try (sharedSettings; creationSettings) {
                    int index = body.getDataStoreIndex();
                    creationSettings.setPosition(dataStore.posX[index], dataStore.posY[index], dataStore.posZ[index]);
                    creationSettings.setRotation(new Quat(dataStore.rotX[index], dataStore.rotY[index], dataStore.rotZ[index], dataStore.rotW[index]));

                    return world.getPhysicsSystem().getBodyInterface().createAndAddSoftBody(creationSettings, activation);
                }
            };

            int bodyId = body.createJoltBody(factory);

            if (bodyId == Jolt.cInvalidBodyId) {
                VxMainClass.LOGGER.error("Jolt failed to create/add soft body for {}", body.getPhysicsId());
                manager.removeBody(body.getPhysicsId(), VxRemovalReason.DISCARD); // Clean up failed addition.
                return;
            }

            // Apply initial velocities to the soft body if provided.
            BodyInterface bodyInterface = world.getPhysicsSystem().getBodyInterface();
            if (linearVelocity != null) {
                bodyInterface.setLinearVelocity(bodyId, linearVelocity);
            }
            if (angularVelocity != null) {
                bodyInterface.setAngularVelocity(bodyId, angularVelocity);
            }

            body.setBodyId(bodyId);
            manager.registerJoltBodyId(bodyId, body);
            body.onBodyAdded(world);

            // If we have restored vertex data (e.g. from a save file), apply it now to the live body.
            // This restores the deformation state.
            int index = body.getDataStoreIndex();
            if (index != -1 && dataStore.vertexData[index] != null) {
                setSoftBodyVertices(world, body, dataStore.vertexData[index]);
            }

            world.getConstraintManager().getDataSystem().onDependencyLoaded(body.getPhysicsId());
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to create/add soft body {}", body.getPhysicsId(), e);
            manager.removeBody(body.getPhysicsId(), VxRemovalReason.DISCARD); // Clean up on exception.
        }
    }

    /**
     * Schedules the destruction of a physical body from the Jolt simulation.
     * This is executed on the physics thread.
     *
     * @param world  The physics world.
     * @param bodyId The Jolt body ID to destroy.
     */
    public void destroyJoltBody(VxPhysicsWorld world, int bodyId) {
        if (bodyId != 0 && bodyId != Jolt.cInvalidBodyId) {
            world.execute(() -> {
                BodyInterface bodyInterface = world.getPhysicsSystem().getBodyInterface();
                if (bodyInterface.isAdded(bodyId)) {
                    bodyInterface.removeBody(bodyId);
                }
                bodyInterface.destroyBody(bodyId);
            });
        }
    }

    /**
     * Retrieves the current vertex positions of a soft body from the Jolt simulation.
     *
     * @param world The physics world.
     * @param body  The soft body wrapper.
     * @return An array of vertex positions in world space, or null if the body is invalid.
     */
    public float @Nullable [] retrieveSoftBodyVertices(VxPhysicsWorld world, VxSoftBody body) {
        if (body.getBodyId() == 0) return null;

        try (ConstBody joltBody = getConstJoltBody(world, body)) {
            if (joltBody != null && joltBody.isSoftBody()) {
                ConstSoftBodyMotionProperties motionProps = (ConstSoftBodyMotionProperties) joltBody.getMotionProperties();
                int numVertices = motionProps.getSettings().countVertices();
                if (numVertices > 0) {
                    int bufferSize = numVertices * 3;
                    FloatBuffer vertexBuffer = Jolt.newDirectFloatBuffer(bufferSize);

                    // Retrieve vertex locations. We use the body's current center of mass position
                    // to transform vertices into world space (assuming standard soft body behavior).
                    RVec3 bodyPos = joltBody.getCenterOfMassPosition();
                    motionProps.putVertexLocations(bodyPos, vertexBuffer);

                    vertexBuffer.flip();
                    float[] vertexArray = new float[bufferSize];
                    vertexBuffer.get(vertexArray);
                    return vertexArray;
                }
            }
        }
        return null;
    }

    /**
     * Sets the vertex positions of a live soft body in the Jolt simulation.
     * This is used to restore the deformation state of a soft body.
     * The input vertices are expected to be in **World Space**. They will be converted
     * to the body's local space (relative to Center of Mass) before being applied.
     *
     * @param world    The physics world.
     * @param body     The soft body wrapper.
     * @param vertices The vertex positions to apply (x, y, z floats) in World Space.
     */
    public void setSoftBodyVertices(VxPhysicsWorld world, VxSoftBody body, float[] vertices) {
        if (body.getBodyId() == 0 || vertices == null) return;

        try (Body joltBody = getJoltBody(world, body)) {
            if (joltBody != null && joltBody.isSoftBody()) {
                SoftBodyMotionProperties motionProps = (SoftBodyMotionProperties) joltBody.getMotionProperties();
                int numVertices = motionProps.getSettings().countVertices();

                if (vertices.length < numVertices * 3) {
                    VxMainClass.LOGGER.warn("Vertex data mismatch for soft body {}: expected {} floats, got {}",
                            body.getPhysicsId(), numVertices * 3, vertices.length);
                    return;
                }

                // Get current body position (Center of Mass) to convert world-space vertices back to local-space
                RVec3 bodyPos = joltBody.getCenterOfMassPosition();
                double bX = bodyPos.xx();
                double bY = bodyPos.yy();
                double bZ = bodyPos.zz();

                Vec3 tempLocalPos = new Vec3();
                for (int i = 0; i < numVertices; i++) {
                    // Convert: Local = World - BodyPos
                    float localX = (float) (vertices[i * 3] - bX);
                    float localY = (float) (vertices[i * 3 + 1] - bY);
                    float localZ = (float) (vertices[i * 3 + 2] - bZ);

                    tempLocalPos.set(localX, localY, localZ);

                    // SoftBodyVertex is a wrapper around the native vertex pointer.
                    // We must update its position.
                    // Note: getVertex returns a new Java object wrapping the pointer.
                    SoftBodyVertex vertex = motionProps.getVertex(i);
                    vertex.setPosition(tempLocalPos);
                }
            }
        }
    }
}