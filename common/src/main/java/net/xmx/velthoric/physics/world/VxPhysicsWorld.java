/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.world;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EPhysicsUpdateError;
import com.github.stephengold.joltjni.readonly.ConstBodyLockInterfaceLocking;
import com.github.stephengold.joltjni.readonly.ConstBodyLockInterfaceNoLock;
import com.github.stephengold.joltjni.readonly.ConstNarrowPhaseQuery;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.natives.VxNativeJolt;
import net.xmx.velthoric.physics.constraint.manager.VxConstraintManager;
import net.xmx.velthoric.physics.object.manager.VxObjectManager;
import net.xmx.velthoric.physics.riding.RidingManager;
import net.xmx.velthoric.physics.terrain.VxTerrainSystem;
import net.xmx.velthoric.physics.world.pcmd.ICommand;
import net.xmx.velthoric.physics.world.pcmd.RunTaskCommand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

/**
 * Manages the entire physics simulation for a single Minecraft dimension.
 * Each instance runs its own dedicated thread to perform physics calculations,
 * keeping the simulation decoupled from the main server tick rate. It handles the
 * Jolt Physics System lifecycle, manages subsystems like objects and terrain, and
 * provides a thread-safe command queue for interacting with the simulation.
 *
 * @author xI-Mx-Ix
 */
public final class VxPhysicsWorld implements Runnable, Executor {

    /** The target frequency of the physics simulation in Hertz. */
    private static final int SIMULATION_HZ = 60;
    /** The fixed time step for each physics update, derived from SIMULATION_HZ. */
    private static final float FIXED_TIME_STEP = 1.0f / SIMULATION_HZ;

    /** The maximum amount of time that can be accumulated to prevent a "spiral of death" if the server lags. */
    private static final float MAX_ACCUMULATED_TIME = 5.0f * FIXED_TIME_STEP;
    /** The maximum number of commands to process from the queue in a single physics tick. */
    private static final int MAX_COMMANDS_PER_TICK = 1024;

    /** A static map holding the physics world instance for each active dimension. */
    private static final Map<ResourceKey<Level>, VxPhysicsWorld> worlds = new ConcurrentHashMap<>();

    // --- Sub-systems ---
    private final ServerLevel level;
    private final ResourceKey<Level> dimensionKey;
    private final VxObjectManager objectManager;
    private final VxConstraintManager constraintManager;
    private final VxTerrainSystem terrainSystem;
    private final RidingManager ridingManager;

    // --- Jolt Physics Core Components ---
    private PhysicsSystem physicsSystem;
    private JobSystemThreadPool jobSystem;
    private TempAllocator tempAllocator;

    // --- Threading and State Management ---
    /** A thread-safe queue for commands to be executed on the physics thread. */
    private final Queue<ICommand> commandQueue = new ConcurrentLinkedQueue<>();
    /** The dedicated thread that runs the physics simulation loop. */
    private volatile Thread physicsThreadExecutor;
    /** A flag to control the main loop of the physics thread. */
    private volatile boolean isRunning = false;
    /** A flag to pause the simulation updates without stopping the thread. */
    private volatile boolean isPaused = false;
    /** Accumulates delta time to drive the fixed-step simulation. */
    private float timeAccumulator = 0.0f;

    private VxPhysicsWorld(ServerLevel level) {
        this.level = level;
        this.dimensionKey = level.dimension();
        this.objectManager = new VxObjectManager(this);
        this.constraintManager = new VxConstraintManager(this.objectManager);
        this.terrainSystem = new VxTerrainSystem(this, this.level);
        this.ridingManager = new RidingManager(this);
    }

    /**
     * Gets the physics world for a given server level, creating and starting it if it doesn't exist.
     *
     * @param level The server level.
     * @return The corresponding {@link VxPhysicsWorld} instance.
     */
    public static VxPhysicsWorld getOrCreate(ServerLevel level) {
        return worlds.computeIfAbsent(level.dimension(), key -> {
            VxPhysicsWorld newWorld = new VxPhysicsWorld(level);
            newWorld.initializeAndStart();
            return newWorld;
        });
    }

    /**
     * Gets the physics world for a given dimension key, if it exists.
     *
     * @param dimensionKey The key of the dimension.
     * @return The {@link VxPhysicsWorld} instance, or null if not found.
     */
    @Nullable
    public static VxPhysicsWorld get(ResourceKey<Level> dimensionKey) {
        return worlds.get(dimensionKey);
    }

    /**
     * Stops and removes the physics world for a specific dimension.
     *
     * @param dimensionKey The key of the dimension to shut down.
     */
    public static void shutdown(ResourceKey<Level> dimensionKey) {
        VxPhysicsWorld world = worlds.remove(dimensionKey);
        if (world != null) {
            world.stop();
        }
    }

    /**
     * Shuts down all running physics worlds.
     */
    public static void shutdownAll() {
        new ArrayList<>(worlds.keySet()).forEach(VxPhysicsWorld::shutdown);
        worlds.clear();
    }

