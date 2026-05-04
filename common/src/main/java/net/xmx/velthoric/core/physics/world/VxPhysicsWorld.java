/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.physics.world;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EPhysicsUpdateError;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.xmx.velthoric.core.body.server.VxServerBodyManager;
import net.xmx.velthoric.core.constraint.manager.VxConstraintManager;
import net.xmx.velthoric.core.physics.VxPhysicsBootstrap;
import net.xmx.velthoric.core.ragdoll.VxRagdollManager;
import net.xmx.velthoric.core.terrain.VxTerrainSystem;
import net.xmx.velthoric.core.terrain.interaction.VxTerrainInteractionHandler;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.jni.BodyPairIgnoreManager;
import net.xmx.velthoric.jni.TerrainContactListener;
import net.xmx.velthoric.util.VxFrameTimer;
import net.xmx.velthoric.util.VxPauseUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

/**
 * Manages the entire physics simulation for a single Minecraft dimension.
 * Each instance runs its own dedicated thread to perform physics calculations,
 * keeping the simulation decoupled from the main server tick rate. It handles the
 * Jolt Physics System lifecycle, manages subsystems like bodies and terrain, and
 * provides a thread-safe command queue for interacting with the simulation.
 *
 * @author xI-Mx-Ix
 */
public final class VxPhysicsWorld implements Runnable, Executor {
    /**
     * The target frequency of the physics simulation in Hertz.
     */
    private static final int SIMULATION_HZ = 60;

    /**
     * The fixed time duration (in seconds) for each physics simulation step.
     */
    private static final float FIXED_TIME_STEP = 1.0f / SIMULATION_HZ;

    /**
     * The maximum amount of time that can be accumulated for simulation steps in a single frame.
     * Prevents the "spiral of death" where physics lag causes more lag.
     */
    private static final float MAX_ACCUMULATED_TIME = 5.0f * FIXED_TIME_STEP;

    /**
     * The maximum number of external commands processed from the queue in a single physics tick.
     */
    private static final int MAX_COMMANDS_PER_TICK = 4096;

    /**
     * A map of all active physics worlds, keyed by their Minecraft dimension resource key.
     */
    private static final Map<ResourceKey<Level>, VxPhysicsWorld> worlds = new ConcurrentHashMap<>();

    // Jolt Physics Configuration Constants

    /**
     * The maximum number of physics bodies supported in a single world.
     */
    private static final int maxBodies = 65536;

    /**
     * The maximum number of simultaneous overlapping body pairs supported.
     */
    private static final int maxBodyPairs = 65536;

    /**
     * The maximum number of contact constraints (points of collision) supported.
     */
    private static final int maxContactConstraints = 65536;

    /**
     * The number of position correction iterations per step. Higher values increase stability.
     */
    private static final int numPositionIterations = 10;

    /**
     * The number of velocity solver iterations per step. Higher values reduce jitter in stacks.
     */
    private static final int numVelocityIterations = 15;

    /**
     * The distance at which the solver starts considering contacts for continuous collision detection.
     */
    private static final float speculativeContactDistance = 0.02f;

    /**
     * The Baumgarte stabilization factor for resolving position errors.
     */
    private static final float baumgarteFactor = 0.2f;

    /**
     * The allowed amount of penetration between bodies before the solver applies correction.
     */
    private static final float penetrationSlop = 0.001f;

    /**
     * The duration a body must remain nearly stationary before it is put to sleep.
     */
    private static final float timeBeforeSleep = 1.0f;

    /**
     * The linear velocity threshold below which a body is considered stationary for sleeping.
     */
    private static final float pointVelocitySleepThreshold = 0.005f;

    /**
     * The default gravity acceleration applied to all dynamic bodies in the world.
     */
    private static final float gravityY = -9.81f;

    /**
     * The size (in bytes) of the pre-allocated temporary memory pool for the physics solver.
     */
    private static final int tempAllocatorSize = 64 * 1024 * 1024; // 64MB

    /**
     * The Minecraft server level associated with this physics world.
     */
    private final ServerLevel level;

    /**
     * The resource key identifying the dimension of this world.
     */
    private final ResourceKey<Level> dimensionKey;

    /**
     * Manages all physics bodies within this world.
     */
    private final VxServerBodyManager bodyManager;

    /**
     * Manages all physical constraints (joints) within this world.
     */
    private final VxConstraintManager constraintManager;

    /**
     * Manages the physical representation and generation of voxel terrain.
     */
    private final VxTerrainSystem terrainSystem;

    /**
     * Manages ragdolls and skeletal physics for entities in this world.
     */
    private final VxRagdollManager ragdollManager;

    /**
     * Tracks the performance and timing of each physics frame.
     */
    private final VxFrameTimer physicsFrameTimer = new VxFrameTimer();

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
    private BodyPairIgnoreManager bodyPairIgnoreManager;

