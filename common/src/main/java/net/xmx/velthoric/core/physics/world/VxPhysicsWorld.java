/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.physics.world;

import com.github.stephengold.joltjni.PhysicsSystem;
import com.github.stephengold.joltjni.enumerate.EPhysicsUpdateError;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.xmx.velthoric.core.body.server.VxServerBodyManager;
import net.xmx.velthoric.core.constraint.manager.VxConstraintManager;
import net.xmx.velthoric.core.ragdoll.VxRagdollManager;
import net.xmx.velthoric.core.terrain.VxTerrainSystem;
import net.xmx.velthoric.core.terrain.interaction.VxTerrainInteractionHandler;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.jni.BodyPairIgnoreHandler;
import net.xmx.velthoric.jni.TerrainContactHandler;
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
     * Configuration parameters for the physics simulation and subsystems.
     * Ensures all physical components are uniformly configured from a single central state.
     */
    public record Config(
        /**
         * Maximum number of physics bodies the simulation can handle.
         */
        int maxBodies,
        
        /**
         * Maximum number of body pairs (potential collisions).
         */
        int maxBodyPairs,
        
        /**
         * Maximum number of contact constraints in the simulation.
         */
        int maxContactConstraints,
        
        /**
         * Number of position solver iterations per step.
         */
        int numPositionIterations,
        
        /**
         * Number of velocity solver iterations per step.
         */
        int numVelocityIterations,
        
        /**
         * Speculative contact distance to prevent tunneling.
         */
        float speculativeContactDistance,
        
        /**
         * Baumgarte stabilization factor.
         */
        float baumgarteFactor,
        
        /**
         * Allowable penetration slop before applying restorative forces.
         */
        float penetrationSlop,
        
        /**
         * Time in seconds a body must be inactive before sleeping.
         */
        float timeBeforeSleep,
        
        /**
         * Velocity threshold below which a body is considered resting.
         */
        float pointVelocitySleepThreshold,
        
        /**
         * Y-axis gravity acceleration applied to bodies.
         */
        float gravityY,
        
        /**
         * Size of the temporary memory allocator used by the physics job system.
         */
        int tempAllocatorSize
    ) {}

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

    /**
     * The core physics simulation instance containing the native Jolt engine and handlers.
     */
    private final VxPhysicsSimulation simulation;

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
     * Constructs a new physics world for the given level with a custom configuration.
     * Subsystems are instantiated but not yet initialized.
     *
     * @param level  The server level.
     * @param config The physics configuration.
     */
    private VxPhysicsWorld(ServerLevel level, Config config) {
        this.level = level;
        this.dimensionKey = level.dimension();
        this.simulation = new VxPhysicsSimulation(config);
        this.bodyManager = new VxServerBodyManager(this);
        this.constraintManager = new VxConstraintManager(this.bodyManager);
        this.terrainSystem = new VxTerrainSystem(this, this.level);
        this.ragdollManager = new VxRagdollManager(this);
    }

    /**
     * Retrieves the physics world for a dimension, creating and starting it with default config if it doesn't exist.
     *
     * @param level The server level.
     * @return The physics world instance.
     */
    public static VxPhysicsWorld getOrCreate(ServerLevel level) {
        return getOrCreate(level, new Config(
                65536,         // maxBodies
                65536,         // maxBodyPairs
                65536,         // maxContactConstraints
                10,            // numPositionIterations
                15,            // numVelocityIterations
                0.02f,         // speculativeContactDistance
                0.2f,          // baumgarteFactor
                0.02f,         // penetrationSlop
                1.0f,          // timeBeforeSleep
                0.005f,        // pointVelocitySleepThreshold
                -9.81f,        // gravityY
                64 * 1024 * 1024 // tempAllocatorSize
        ));
    }

    /**
     * Retrieves the physics world for a dimension, creating and starting it with a custom config if it doesn't exist.
     *
     * @param level  The server level.
     * @param config The custom physics configuration.
     * @return The physics world instance.
     */
    public static VxPhysicsWorld getOrCreate(ServerLevel level, Config config) {
        return worlds.computeIfAbsent(level.dimension(), key -> {
            VxPhysicsWorld newWorld = new VxPhysicsWorld(level, config);
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
            this.simulation.initialize(this);
            this.bodyManager.initialize();
            this.constraintManager.initialize();
            this.terrainSystem.initialize();

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

            this.simulation.cleanup();
            this.commandQueue.clear();
        }
    }

    /**
     * Internal simulation step. Handles time accumulation and fixed-step updates.
     *
     * @param deltaTime The real-time delta since the last iteration (in seconds).
     */
    private void updatePhysicsLoop(float deltaTime) {
        if (VxPauseUtil.isPaused() || !this.isRunning || this.simulation.getPhysicsSystem() == null) {
            return;
        }

        this.timeAccumulator += deltaTime;
        if (this.timeAccumulator > MAX_ACCUMULATED_TIME) {
            this.timeAccumulator = MAX_ACCUMULATED_TIME;
        }

        if (this.timeAccumulator >= FIXED_TIME_STEP) {
            long startTime = System.nanoTime();

            this.onPrePhysicsTick();

            int error = this.simulation.update(FIXED_TIME_STEP, 1);
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
        return this.simulation.getPhysicsSystem();
    }

    /**
     * @return The frame timer used to measure physics simulation performance.
     */
    public VxFrameTimer getPhysicsFrameTimer() {
        return this.physicsFrameTimer;
    }

    /**
     * @return The configuration used by this physics world.
     */
    public Config getConfig() {
        return this.simulation.getConfig();
    }

    /**
     * @return The handler for ignored body pairs in this world.
     */
    public BodyPairIgnoreHandler getBodyPairIgnoreHandler() {
        return this.simulation.getBodyPairIgnoreHandler();
    }

    /**
     * @return The handler for terrain contact logic in this world.
     */
    public TerrainContactHandler getTerrainContactHandler() {
        return this.simulation.getTerrainContactHandler();
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