    /**
     * Initializes all subsystems and starts the dedicated physics thread.
     */
    private void initializeAndStart() {
        if (this.physicsThreadExecutor != null && this.physicsThreadExecutor.isAlive()) {
            return;
        }

        this.objectManager.initialize();
        this.constraintManager.initialize(this);
        this.terrainSystem.initialize();

        this.isRunning = true;
        String threadName = "Velthoric Physics Thread - " + dimensionKey.location().getPath().replace('/', '_');
        this.physicsThreadExecutor = new Thread(this, threadName);
        this.physicsThreadExecutor.setDaemon(true);
        this.physicsThreadExecutor.start();
    }

    /**
     * Signals the physics thread to stop and waits for it to terminate.
     */
    public void stop() {
        if (!this.isRunning) {
            return;
        }
        this.isRunning = false;
        if (this.physicsThreadExecutor != null && this.physicsThreadExecutor.isAlive()) {
            try {
                this.physicsThreadExecutor.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                VxMainClass.LOGGER.warn("The server thread was interrupted while waiting for the physics thread {} to stop completely.", this.physicsThreadExecutor.getName());
            }
        }
    }

    /**
     * The main entry point for the physics thread. Contains the simulation loop.
     */
    @Override
    public void run() {
        try {
            VxNativeJolt.initialize();
            initializePhysicsSystem();

            long lastTimeNanos = System.nanoTime();

            while (this.isRunning) {
                // Calculate delta time for this frame.
                long currentTimeNanos = System.nanoTime();
                float deltaTime = (currentTimeNanos - lastTimeNanos) / 1_000_000_000.0f;
                lastTimeNanos = currentTimeNanos;

                this.update(deltaTime);

                // Sleep briefly to yield CPU time.
                Thread.sleep(1);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            VxMainClass.LOGGER.fatal("Fatal error in physics loop for dimension {}", dimensionKey.location(), t);
            this.isRunning = false;
        } finally {
            // Ensure all systems are shut down and native memory is released.
            shutdownInternalSystems();
            cleanupJolt();
        }
    }

    /**
     * Performs a single update step of the physics simulation.
     *
     * @param deltaTime The time elapsed since the last update.
     */
    private void update(float deltaTime) {
        processCommandQueue();

        if (this.isPaused || !this.isRunning || this.physicsSystem == null) {
            return;
        }

        // Add the frame's delta time to the accumulator.
        this.timeAccumulator += deltaTime;

        // Clamp the accumulator to prevent the simulation from trying to catch up too much at once.
        if (this.timeAccumulator > MAX_ACCUMULATED_TIME) {
            this.timeAccumulator = MAX_ACCUMULATED_TIME;
        }

        // Perform a fixed-step update if enough time has accumulated.
        if (this.timeAccumulator >= FIXED_TIME_STEP) {
            int error = this.physicsSystem.update(FIXED_TIME_STEP, 1, this.tempAllocator, this.jobSystem);
            if (error != EPhysicsUpdateError.None) {
                VxMainClass.LOGGER.error("Jolt physics update failed with error code: {}. Shutting down world.", error);
                this.isRunning = false;
                return;
            }
            this.timeAccumulator -= FIXED_TIME_STEP;
        }

        // Tick the object manager after the physics step to sync states.
        this.objectManager.onPhysicsTick(System.nanoTime());
    }

    /**
     * Processes commands from the queue to be executed on the physics thread.
     */
    private void processCommandQueue() {
        for (int i = 0; i < MAX_COMMANDS_PER_TICK; i++) {
            ICommand command = this.commandQueue.poll();
            if (command == null) {
                break; // Queue is empty.
            }
            try {
                command.execute(this);
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Exception while executing physics command", e);
            }
        }
    }

    /**
     * Initializes the core Jolt physics system with configured settings.
     */
    public void initializePhysicsSystem() {
        // --- Simulation Parameters ---
        final int maxBodies = 65536;
        final int maxBodyPairs = 65536;
        final int maxContactConstraints = 20480;
        final int numPositionIterations = 4;
        final int numVelocityIterations = 8;
        final float speculativeContactDistance = 0.02f;
        final float baumgarteFactor = 0.2f;
        final float penetrationSlop = 0.001f;
        final float timeBeforeSleep = 0.4f;
        final float pointVelocitySleepThreshold = 0.005f;
        final float gravityY = -9.81f; // Standard gravity

        // --- Jolt System Initialization ---
        this.tempAllocator = new TempAllocatorImpl(64 * 1024 * 1024); // 64 MB temporary allocator
        int numThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        this.jobSystem = new JobSystemThreadPool(Jolt.cMaxPhysicsJobs, Jolt.cMaxPhysicsBarriers, numThreads);

        this.physicsSystem = new PhysicsSystem();
        BroadPhaseLayerInterface bpli = VxNativeJolt.getBroadPhaseLayerInterface();
        ObjectVsBroadPhaseLayerFilter ovbpf = VxNativeJolt.getObjectVsBroadPhaseLayerFilter();
        ObjectLayerPairFilter olpf = VxNativeJolt.getObjectLayerPairFilter();

        this.physicsSystem.init(maxBodies, 0, maxBodyPairs, maxContactConstraints, bpli, ovbpf, olpf);

        // Apply physics settings
        try (PhysicsSettings settings = this.physicsSystem.getPhysicsSettings()) {
            settings.setNumPositionSteps(numPositionIterations);
            settings.setNumVelocitySteps(numVelocityIterations);
            settings.setSpeculativeContactDistance(speculativeContactDistance);
            settings.setBaumgarte(baumgarteFactor);
            settings.setPenetrationSlop(penetrationSlop);
            settings.setTimeBeforeSleep(timeBeforeSleep);
            settings.setPointVelocitySleepThreshold(pointVelocitySleepThreshold);
            settings.setDeterministicSimulation(false); // Non-deterministic is faster
            this.physicsSystem.setPhysicsSettings(settings);
        }
        this.physicsSystem.setGravity(0f, gravityY, 0f);
        this.physicsSystem.optimizeBroadPhase();
    }

    /**
     * Shuts down all internal Velthoric subsystems.
     */
    private void shutdownInternalSystems() {
        if (this.terrainSystem != null) {
            this.terrainSystem.shutdown();
        }
        if (this.constraintManager != null) {
            this.constraintManager.shutdown();
        }
        if (this.objectManager != null) {
            this.objectManager.shutdown();
        }
    }

    /**
     * Cleans up and releases all native Jolt physics resources.
     */
    private void cleanupJolt() {
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
     * Queues a command to be executed on the physics thread.
     *
     * @param command The command to queue.
     */
    public void queueCommand(ICommand command) {
        if (command != null && this.isRunning) {
            this.commandQueue.offer(command);
        }
    }

    /**
     * Executes a {@link Runnable} task on the physics thread.
     * This is an implementation of the {@link Executor} interface.
     *
     * @param task The task to execute.
     */
    @Override
    public void execute(@NotNull Runnable task) {
        RunTaskCommand.queue(this, task);
    }

    // --- Getters for subsystems and state ---

    public VxObjectManager getObjectManager() {
        return this.objectManager;
    }

    public VxConstraintManager getConstraintManager() {
        return this.constraintManager;
    }

    public VxTerrainSystem getTerrainSystem() {
        return this.terrainSystem;
    }

    public RidingManager getRidingManager() {
        return this.ridingManager;
    }

    public ServerLevel getLevel() {
        return this.level;
    }

    public ResourceKey<Level> getDimensionKey() {
        return this.dimensionKey;
    }

    public float getFixedTimeStep() {
        return FIXED_TIME_STEP;
    }

    public boolean isRunning() {
        return this.isRunning && this.physicsThreadExecutor != null && this.physicsThreadExecutor.isAlive();
    }

    public boolean isPaused() {
        return this.isPaused;
    }

    /**
     * Pauses the physics simulation. This is a thread-safe operation.
     */
    public void pause() {
        this.execute(() -> this.isPaused = true);
    }

    /**
     * Resumes the physics simulation. This is a thread-safe operation.
     */
    public void resume() {
        this.execute(() -> this.isPaused = false);
    }

    // --- Getters for Jolt interfaces ---

    @Nullable
    public PhysicsSystem getPhysicsSystem() {
        return this.physicsSystem;
    }

    @Nullable
    public BodyInterface getBodyInterface() {
        return this.physicsSystem != null ? this.physicsSystem.getBodyInterface() : null;
    }

    @Nullable
    public ConstBodyLockInterfaceLocking getBodyLockInterface() {
        return this.physicsSystem != null ? this.physicsSystem.getBodyLockInterface() : null;
    }

    @Nullable
    public ConstBodyLockInterfaceNoLock getBodyLockInterfaceNoLock() {
        return this.physicsSystem != null ? this.physicsSystem.getBodyLockInterfaceNoLock() : null;
    }

    @Nullable
    public ConstNarrowPhaseQuery getNarrowPhaseQuery() {
        return this.physicsSystem != null ? this.physicsSystem.getNarrowPhaseQuery() : null;
    }

    // --- Static Getters for subsystems ---

    @Nullable
    public static VxObjectManager getObjectManager(ResourceKey<Level> dimensionKey) {
        VxPhysicsWorld world = get(dimensionKey);
        return world != null ? world.getObjectManager() : null;
    }

    @Nullable
    public static VxConstraintManager getConstraintManager(ResourceKey<Level> dimensionKey) {
        VxPhysicsWorld world = get(dimensionKey);
        return world != null ? world.getConstraintManager() : null;
    }

    @Nullable
    public static VxTerrainSystem getTerrainSystem(ResourceKey<Level> dimensionKey) {
        VxPhysicsWorld world = get(dimensionKey);
        return world != null ? world.getTerrainSystem() : null;
    }

    @Nullable
    public static RidingManager getRidingManager(ResourceKey<Level> dimensionKey) {
        VxPhysicsWorld world = get(dimensionKey);
        return world != null ? world.getRidingManager() : null;
    }

    /**
     * @return An unmodifiable collection of all active physics world instances.
     */
    public static Collection<VxPhysicsWorld> getAll() {
        return Collections.unmodifiableCollection(worlds.values());
    }
}