package net.xmx.xbullet.physics.world;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EPhysicsUpdateError;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.xmx.xbullet.init.ModConfig;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.natives.NativeJoltInitializer;
import net.xmx.xbullet.physics.constraint.manager.ConstraintManager;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManager;
import net.xmx.xbullet.physics.world.pcmd.ICommand;
import net.xmx.xbullet.physics.world.pcmd.RunTaskCommand;
import net.xmx.xbullet.physics.world.pcmd.UpdatePhysicsStateCommand;
import net.xmx.xbullet.physics.terrain.manager.TerrainSystem;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

public final class PhysicsWorld implements Runnable, Executor {

    // --- Static Registry & Factory ---

    private static final Map<ResourceKey<Level>, PhysicsWorld> worlds = new ConcurrentHashMap<>();

    public static PhysicsWorld getOrCreate(ServerLevel level) {
        return worlds.computeIfAbsent(level.dimension(), key -> {
            PhysicsWorld newWorld = new PhysicsWorld(level);
            newWorld.initializeAndStart();
            return newWorld;
        });
    }

    @Nullable
    public static PhysicsWorld get(ResourceKey<Level> dimensionKey) {
        return worlds.get(dimensionKey);
    }

    public static void shutdown(ResourceKey<Level> dimensionKey) {
        PhysicsWorld world = worlds.remove(dimensionKey);
        if (world != null) {
            world.stop();
        }
    }

    public static void shutdownAll() {
        new ArrayList<>(worlds.keySet()).forEach(PhysicsWorld::shutdown);
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
    private final PhysicsObjectManager physicsObjectManager;
    private final ConstraintManager constraintManager;
    private final TerrainSystem terrainSystem;

    // --- Instance Fields: Core Physics Simulation ---

    private PhysicsSystem physicsSystem;
    private JobSystemThreadPool jobSystem;
    private TempAllocator tempAllocator;

    private final Queue<ICommand> commandQueue = new ConcurrentLinkedQueue<>();
    private volatile Thread physicsThreadExecutor;
    private volatile boolean isRunning = false;
    private volatile boolean isPaused = false;

    private long accumulatedPauseTimeNanos = 0L;
    private long pauseStartTimeNanos = 0L;
    private final float fixedTimeStep;
    private float timeAccumulator = 0.0f;

    private static final int DEFAULT_SIMULATION_HZ = 60;
    private static final float MAX_ACCUMULATED_TIME = 0.2f;

    // --- Constructor & Lifecycle ---

    private PhysicsWorld(ServerLevel level) {
        this.level = level;
        this.dimensionKey = level.dimension();
        this.fixedTimeStep = 1.0f / DEFAULT_SIMULATION_HZ;
        this.physicsObjectManager = new PhysicsObjectManager();
        this.constraintManager = new ConstraintManager(this.physicsObjectManager);
        this.terrainSystem = new TerrainSystem(this, this.level);
    }

    private void initializeAndStart() {
        this.physicsObjectManager.initialize(this);
        this.constraintManager.initialize(this);
        this.terrainSystem.initialize(this.physicsObjectManager);

        if (this.physicsThreadExecutor != null && this.physicsThreadExecutor.isAlive()) {
            return;
        }

        this.isRunning = true;
        this.physicsThreadExecutor = new Thread(this, "XBullet-Jolt-Physics-" + dimensionKey.location().getPath());
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
        if (this.physicsObjectManager != null) {
            this.physicsObjectManager.shutdown();
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
            accumulatedPauseTimeNanos += System.nanoTime() - pauseStartTimeNanos;
            pauseStartTimeNanos = 0L;
        }

        timeAccumulator += deltaTime;
        if (timeAccumulator > MAX_ACCUMULATED_TIME) {
            timeAccumulator = MAX_ACCUMULATED_TIME;
        }

        final int maxSubSteps = ModConfig.MAX_SUBSTEPS.get();
        int substepsPerformed = 0;

        while (timeAccumulator >= this.fixedTimeStep && substepsPerformed < maxSubSteps) {
            int error = physicsSystem.update(this.fixedTimeStep, 1, tempAllocator, jobSystem);
            if (error != EPhysicsUpdateError.None) {
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
                XBullet.LOGGER.error("Exception while executing command in processCommandQueue", e);
            }
        }
    }


    private void initializePhysicsSystem() {
        this.tempAllocator = new TempAllocatorMalloc();
        int numThreads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
        this.jobSystem = new JobSystemThreadPool(Jolt.cMaxPhysicsJobs, Jolt.cMaxPhysicsBarriers, numThreads);

        this.physicsSystem = new PhysicsSystem();
        BroadPhaseLayerInterface bpli = NativeJoltInitializer.getBroadPhaseLayerInterface();
        ObjectVsBroadPhaseLayerFilter ovbpf = NativeJoltInitializer.getObjectVsBroadPhaseLayerFilter();
        ObjectLayerPairFilter olpf = NativeJoltInitializer.getObjectLayerPairFilter();

        physicsSystem.init(ModConfig.MAX_BODIES.get(), 0, ModConfig.MAX_BODY_PAIRS.get(),
                ModConfig.MAX_CONTACT_CONSTRAINTS.get(), bpli, ovbpf, olpf);

        PhysicsSettings settings = physicsSystem.getPhysicsSettings();
        settings.setNumPositionSteps(ModConfig.NUM_ITERATIONS.get());
        settings.setNumVelocitySteps(ModConfig.NUM_ITERATIONS.get());
        settings.setBaumgarte(ModConfig.ERP.get().floatValue());
        physicsSystem.setGravity(0f, -9.81f, 0f);
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

    public void execute(Runnable task) {
        queueCommand(new RunTaskCommand(task));
    }

    public PhysicsObjectManager getObjectManager() {
        return physicsObjectManager;
    }

    public ConstraintManager getConstraintManager() {
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

    public Map<UUID, IPhysicsObject> getPhysicsObjectsMap() {
        return physicsObjectManager.getManagedObjects();
    }

    public Optional<IPhysicsObject> findPhysicsObjectByBodyId(int bodyId) {
        return physicsObjectManager.getObjectByBodyId(bodyId);
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
    public static PhysicsObjectManager getObjectManager(ResourceKey<Level> dimensionKey) {
        PhysicsWorld world = get(dimensionKey);
        return world != null ? world.getObjectManager() : null;
    }

    @Nullable
    public static ConstraintManager getConstraintManager(ResourceKey<Level> dimensionKey) {
        PhysicsWorld world = get(dimensionKey);
        return world != null ? world.getConstraintManager() : null;
    }

    @Nullable
    public static TerrainSystem getTerrainSystem(ResourceKey<Level> dimensionKey) {
        PhysicsWorld world = get(dimensionKey);
        return world != null ? world.getTerrainSystem() : null;
    }

    public static Collection<PhysicsWorld> getAll() {
        return Collections.unmodifiableCollection(worlds.values());
    }
}