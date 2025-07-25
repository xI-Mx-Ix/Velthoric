package net.xmx.vortex.physics.world;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EPhysicsUpdateError;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.xmx.vortex.debug.drawer.ServerShapeDrawerManager;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.natives.NativeJoltInitializer;
import net.xmx.vortex.physics.constraint.manager.VxConstraintManager;
import net.xmx.vortex.physics.object.physicsobject.IPhysicsObject;
import net.xmx.vortex.physics.object.physicsobject.manager.VxObjectManager;
import net.xmx.vortex.physics.terrain.TerrainSystem;
import net.xmx.vortex.physics.world.pcmd.ICommand;
import net.xmx.vortex.physics.world.pcmd.RunTaskCommand;
import net.xmx.vortex.physics.world.pcmd.UpdatePhysicsStateCommand;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

public final class VxPhysicsWorld implements Runnable, Executor {

    private final int maxBodies;
    private final int maxBodyPairs;
    private final int maxContactConstraints;
    private final int numPositionIterations;
    private final int numVelocityIterations;
    private final float baumgarteFactor;
    private final float penetrationSlop;
    private final float timeBeforeSleep;
    private final float pointVelocitySleepThreshold;
    private final float gravityY;
    private final int maxSubsteps;

    // --- Static Registry & Factory ---

    private static final Map<ResourceKey<Level>, VxPhysicsWorld> worlds = new ConcurrentHashMap<>();

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

    // --- Jolt Layer Definitions ---

    public static class Layers {
        public static final short STATIC = 0;
        public static final short DYNAMIC = 1;
        public static final short NUM_LAYERS = 2;
    }

    public static class BroadPhaseLayers {
        public static final byte STATIC = 0;
        public static final byte DYNAMIC = 1;
        public static final byte NUM_LAYERS = 2;
    }

    // --- Instance Fields: Managers & Game State ---

    private final ServerLevel level;
    private final ResourceKey<Level> dimensionKey;
    private final VxObjectManager objectManager;
    private final VxConstraintManager constraintManager;
    private final TerrainSystem terrainSystem;

    // --- Instance Fields: Core Physics Simulation ---

    private PhysicsSystem physicsSystem;
    private JobSystemThreadPool jobSystem;
    private TempAllocator tempAllocator;

    private final Queue<ICommand> commandQueue = new ConcurrentLinkedQueue<>();
    private volatile Thread physicsThreadExecutor;
    private volatile boolean isRunning = false;
    private volatile boolean isPaused = false;

    private long pauseStartTimeNanos = 0L;
    private final float fixedTimeStep;

    private float timeAccumulator = 0.0f;

    private static final int DEFAULT_SIMULATION_HZ = 60;
    private static final float MAX_ACCUMULATED_TIME = 0.2f;

    private ServerShapeDrawerManager serverShapeDrawerManager;

    // --- Constructor & Lifecycle ---

    private VxPhysicsWorld(ServerLevel level) {
        this.level = level;
        this.dimensionKey = level.dimension();
        this.fixedTimeStep = 1.0f / DEFAULT_SIMULATION_HZ;
        this.objectManager = new VxObjectManager();
        this.constraintManager = new VxConstraintManager(this.objectManager);
        this.terrainSystem = new TerrainSystem(this, this.level);
        this.serverShapeDrawerManager = new ServerShapeDrawerManager(this);

        this.maxBodies = 65536;
        this.maxBodyPairs = 65536;
        this.maxContactConstraints = 10240;

        this.numPositionIterations = 10;
        this.numVelocityIterations = 10;

        this.baumgarteFactor = 0.2f;
        this.penetrationSlop = 0.02f;
        this.timeBeforeSleep = 0.5f;
        this.pointVelocitySleepThreshold = 0.03f;

        this.gravityY = -9.81f;
        this.maxSubsteps = 10;
    }

