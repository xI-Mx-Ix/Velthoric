/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.physics.world;

import com.github.stephengold.joltjni.*;
import net.xmx.velthoric.core.physics.VxPhysicsBootstrap;
import net.xmx.velthoric.jni.BodyPairIgnoreHandler;
import net.xmx.velthoric.jni.TerrainContactHandler;
import net.xmx.velthoric.jni.VelthoricContactListener;
import org.jetbrains.annotations.Nullable;

/**
 * Encapsulates the native Jolt physics engine simulation and its core components.
 * Manages the memory allocators, multi-threading job system, native handlers,
 * and handles the lifecycle (initialization, updates, and cleanup) of the Jolt system.
 *
 * @author xI-Mx-Ix
 */
public class VxPhysicsSimulation {
    /**
     * The configuration for this physics simulation instance.
     */
    private final VxPhysicsConfig config;

    /**
     * The native Jolt Physics System instance.
     */
    private PhysicsSystem physicsSystem;

    /**
     * The multi-threaded job system for parallelizing physics calculations.
     */
    private JobSystemThreadPool jobSystem;

    /**
     * Allocator for temporary memory used during the physics update.
     */
    private TempAllocator tempAllocator;

    /**
     * Manages ignored body pairs for collision filtering across the entire world.
     */
    private BodyPairIgnoreHandler bodyPairIgnoreHandler;

    /**
     * Specialized handler for terrain-specific collision logic.
     */
    private TerrainContactHandler terrainContactHandler;

    /**
     * Native listener for contact events, handling terrain interactions and collision filtering.
     */
    private VelthoricContactListener contactListener;

    /**
     * Constructs a new physics simulation instance with the specified configuration.
     *
     * @param config The physics configuration to apply.
     */
    public VxPhysicsSimulation(VxPhysicsConfig config) {
        this.config = config;
    }

    /**
     * Initializes the native Jolt Physics System and all required filters/allocators.
     *
     * @param world The parent physics world managing this simulation.
     */
    public void initialize(VxPhysicsWorld world) {
        this.tempAllocator = new TempAllocatorImpl(this.config.tempAllocatorSize);
        int numThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        this.jobSystem = new JobSystemThreadPool(Jolt.cMaxPhysicsJobs, Jolt.cMaxPhysicsBarriers, numThreads);

        this.physicsSystem = new PhysicsSystem();
        BroadPhaseLayerInterface bpli = VxPhysicsBootstrap.getBroadPhaseLayerInterface();
        ObjectVsBroadPhaseLayerFilter ovbpf = VxPhysicsBootstrap.getObjectVsBroadPhaseLayerFilter();
        ObjectLayerPairFilter olpf = VxPhysicsBootstrap.getObjectLayerPairFilter();

        this.physicsSystem.init(this.config.maxBodies, 0, this.config.maxBodyPairs, this.config.maxContactConstraints, bpli, ovbpf, olpf);

        try (PhysicsSettings settings = this.physicsSystem.getPhysicsSettings()) {
            settings.setNumPositionSteps(this.config.numPositionIterations);
            settings.setNumVelocitySteps(this.config.numVelocityIterations);
            settings.setSpeculativeContactDistance(this.config.speculativeContactDistance);
            settings.setBaumgarte(this.config.baumgarteFactor);
            settings.setPenetrationSlop(this.config.penetrationSlop);
            settings.setTimeBeforeSleep(this.config.timeBeforeSleep);
            settings.setPointVelocitySleepThreshold(this.config.pointVelocitySleepThreshold);
            settings.setDeterministicSimulation(false);
            this.physicsSystem.setPhysicsSettings(settings);
        }

        this.physicsSystem.setGravity(0f, this.config.gravityY, 0f);

        // Initialize the contact handlers
        this.bodyPairIgnoreHandler = new BodyPairIgnoreHandler();
        this.terrainContactHandler = new TerrainContactHandler(this.physicsSystem.va(), world);

        // Attach the native contact listener dispatcher and inject handlers
        this.contactListener = new VelthoricContactListener(this.physicsSystem.va(), world, this.bodyPairIgnoreHandler, this.terrainContactHandler);

        this.physicsSystem.optimizeBroadPhase();
    }

    /**
     * Steps the simulation forward by the specified fixed delta time.
     *
     * @param fixedTimeStep  The time step to advance (in seconds).
     * @param collisionSteps Number of collision steps to perform.
     * @return The error code generated during the update (0/EPhysicsUpdateError.None means success).
     */
    public int update(float fixedTimeStep, int collisionSteps) {
        if (this.physicsSystem == null || this.tempAllocator == null || this.jobSystem == null) {
            return -1; // General failure if not initialized
        }
        return this.physicsSystem.update(fixedTimeStep, collisionSteps, this.tempAllocator, this.jobSystem);
    }

    /**
     * Releases all native Jolt resources gracefully.
     */
    public void cleanup() {
        if (this.contactListener != null) {
            this.contactListener.close();
            this.contactListener = null;
        }
        if (this.bodyPairIgnoreHandler != null) {
            this.bodyPairIgnoreHandler.close();
            this.bodyPairIgnoreHandler = null;
        }
        if (this.terrainContactHandler != null) {
            this.terrainContactHandler.close();
            this.terrainContactHandler = null;
        }
        if (this.physicsSystem != null) {
            this.physicsSystem.close();
            this.physicsSystem = null;
        }
        if (this.jobSystem != null) {
            this.jobSystem.close();
            this.jobSystem = null;
        }
        if (this.tempAllocator != null) {
            this.tempAllocator.close();
            this.tempAllocator = null;
        }
    }

    /**
     * @return The native Jolt Physics System, or null if not initialized.
     */
    @Nullable
    public PhysicsSystem getPhysicsSystem() {
        return this.physicsSystem;
    }

    /**
     * @return The handler for ignored body pairs in this simulation.
     */
    public BodyPairIgnoreHandler getBodyPairIgnoreHandler() {
        return this.bodyPairIgnoreHandler;
    }

    /**
     * @return The handler for terrain contact logic in this simulation.
     */
    public TerrainContactHandler getTerrainContactHandler() {
        return this.terrainContactHandler;
    }
}