    /**
     * Native listener for contact events, handling terrain interactions and collision filtering.
     */
    private TerrainContactListener terrainContactListener;

    /**
     * A thread-safe queue of commands to be executed on the physics thread.
     */
    private final Queue<Runnable> commandQueue = new ConcurrentLinkedQueue<>();

    /**
     * The dedicated thread executing the physics simulation loop.
     */
    private volatile Thread physicsThreadExecutor;

    /**
     * Atomic flag indicating whether the simulation loop is currently running.
     */
    private volatile boolean isRunning = false;

    /**
     * Accumulates real-time delta between frames to trigger fixed physics steps.
     */
    private float timeAccumulator = 0.0f;

    /**
     * The system time (in nanoseconds) of the last physics loop iteration.
     */
    private long lastTimeNanos = 0L;

    /**
     * Constructs a new physics world for the given level.
     * Subsystems are instantiated but not yet initialized.
     *
     * @param level The server level.
     */
    private VxPhysicsWorld(ServerLevel level) {
        this.level = level;
        this.dimensionKey = level.dimension();
        this.bodyManager = new VxServerBodyManager(this);
        this.constraintManager = new VxConstraintManager(this.bodyManager);
        this.terrainSystem = new VxTerrainSystem(this, this.level);
        this.ragdollManager = new VxRagdollManager(this);
    }

    /**
     * Retrieves the physics world for a dimension, creating and starting it if it doesn't exist.
     *
     * @param level The server level.
     * @return The physics world instance.
     */
    public static VxPhysicsWorld getOrCreate(ServerLevel level) {
        return worlds.computeIfAbsent(level.dimension(), key -> {
            VxPhysicsWorld newWorld = new VxPhysicsWorld(level);
            newWorld.initializeAndStart();
            return newWorld;
        });
    }

    /**
     * Retrieves an existing physics world for a dimension.
     *
     * @param dimensionKey The dimension key.
     * @return The physics world, or null if not initialized.
     */
    @Nullable
    public static VxPhysicsWorld get(ResourceKey<Level> dimensionKey) {
        return worlds.get(dimensionKey);
    }

    /**
     * Stops and removes the physics world for the specified dimension.
     *
     * @param dimensionKey The dimension key to shut down.
     */
    public static void shutdown(ResourceKey<Level> dimensionKey) {
        VxPhysicsWorld world = worlds.remove(dimensionKey);
        if (world != null) {
            world.stop();
        }
    }

    /**
     * Shuts down all active physics worlds.
     */
    public static void shutdownAll() {
        new ArrayList<>(worlds.keySet()).forEach(VxPhysicsWorld::shutdown);
        worlds.clear();
    }

    /**
     * Prepares subsystems and starts the dedicated physics thread.
     */
    private void initializeAndStart() {
        if (this.physicsThreadExecutor != null && this.physicsThreadExecutor.isAlive()) {
            return;
        }

        this.bodyManager.initialize();
        this.constraintManager.initialize();
        this.terrainSystem.initialize();

        this.isRunning = true;
        String threadName = "Velthoric Physics Thread - " + dimensionKey.location().getPath().replace('/', '_');
        this.physicsThreadExecutor = new Thread(this, threadName);
        this.physicsThreadExecutor.setDaemon(true);
        this.physicsThreadExecutor.start();
    }