    private void initializeAndStart() {
        this.objectManager.initialize(this);
        this.constraintManager.initialize(this);
        this.terrainSystem.initialize();

        if (this.physicsThreadExecutor != null && this.physicsThreadExecutor.isAlive()) {
            return;
        }

        this.isRunning = true;
        this.physicsThreadExecutor = new Thread(this, "Vortex Physics World: " + dimensionKey.location().getPath());
        this.physicsThreadExecutor.setDaemon(true);
        this.physicsThreadExecutor.start();
    }

    public void stop() {
        if (!this.isRunning) {
            return;
        }
        this.isRunning = false;

        if (this.terrainSystem != null) {
            this.terrainSystem.shutdown();
        }
        if (this.constraintManager != null) {
            this.constraintManager.shutdown();
        }
        if (this.objectManager != null) {
            this.objectManager.shutdown();
        }

        if (this.physicsThreadExecutor != null) {
            this.physicsThreadExecutor.interrupt();
            try {
                this.physicsThreadExecutor.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        this.physicsThreadExecutor = null;
    }

    // --- Core Simulation Loop & Logic ---

    @Override
    public void run() {
        try {
            NativeJoltInitializer.initialize();
            initializePhysicsSystem();
        } catch (Throwable t) {
            VxMainClass.LOGGER.fatal("Failed to initialize physics system for dimension {}", dimensionKey.location(), t);
            this.isRunning = false;
            cleanupInternal();
            return;
        }

        long lastLoopTimeNanos = System.nanoTime();
        while (this.isRunning) {
            try {
                long currentTimeNanos = System.nanoTime();
                long elapsedNanos = currentTimeNanos - lastLoopTimeNanos;
                lastLoopTimeNanos = currentTimeNanos;
                float deltaTimeSeconds = Math.min(elapsedNanos / 1_000_000_000.0f, 0.1f);

                this.update(deltaTimeSeconds);

                long loopEndTimeNanos = System.nanoTime();
                long actualLoopDurationNanos = loopEndTimeNanos - currentTimeNanos;
                long sleepTimeNanos = (long) (fixedTimeStep * 1_000_000_000.0) - actualLoopDurationNanos;
                if (sleepTimeNanos > 500_000L) {
                    Thread.sleep(sleepTimeNanos / 1_000_000L, (int) (sleepTimeNanos % 1_000_000L));
                }
            } catch (InterruptedException e) {
                this.isRunning = false;
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                VxMainClass.LOGGER.error("Fatal error in physics loop for dimension {}", dimensionKey.location(), t);
                this.isRunning = false;
            }
        }
        cleanupInternal();
    }

    private void update(float deltaTime) {
        processCommandQueue();

        if (this.isPaused || !this.isRunning || physicsSystem == null) {
            if (isPaused && pauseStartTimeNanos == 0L) {
                pauseStartTimeNanos = System.nanoTime();
            }
            return;
        }

        if (pauseStartTimeNanos != 0L) {
            pauseStartTimeNanos = 0L;
        }

        timeAccumulator += deltaTime;
        if (timeAccumulator > MAX_ACCUMULATED_TIME) {
            timeAccumulator = MAX_ACCUMULATED_TIME;
        }

        int substepsPerformed = 0;

        while (timeAccumulator >= this.fixedTimeStep && substepsPerformed < maxSubsteps) {
            int error = physicsSystem.update(this.fixedTimeStep, 1, tempAllocator, jobSystem);
            if (error != EPhysicsUpdateError.None) {
                VxMainClass.LOGGER.error("Jolt physics update failed with error code: {}", error);
                this.isRunning = false;
                return;
            }
            timeAccumulator -= this.fixedTimeStep;
            substepsPerformed++;
        }

        if (this.isRunning) {
            queueCommand(new UpdatePhysicsStateCommand(System.nanoTime()));
        }
    }

    private void processCommandQueue() {
        ICommand command;
        while (physicsSystem != null && isRunning && (command = commandQueue.poll()) != null) {
            try {
                command.execute(this);
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Exception while executing physics command", e);
            }
        }
    }

    public void initializePhysicsSystem() {
        this.tempAllocator = new TempAllocatorMalloc();
        int numThreads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
        this.jobSystem = new JobSystemThreadPool(Jolt.cMaxPhysicsJobs, Jolt.cMaxPhysicsBarriers, numThreads);

        this.physicsSystem = new PhysicsSystem();
        BroadPhaseLayerInterface bpli = NativeJoltInitializer.getBroadPhaseLayerInterface();
        ObjectVsBroadPhaseLayerFilter ovbpf = NativeJoltInitializer.getObjectVsBroadPhaseLayerFilter();
        ObjectLayerPairFilter olpf = NativeJoltInitializer.getObjectLayerPairFilter();

        physicsSystem.init(maxBodies, 0, maxBodyPairs, maxContactConstraints, bpli, ovbpf, olpf);

        PhysicsSettings settings = physicsSystem.getPhysicsSettings();

        settings.setNumPositionSteps(numPositionIterations);
        settings.setNumVelocitySteps(numVelocityIterations);
        settings.setBaumgarte(baumgarteFactor);
        settings.setPenetrationSlop(penetrationSlop);
        settings.setTimeBeforeSleep(timeBeforeSleep);
        settings.setPointVelocitySleepThreshold(pointVelocitySleepThreshold);
        settings.setDeterministicSimulation(true);

        physicsSystem.setGravity(0f, gravityY, 0f);
        physicsSystem.optimizeBroadPhase();
    }

    private void cleanupInternal() {
        if (physicsSystem != null) {
            physicsSystem.close();
            physicsSystem = null;
        }
        if (jobSystem != null) {
            jobSystem.close();
            jobSystem = null;
        }
        if (tempAllocator != null) {
            tempAllocator.close();
            tempAllocator = null;
        }
        commandQueue.clear();
    }

    // --- Public API & Getters ---

    public void queueCommand(ICommand command) {
        if (command != null && this.isRunning) {
            commandQueue.offer(command);
        }
    }

    @Override
    public void execute(Runnable task) {
        queueCommand(new RunTaskCommand(task));
    }

    public VxObjectManager getObjectManager() {
        return objectManager;
    }

    public VxConstraintManager getConstraintManager() {
        return constraintManager;
    }

    public TerrainSystem getTerrainSystem() {
        return terrainSystem;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public ResourceKey<Level> getDimensionKey() {
        return dimensionKey;
    }

    public Optional<IPhysicsObject> findPhysicsObjectByBodyId(int bodyId) {
        return objectManager.getObjectByBodyId(bodyId);
    }

    public float getFixedTimeStep() {
        return this.fixedTimeStep;
    }

    public boolean isRunning() {
        return isRunning && physicsThreadExecutor != null && physicsThreadExecutor.isAlive();
    }

    public boolean isPaused() {
        return isPaused;
    }

    public void pause() {
        execute(() -> this.isPaused = true);
    }

    public void resume() {
        execute(() -> this.isPaused = false);
    }

    @Nullable
    public PhysicsSystem getPhysicsSystem() {
        return physicsSystem;
    }

    @Nullable
    public BodyInterface getBodyInterface() {
        return physicsSystem != null ? physicsSystem.getBodyInterface() : null;
    }

    @Nullable
    public BodyLockInterface getBodyLockInterface() {
        return physicsSystem != null ? physicsSystem.getBodyLockInterface() : null;
    }

    @Nullable
    public BodyLockInterface getBodyLockInterfaceNoLock() {
        return physicsSystem != null ? physicsSystem.getBodyLockInterfaceNoLock() : null;
    }

    @Nullable
    public NarrowPhaseQuery getNarrowPhaseQuery() {
        return physicsSystem != null ? physicsSystem.getNarrowPhaseQuery() : null;
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
    public static TerrainSystem getTerrainSystem(ResourceKey<Level> dimensionKey) {
        VxPhysicsWorld world = get(dimensionKey);
        return world != null ? world.getTerrainSystem() : null;
    }

    public static Collection<VxPhysicsWorld> getAll() {
        return Collections.unmodifiableCollection(worlds.values());
    }

    public ServerShapeDrawerManager getDebugDrawerManager() {
        return serverShapeDrawerManager;
    }
}