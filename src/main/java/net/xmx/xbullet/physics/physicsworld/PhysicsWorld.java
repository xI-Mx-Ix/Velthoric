package net.xmx.xbullet.physics.physicsworld;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EPhysicsUpdateError;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.xmx.xbullet.init.ModConfig;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.natives.NativeJoltInitializer;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.physicsworld.pcmd.ICommand;
import net.xmx.xbullet.physics.physicsworld.pcmd.RunTaskCommand;
import net.xmx.xbullet.physics.physicsworld.pcmd.UpdatePhysicsStateCommand;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PhysicsWorld implements Runnable {

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

    private PhysicsSystem physicsSystem;
    private JobSystemThreadPool jobSystem;
    private TempAllocator tempAllocator;

    private long accumulatedPauseTimeNanos = 0L;
    private long pauseStartTimeNanos = 0L;
    private final float fixedTimeStep;
    private float timeAccumulator = 0.0f;

    private final Map<UUID, IPhysicsObject> physicsObjectsMap = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> bodyIds = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> bodyIdToUuidMap = new ConcurrentHashMap<>();
    private final Map<UUID, Constraint> physicsJointsMap = new ConcurrentHashMap<>();

    private final Map<UUID, PhysicsTransform> syncedTransforms = new ConcurrentHashMap<>();
    private final Map<UUID, Vec3> syncedLinearVelocities = new ConcurrentHashMap<>();
    private final Map<UUID, Vec3> syncedAngularVelocities = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> syncedActiveStates = new ConcurrentHashMap<>();
    private final Map<UUID, float[]> syncedSoftBodyVertexData = new ConcurrentHashMap<>();
    private final Map<UUID, Long> syncedStateTimestampsNanos = new ConcurrentHashMap<>();

    private final Queue<ICommand> commandQueue = new ConcurrentLinkedQueue<>();
    private volatile Thread physicsThreadExecutor;
    private volatile boolean isRunning = false;
    private volatile boolean isPaused = false;
    private static final int DEFAULT_SIMULATION_HZ = 60;
    private static final float MAX_ACCUMULATED_TIME = 0.2f;
    private final ResourceKey<Level> dimensionKey;

    public PhysicsWorld(ResourceKey<Level> dimensionKey) {
        this(dimensionKey, DEFAULT_SIMULATION_HZ);
    }

    public PhysicsWorld(ResourceKey<Level> dimensionKey, int simulationFrequencyHz) {
        if (simulationFrequencyHz <= 0) {
            simulationFrequencyHz = DEFAULT_SIMULATION_HZ;
        }
        this.dimensionKey = dimensionKey;
        this.fixedTimeStep = 1.0f / simulationFrequencyHz;
    }

    public void initialize() {
        if (this.physicsThreadExecutor != null && this.physicsThreadExecutor.isAlive()) {
            return;
        }
        this.isRunning = true;
        this.physicsThreadExecutor = new Thread(this, "XBullet-Jolt-Physics-" + dimensionKey.location().getPath());
        this.physicsThreadExecutor.setDaemon(true);
        this.physicsThreadExecutor.start();
    }

    @Override
    public void run() {
        try {
            NativeJoltInitializer.initialize();
            initializePhysicsSystem();
        } catch (Throwable t) {
            XBullet.LOGGER.error("FATAL: Failed to initialize Jolt physics for dimension {}. The physics thread will not run.", dimensionKey.location(), t);
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
                XBullet.LOGGER.error("An uncaught exception occurred in the main physics loop for dimension {}. The simulation will now shut down to prevent further issues.", dimensionKey.location(), t);
                this.isRunning = false;
            }
        }
        cleanupInternal();
    }

    public void update(float deltaTime) {

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
            XBullet.LOGGER.warn("Physics simulation for dimension {} is running slow. Capping accumulated time to {}.", dimensionKey.location(), MAX_ACCUMULATED_TIME);
            timeAccumulator = MAX_ACCUMULATED_TIME;
        }

        final int maxSubSteps = ModConfig.MAX_SUBSTEPS.get();
        int substepsPerformed = 0;

        while (timeAccumulator >= this.fixedTimeStep && substepsPerformed < maxSubSteps) {
            try {
                int error = physicsSystem.update(this.fixedTimeStep, 1, tempAllocator, jobSystem);
                if (error != EPhysicsUpdateError.None) {
                    XBullet.LOGGER.error("Jolt physicsSystem.update returned an error: {}. Shutting down physics thread for dimension {}.", error, dimensionKey.location());
                    this.isRunning = false;
                    return;
                }
            } catch (Exception e) {
                XBullet.LOGGER.error("PhysicsThread: Exception during one physics sub-step. The simulation will be shut down.", e);
                this.isRunning = false;
                return;
            }
            timeAccumulator -= this.fixedTimeStep;
            substepsPerformed++;
        }

        if (this.isRunning) {
            new UpdatePhysicsStateCommand(System.nanoTime()).execute(this);
        }
    }

    private void initializePhysicsSystem() {

        this.tempAllocator = new TempAllocatorMalloc();

        int numThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        this.jobSystem = new JobSystemThreadPool(Jolt.cMaxPhysicsJobs, Jolt.cMaxPhysicsBarriers, numThreads);

        this.physicsSystem = new PhysicsSystem();

        BroadPhaseLayerInterface bpli = NativeJoltInitializer.getBroadPhaseLayerInterface();
        ObjectVsBroadPhaseLayerFilter ovbpf = NativeJoltInitializer.getObjectVsBroadPhaseLayerFilter();
        ObjectLayerPairFilter olpf = NativeJoltInitializer.getObjectLayerPairFilter();

        int maxBodies = ModConfig.MAX_BODIES.get();
        int numBodyMutexes = 0;
        int maxBodyPairs = ModConfig.MAX_BODY_PAIRS.get();
        int maxContactConstraints = ModConfig.MAX_CONTACT_CONSTRAINTS.get();

        physicsSystem.init(maxBodies, numBodyMutexes, maxBodyPairs, maxContactConstraints,
                bpli,
                ovbpf,
                olpf
        );

        PhysicsSettings settings = physicsSystem.getPhysicsSettings();
        settings.setNumPositionSteps(ModConfig.NUM_ITERATIONS.get());
        settings.setNumVelocitySteps(ModConfig.NUM_ITERATIONS.get());
        settings.setBaumgarte(ModConfig.ERP.get().floatValue());
        physicsSystem.setGravity(0f, -9.81f, 0f);
    }

    public void processCommandQueue() {
        ICommand command;
        while (physicsSystem != null && isRunning && (command = commandQueue.poll()) != null) {
            try {
                command.execute(this);
            } catch (Exception e) {
                XBullet.LOGGER.error("PhysicsThread: Exception executing command", e);
            }
        }
    }

    public void queueCommand(ICommand command) {
        if (command == null || !this.isRunning) {

            if (command != null) {
                XBullet.LOGGER.warn("Attempted to queue command {} to a non-running physics world for dimension {}.", command.getClass().getSimpleName(), dimensionKey.location());
            }
            return;
        }
        commandQueue.offer(command);
    }

    public void execute(Runnable task) {
        queueCommand(new RunTaskCommand(task));
    }

    public void stop() {
        if (!this.isRunning && (this.physicsThreadExecutor == null || !this.physicsThreadExecutor.isAlive())) {
            return;
        }

        XBullet.LOGGER.info("Stopping physics world for dimension: {}", dimensionKey.location());
        this.isRunning = false;
        if (this.physicsThreadExecutor != null) {
            this.physicsThreadExecutor.interrupt();
            try {
                this.physicsThreadExecutor.join(5000);
            } catch (InterruptedException e) {
                XBullet.LOGGER.warn("Interrupted while waiting for physics thread {} to stop.", this.physicsThreadExecutor.getName());
                Thread.currentThread().interrupt();
            }
        }
        this.physicsThreadExecutor = null;

    }

    private void cleanupInternal() {
        XBullet.LOGGER.info("Cleaning up physics resources for dimension: {}", dimensionKey.location());
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

        clearAllMaps();
    }

    private void clearAllMaps() {
        bodyIds.clear();
        bodyIdToUuidMap.clear();
        physicsObjectsMap.clear();
        syncedTransforms.clear();
        syncedLinearVelocities.clear();
        syncedAngularVelocities.clear();
        syncedActiveStates.clear();
        syncedSoftBodyVertexData.clear();
        syncedStateTimestampsNanos.clear();
        commandQueue.clear();
        physicsJointsMap.clear();
    }

    public Map<UUID, IPhysicsObject> getPhysicsObjectsMap() {
        return physicsObjectsMap;
    }

    public Optional<IPhysicsObject> findPhysicsObjectByBodyId(int bodyId) {
        UUID objectId = this.bodyIdToUuidMap.get(bodyId);
        if (objectId != null) {
            return Optional.ofNullable(this.physicsObjectsMap.get(objectId));
        }
        return Optional.empty();
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

    public boolean isRunning() {
        return this.isRunning && this.physicsThreadExecutor != null && this.physicsThreadExecutor.isAlive();
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

    public ResourceKey<Level> getDimensionKey() {
        return dimensionKey;
    }

    public float getFixedTimeStep() {
        return this.fixedTimeStep;
    }

    public Map<UUID, PhysicsTransform> getSyncedTransforms() {
        return syncedTransforms;
    }

    @Nullable
    public PhysicsTransform getTransform(UUID id) {
        return syncedTransforms.get(id);
    }

    public Map<UUID, Vec3> getSyncedLinearVelocities() {
        return syncedLinearVelocities;
    }

    @Nullable
    public Vec3 getLinearVelocity(UUID id) {
        return syncedLinearVelocities.get(id);
    }

    public Map<UUID, Vec3> getSyncedAngularVelocities() {
        return syncedAngularVelocities;
    }

    @Nullable
    public Vec3 getAngularVelocity(UUID id) {
        return syncedAngularVelocities.get(id);
    }

    public Map<UUID, Boolean> getSyncedActiveStates() {
        return syncedActiveStates;
    }

    @Nullable
    public Boolean isActive(UUID id) {
        return syncedActiveStates.get(id);
    }

    public Map<UUID, float[]> getSyncedSoftBodyVertexData() {
        return syncedSoftBodyVertexData;
    }

    @Nullable
    public float[] getSoftBodyVertexData(UUID id) {
        return syncedSoftBodyVertexData.get(id);
    }

    public Map<UUID, Long> getSyncedStateTimestampsNanos() {
        return syncedStateTimestampsNanos;
    }

    public Map<UUID, Integer> getBodyIds() {
        return bodyIds;
    }

    public Map<Integer, UUID> getBodyIdToUuidMap() {
        return bodyIdToUuidMap;
    }

    public Map<UUID, Constraint> getPhysicsJointsMap() {
        return physicsJointsMap;
    }

    public long getAccumulatedPauseTimeNanos() {
        if (isPaused && pauseStartTimeNanos > 0L) {
            return accumulatedPauseTimeNanos + (System.nanoTime() - pauseStartTimeNanos);
        }
        return accumulatedPauseTimeNanos;
    }
}