    /**
     * Gracefully stops the physics simulation and waits for the thread to join.
     */
    public void stop() {
        if (!this.isRunning) {
            return;
        }
        this.isRunning = false;

        if (this.physicsThreadExecutor != null) {
            VxMainClass.LOGGER.debug("Stopping physics world for {}...", dimensionKey.location());
            try {
                // Wait for the physics thread to finish its last loop and shut down internal systems
                this.physicsThreadExecutor.join(5000); // 5 second timeout safety
                if (this.physicsThreadExecutor.isAlive()) {
                    VxMainClass.LOGGER.warn("Physics thread for {} did not stop in time. Forcing continuation.", dimensionKey.location());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            VxMainClass.LOGGER.debug("Physics world for {} stop sequence completed.", dimensionKey.location());
        }
    }

    /**
     * The main execution loop for the physics thread.
     * Manages initialization, command processing, and time-stepping.
     */
    @Override
    public void run() {
        try {
            initializePhysicsSystem();

            this.lastTimeNanos = System.nanoTime();

            while (this.isRunning) {
                processCommandQueue();

                long currentTimeNanos = System.nanoTime();
                float deltaTime = (currentTimeNanos - this.lastTimeNanos) / 1_000_000_000.0f;
                this.lastTimeNanos = currentTimeNanos;

                this.updatePhysicsLoop(deltaTime);

                Thread.sleep(1);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            VxMainClass.LOGGER.fatal("Fatal error in physics loop for dimension {}", dimensionKey.location(), t);
            this.isRunning = false;
        } finally {
            shutdownInternalSystems();
            cleanupJolt();
        }
    }

    /**
     * Internal simulation step. Handles time accumulation and fixed-step updates.
     *
     * @param deltaTime The real-time delta since the last iteration (in seconds).
     */
    private void updatePhysicsLoop(float deltaTime) {
        if (VxPauseUtil.isPaused() || !this.isRunning || this.physicsSystem == null) {
            return;
        }

        this.timeAccumulator += deltaTime;
        if (this.timeAccumulator > MAX_ACCUMULATED_TIME) {
            this.timeAccumulator = MAX_ACCUMULATED_TIME;
        }

        if (this.timeAccumulator >= FIXED_TIME_STEP) {
            long startTime = System.nanoTime();

            this.onPrePhysicsTick();

            int error = this.physicsSystem.update(FIXED_TIME_STEP, 1, this.tempAllocator, this.jobSystem);
            if (error != EPhysicsUpdateError.None) {
                VxMainClass.LOGGER.error("Jolt physics update failed with error code: {}. Shutting down world.", error);
                this.isRunning = false;
                return;
            }

            this.onPhysicsTick();

            this.physicsFrameTimer.logFrameDuration(System.nanoTime() - startTime);
            this.timeAccumulator -= FIXED_TIME_STEP;
        }
    }

    /**
     * Hook called before the native physics step.
     */
    public void onPrePhysicsTick() {
        this.bodyManager.onPrePhysicsTick(this);
    }

    /**
     * Hook called after the native physics step.
     */
    public void onPhysicsTick() {
        this.bodyManager.onPhysicsTick(this);
    }

    /**
     * Called during the main game tick to handle cross-thread synchronization.
     *
     * @param level The server level.
     */
    public void onGameTick(ServerLevel level) {
        this.bodyManager.onGameTick(level);
        VxTerrainInteractionHandler.tick(this);
    }

    /**
     * Processes pending commands from the queue.
     * Limited by MAX_COMMANDS_PER_TICK to ensure loop stability.
     */
    private void processCommandQueue() {
        for (int i = 0; i < MAX_COMMANDS_PER_TICK; i++) {
            Runnable command = this.commandQueue.poll();
            if (command == null) {
                break;
            }
            try {
                command.run();
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Exception while executing physics command", e);
            }
        }
    }

    /**
     * Initializes the native Jolt Physics System and all required filters/allocators.
     */
    public void initializePhysicsSystem() {
        this.tempAllocator = new TempAllocatorImpl(tempAllocatorSize);
        int numThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        this.jobSystem = new JobSystemThreadPool(Jolt.cMaxPhysicsJobs, Jolt.cMaxPhysicsBarriers, numThreads);

        this.physicsSystem = new PhysicsSystem();
        BroadPhaseLayerInterface bpli = VxPhysicsBootstrap.getBroadPhaseLayerInterface();
        ObjectVsBroadPhaseLayerFilter ovbpf = VxPhysicsBootstrap.getObjectVsBroadPhaseLayerFilter();
        ObjectLayerPairFilter olpf = VxPhysicsBootstrap.getObjectLayerPairFilter();

        this.physicsSystem.init(maxBodies, 0, maxBodyPairs, maxContactConstraints, bpli, ovbpf, olpf);

        try (PhysicsSettings settings = this.physicsSystem.getPhysicsSettings()) {
            settings.setNumPositionSteps(numPositionIterations);
            settings.setNumVelocitySteps(numVelocityIterations);
            settings.setSpeculativeContactDistance(speculativeContactDistance);
            settings.setBaumgarte(baumgarteFactor);
            settings.setPenetrationSlop(penetrationSlop);
            settings.setTimeBeforeSleep(timeBeforeSleep);
            settings.setPointVelocitySleepThreshold(pointVelocitySleepThreshold);
            settings.setDeterministicSimulation(false);
            this.physicsSystem.setPhysicsSettings(settings);
        }

        this.physicsSystem.setGravity(0f, gravityY, 0f);

        // Initialize the body pair ignore manager
        this.bodyPairIgnoreManager = new BodyPairIgnoreManager();

        // Attach the native contact listener
        this.terrainContactListener = new TerrainContactListener(this.physicsSystem.va(), this, this.bodyPairIgnoreManager);

        this.physicsSystem.optimizeBroadPhase();
    }

    /**
     * Shuts down Java-level subsystems.
     */
    private void shutdownInternalSystems() {
        if (this.terrainSystem != null) {
            this.terrainSystem.shutdown();
        }
        if (this.constraintManager != null) {
            this.constraintManager.shutdown();
        }
        if (this.bodyManager != null) {
            this.bodyManager.shutdown();
        }
    }

    /**
     * Releases all native Jolt resources and clears the command queue.
     */
    private void cleanupJolt() {
        if (this.terrainContactListener != null) {
            this.terrainContactListener.close();
            this.terrainContactListener = null;
        }
        if (this.bodyPairIgnoreManager != null) {
            this.bodyPairIgnoreManager.close();
            this.bodyPairIgnoreManager = null;
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

        this.commandQueue.clear();
    }

    /**
     * Schedules a task to be executed on the physics thread.
     *
     * @param command The task to run.
     */
    public void queueCommand(Runnable command) {
        if (command != null && this.isRunning) {
            this.commandQueue.offer(command);
        }
    }

    /**
     * Implementation of {@link Executor#execute(Runnable)}.
     * Schedules the task on the physics thread.
     *
     * @param task The task to execute.
     */
    @Override
    public void execute(@NotNull Runnable task) {
        this.queueCommand(task);
    }

    /**
     * @return The body manager for this world.
     */
    public VxServerBodyManager getBodyManager() {
        return this.bodyManager;
    }

    /**
     * @return The constraint manager for this world.
     */
    public VxConstraintManager getConstraintManager() {
        return this.constraintManager;
    }

    /**
     * @return The terrain system for this world.
     */
    public VxTerrainSystem getTerrainSystem() {
        return this.terrainSystem;
    }

    /**
     * @return The ragdoll manager for this world.
     */
    public VxRagdollManager getRagdollManager() {
        return this.ragdollManager;
    }

    /**
     * @return The server level associated with this world.
     */
    public ServerLevel getLevel() {
        return this.level;
    }

    /**
     * @return The dimension resource key of this world.
     */
    public ResourceKey<Level> getDimensionKey() {
        return this.dimensionKey;
    }

    /**
     * @return The constant physics time step (seconds).
     */
    public static float getFixedTimeStep() {
        return FIXED_TIME_STEP;
    }

    /**
     * @return True if the physics simulation thread is active and running.
     */
    public boolean isRunning() {
        return this.isRunning && this.physicsThreadExecutor != null && this.physicsThreadExecutor.isAlive();
    }

    /**
     * @return The native Jolt Physics System, or null if not initialized.
     */
    @Nullable
    public PhysicsSystem getPhysicsSystem() {
        return this.physicsSystem;
    }

    /**
     * @return The frame timer used to measure physics simulation performance.
     */
    public VxFrameTimer getPhysicsFrameTimer() {
        return this.physicsFrameTimer;
    }

    /**
     * @return The manager for ignored body pairs in this world.
     */
    public BodyPairIgnoreManager getBodyPairIgnoreManager() {
        return this.bodyPairIgnoreManager;
    }

    /**
     * @return The native virtual address of the Jolt Physics System.
     */
    public long getPhysicsSystemPtr() {
        return this.physicsSystem != null ? this.physicsSystem.va() : 0L;
    }

    /**
     * Static helper to get the body manager for a dimension.
     *
     * @param dimensionKey The dimension.
     * @return The manager, or null if the world is not initialized.
     */
    @Nullable
    public static VxServerBodyManager getBodyManager(ResourceKey<Level> dimensionKey) {
        VxPhysicsWorld world = get(dimensionKey);
        return world != null ? world.getBodyManager() : null;
    }

    /**
     * Static helper to get the constraint manager for a dimension.
     *
     * @param dimensionKey The dimension.
     * @return The manager, or null if the world is not initialized.
     */
    @Nullable
    public static VxConstraintManager getConstraintManager(ResourceKey<Level> dimensionKey) {
        VxPhysicsWorld world = get(dimensionKey);
        return world != null ? world.getConstraintManager() : null;
    }

    /**
     * Static helper to get the terrain system for a dimension.
     *
     * @param dimensionKey The dimension.
     * @return The system, or null if the world is not initialized.
     */
    @Nullable
    public static VxTerrainSystem getTerrainSystem(ResourceKey<Level> dimensionKey) {
        VxPhysicsWorld world = get(dimensionKey);
        return world != null ? world.getTerrainSystem() : null;
    }

    /**
     * Static helper to get the ragdoll manager for a dimension.
     *
     * @param dimensionKey The dimension.
     * @return The manager, or null if the world is not initialized.
     */
    @Nullable
    public static VxRagdollManager getRagdollManager(ResourceKey<Level> dimensionKey) {
        VxPhysicsWorld world = get(dimensionKey);
        return world != null ? world.getRagdollManager() : null;
    }

    /**
     * @return An unmodifiable collection of all active physics worlds.
     */
    public static Collection<VxPhysicsWorld> getAll() {
        return Collections.unmodifiableCollection(worlds.values());
    }
}
