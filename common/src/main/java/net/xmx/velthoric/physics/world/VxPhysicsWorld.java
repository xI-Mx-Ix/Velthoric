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
import net.minecraft.util.FrameTimer;
import net.minecraft.world.level.Level;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.natives.VxNativeJolt;
import net.xmx.velthoric.physics.constraint.manager.VxConstraintManager;
import net.xmx.velthoric.physics.buoyancy.VxBuoyancyManager;
import net.xmx.velthoric.physics.object.manager.VxObjectManager;
import net.xmx.velthoric.physics.mounting.manager.VxMountingManager;
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
    private static final int SIMULATION_HZ = 60;
    private static final float FIXED_TIME_STEP = 1.0f / SIMULATION_HZ;
    private static final float MAX_ACCUMULATED_TIME = 5.0f * FIXED_TIME_STEP;
    private static final int MAX_COMMANDS_PER_TICK = 4096;
    private static final Map<ResourceKey<Level>, VxPhysicsWorld> worlds = new ConcurrentHashMap<>();

    private final ServerLevel level;
    private final ResourceKey<Level> dimensionKey;
    private final VxObjectManager objectManager;
    private final VxConstraintManager constraintManager;
    private final VxTerrainSystem terrainSystem;
    private final VxMountingManager mountingManager;
    private final VxBuoyancyManager buoyancyManager;

    private final FrameTimer physicsFrameTimer = new FrameTimer();

    private PhysicsSystem physicsSystem;
    private JobSystemThreadPool jobSystem;
    private TempAllocator tempAllocator;

    private final Queue<ICommand> commandQueue = new ConcurrentLinkedQueue<>();
    private volatile Thread physicsThreadExecutor;
    private volatile boolean isRunning = false;
    private volatile boolean isPaused = false;
    private float timeAccumulator = 0.0f;
    private long lastTimeNanos = 0L;

    private VxPhysicsWorld(ServerLevel level) {
        this.level = level;
        this.dimensionKey = level.dimension();
        this.objectManager = new VxObjectManager(this);
        this.constraintManager = new VxConstraintManager(this.objectManager);
        this.terrainSystem = new VxTerrainSystem(this, this.level);
        this.mountingManager = new VxMountingManager(this);
        this.buoyancyManager = new VxBuoyancyManager(this);
    }

    public static VxPhysicsWorld getOrCreate(ServerLevel level) {
        return worlds.computeIfAbsent(level.dimension(), key -> {
            VxPhysicsWorld newWorld = new VxPhysicsWorld(level);
            newWorld.initializeAndStart();
            return newWorld;
        });
    }

    @Nullable
    public static VxPhysicsWorld get(ResourceKey<Level> dimensionKey) {
        return worlds.get(dimensionKey);
    }

    public static void shutdown(ResourceKey<Level> dimensionKey) {
        VxPhysicsWorld world = worlds.remove(dimensionKey);
        if (world != null) {
            world.stop();
        }
    }

    public static void shutdownAll() {
        new ArrayList<>(worlds.keySet()).forEach(VxPhysicsWorld::shutdown);
        worlds.clear();
    }

    private void initializeAndStart() {
        if (this.physicsThreadExecutor != null && this.physicsThreadExecutor.isAlive()) {
            return;
        }

        this.objectManager.initialize();
        this.constraintManager.initialize();
        this.terrainSystem.initialize();

        this.isRunning = true;
        String threadName = "Velthoric Physics Thread - " + dimensionKey.location().getPath().replace('/', '_');
        this.physicsThreadExecutor = new Thread(this, threadName);
        this.physicsThreadExecutor.setDaemon(true);
        this.physicsThreadExecutor.start();
    }

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

    @Override
    public void run() {
        try {
            VxNativeJolt.initialize();
            initializePhysicsSystem();

            this.lastTimeNanos = System.nanoTime();

            while (this.isRunning) {
                processCommandQueue();

                long currentTimeNanos = System.nanoTime();
                if (this.isPaused) {
                    this.lastTimeNanos = currentTimeNanos;
                    Thread.sleep(10);
                    continue;
                }

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

    private void updatePhysicsLoop(float deltaTime) {
        if (this.isPaused || !this.isRunning || this.physicsSystem == null) {
            return;
        }

        this.timeAccumulator += deltaTime;
        if (this.timeAccumulator > MAX_ACCUMULATED_TIME) {
            this.timeAccumulator = MAX_ACCUMULATED_TIME;
        }

        if (this.timeAccumulator >= FIXED_TIME_STEP) {
            long startTime = System.nanoTime();

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


    public void onPhysicsTick() {
        this.objectManager.onPhysicsTick(this);
        this.buoyancyManager.applyBuoyancyForces(FIXED_TIME_STEP);
    }

    public void onGameTick(ServerLevel level) {
        this.objectManager.onGameTick(level);
        this.mountingManager.onGameTick();
        this.buoyancyManager.updateFluidStates();
    }

    private void processCommandQueue() {
        for (int i = 0; i < MAX_COMMANDS_PER_TICK; i++) {
            ICommand command = this.commandQueue.poll();
            if (command == null) {
                break;
            }
            try {
                command.execute(this);
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Exception while executing physics command", e);
            }
        }
    }

    public void initializePhysicsSystem() {
        final int maxBodies = 65536;
        final int maxBodyPairs = 65536;
        final int maxContactConstraints = 65536;
        final int numPositionIterations = 10;
        final int numVelocityIterations = 15;
        final float speculativeContactDistance = 0.02f;
        final float baumgarteFactor = 0.2f;
        final float penetrationSlop = 0.001f;
        final float timeBeforeSleep = 1.0f;
        final float pointVelocitySleepThreshold = 0.005f;
        final float gravityY = -9.81f;

        this.tempAllocator = new TempAllocatorImpl(64 * 1024 * 1024);
        int numThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        this.jobSystem = new JobSystemThreadPool(Jolt.cMaxPhysicsJobs, Jolt.cMaxPhysicsBarriers, numThreads);

        this.physicsSystem = new PhysicsSystem();
        BroadPhaseLayerInterface bpli = VxNativeJolt.getBroadPhaseLayerInterface();
        ObjectVsBroadPhaseLayerFilter ovbpf = VxNativeJolt.getObjectVsBroadPhaseLayerFilter();
        ObjectLayerPairFilter olpf = VxNativeJolt.getObjectLayerPairFilter();

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
        this.physicsSystem.optimizeBroadPhase();
    }

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
        if (this.buoyancyManager != null) {
            this.buoyancyManager.shutdown();
        }
    }

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

    public void queueCommand(ICommand command) {
        if (command != null && this.isRunning) {
            this.commandQueue.offer(command);
        }
    }

    @Override
    public void execute(@NotNull Runnable task) {
        RunTaskCommand.queue(this, task);
    }

    public VxObjectManager getObjectManager() {
        return this.objectManager;
    }

    public VxConstraintManager getConstraintManager() {
        return this.constraintManager;
    }

    public VxTerrainSystem getTerrainSystem() {
        return this.terrainSystem;
    }

    public VxMountingManager getMountingManager() {
        return this.mountingManager;
    }

    public VxBuoyancyManager getBuoyancyManager() {
        return this.buoyancyManager;
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

    public void pause() {
        this.execute(() -> this.isPaused = true);
    }

    public void resume() {
        this.execute(() -> this.isPaused = false);
    }

    @Nullable
    public PhysicsSystem getPhysicsSystem() {
        return this.physicsSystem;
    }

    public FrameTimer getPhysicsFrameTimer() {
        return this.physicsFrameTimer;
    }

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
    public static VxMountingManager getMountingManager(ResourceKey<Level> dimensionKey) {
        VxPhysicsWorld world = get(dimensionKey);
        return world != null ? world.getMountingManager() : null;
    }

    @Nullable
    public static VxBuoyancyManager getBuoyancyManager(ResourceKey<Level> dimensionKey) {
        VxPhysicsWorld world = get(dimensionKey);
        return world != null ? world.getBuoyancyManager() : null;
    }

    public static Collection<VxPhysicsWorld> getAll() {
        return Collections.unmodifiableCollection(worlds.values());
    }
}