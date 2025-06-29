package net.xmx.xbullet.physics.physicsworld;

import com.github.stephengold.joltjni.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;
import net.xmx.xbullet.init.ModConfig;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.natives.NativeJoltInitializer;
import net.xmx.xbullet.network.NetworkHandler;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.physicsworld.pcmd.ICommand;
import net.xmx.xbullet.physics.physicsworld.pcmd.RunTaskCommand;
import net.xmx.xbullet.physics.physicsworld.pcmd.UpdatePhysicsStateCommand;

import javax.annotation.Nullable;
import java.util.*;
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
    private float fixedTimeStep;
    private float timeAccumulator = 0.0f;

    private volatile boolean isShutdown = false;

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
    private volatile boolean shouldRun = false;
    private volatile boolean isPaused = false;
    private static final int DEFAULT_SIMULATION_HZ = 60;

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
        if (physicsThreadExecutor != null && physicsThreadExecutor.isAlive()) {
            return;
        }
        this.shouldRun = true;
        physicsThreadExecutor = new Thread(this, "XBullet-Jolt-Physics-" + dimensionKey.location().getPath());
        physicsThreadExecutor.setDaemon(true);
        physicsThreadExecutor.setPriority(Thread.NORM_PRIORITY);
        physicsThreadExecutor.start();
    }

    @Override
    public void run() {
        try {
            NativeJoltInitializer.initialize();
            initializePhysicsSystem();
        } catch (Throwable t) {
            XBullet.LOGGER.error("FATAL: Failed to initialize Jolt physics for dimension {}. The physics thread will not run.", dimensionKey.location(), t);
            this.shouldRun = false;
            cleanupInternal();
            return;
        }

        long lastLoopTimeNanos = System.nanoTime();

        while (this.shouldRun) {
            try {
                long currentTimeNanos = System.nanoTime();
                long elapsedNanos = currentTimeNanos - lastLoopTimeNanos;
                lastLoopTimeNanos = currentTimeNanos;

                float deltaTimeSeconds = Math.min(elapsedNanos / 1_000_000_000.0f, 0.1f);

                this.update(deltaTimeSeconds);

                long loopEndTimeNanos = System.nanoTime();
                long actualLoopDurationNanos = loopEndTimeNanos - currentTimeNanos;
                long sleepTimeNanos = (long)(fixedTimeStep * 1_000_000_000.0) - actualLoopDurationNanos;

                if (sleepTimeNanos > 0) {

                    Thread.sleep(sleepTimeNanos / 1_000_000L, (int) (sleepTimeNanos % 1_000_000L));
                }
            } catch (InterruptedException e) {
                XBullet.LOGGER.info("Physics thread for dimension {} was interrupted. Shutting down.", dimensionKey.location());
                this.shouldRun = false;
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                XBullet.LOGGER.error("An uncaught exception occurred in the main physics loop for dimension {}. The simulation might be unstable.", dimensionKey.location(), t);

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    this.shouldRun = false;
                    Thread.currentThread().interrupt();
                }
            }
        }
        cleanupInternal();
    }

    public void update(float deltaTime) {
        if (this.isPaused || !this.shouldRun || physicsSystem == null) {
            if (isPaused && pauseStartTimeNanos == 0L) {
                pauseStartTimeNanos = System.nanoTime();
            }
            return;
        }

        if (pauseStartTimeNanos != 0L) {
            accumulatedPauseTimeNanos += System.nanoTime() - pauseStartTimeNanos;
            pauseStartTimeNanos = 0L;
        }

        processCommandQueue();

        timeAccumulator += deltaTime;
        final int maxSubSteps = ModConfig.MAX_SUBSTEPS.get();
        int substepsPerformed = 0;

        while (timeAccumulator >= this.fixedTimeStep && substepsPerformed < maxSubSteps) {
            try {
                physicsSystem.update(this.fixedTimeStep, 1, tempAllocator, jobSystem);
            } catch (Exception e) {
                XBullet.LOGGER.error("PhysicsThread: Exception during one physics sub-step.", e);
            }
            timeAccumulator -= this.fixedTimeStep;
            substepsPerformed++;
        }

        new UpdatePhysicsStateCommand(System.nanoTime()).execute(this);
    }

    private void initializePhysicsSystem() {
        tempAllocator = new TempAllocatorImpl(10 * 1024 * 1024);
        int numThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        jobSystem = new JobSystemThreadPool(Jolt.cMaxPhysicsJobs, Jolt.cMaxPhysicsBarriers, numThreads);

        physicsSystem = new PhysicsSystem();
        final int maxBodies = 65536;
        final int numBodyMutexes = 0;
        final int maxBodyPairs = 65536;
        final int maxContactConstraints = 10240;
        physicsSystem.init(maxBodies, numBodyMutexes, maxBodyPairs, maxContactConstraints,
                NativeJoltInitializer.getBroadPhaseLayerInterface(),
                NativeJoltInitializer.getObjectVsBroadPhaseLayerFilter(),
                NativeJoltInitializer.getObjectLayerPairFilter());

        PhysicsSettings settings = physicsSystem.getPhysicsSettings();
        settings.setNumPositionSteps(ModConfig.NUM_ITERATIONS.get());
        settings.setNumVelocitySteps(ModConfig.NUM_ITERATIONS.get());
        settings.setBaumgarte(ModConfig.ERP.get().floatValue());
        physicsSystem.setGravity(0f, -9.81f, 0f);
    }

    public void processCommandQueue() {
        ICommand command;
        while (physicsSystem != null && shouldRun && (command = commandQueue.poll()) != null) {
            try {
                command.execute(this);
            } catch (Exception e) {
                XBullet.LOGGER.error("PhysicsThread: Exception executing command", e);
            }
        }
    }

    public void queueCommand(ICommand command) {
        if (command == null || physicsThreadExecutor == null || !physicsThreadExecutor.isAlive() || !this.shouldRun) {
            return;
        }
        commandQueue.offer(command);
    }

    public void execute(Runnable task) {
        queueCommand(new RunTaskCommand(task));
    }

    public void stop() {
        if (isShutdown) {
            return;
        }
        isShutdown = true;

        this.shouldRun = false;
        if (physicsThreadExecutor != null) {
            physicsThreadExecutor.interrupt();
            try {
                physicsThreadExecutor.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void cleanupInternal() {
        if (physicsSystem != null) {
            physicsSystem.close();
            physicsSystem = null;
        }

        if (jobSystem != null) jobSystem.close();
        if (tempAllocator != null) tempAllocator.close();
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

    public Map<UUID, IPhysicsObject> getPhysicsObjectsMap() { return physicsObjectsMap; }

    public Optional<IPhysicsObject> findPhysicsObjectByBodyId(int bodyId) {
        UUID objectId = this.bodyIdToUuidMap.get(bodyId);
        if (objectId != null) {
            return Optional.ofNullable(this.physicsObjectsMap.get(objectId));
        }
        return Optional.empty();
    }

    @Nullable public PhysicsSystem getPhysicsSystem() {
        return physicsSystem;
    }

    @Nullable public BodyInterface getBodyInterface() {
        return physicsSystem != null ? physicsSystem.getBodyInterface() : null;
    }

    @Nullable public BodyLockInterface getBodyLockInterface() {
        return physicsSystem != null ? physicsSystem.getBodyLockInterface() : null;
    }

    public boolean isRunning() {
        return !isShutdown && shouldRun && physicsThreadExecutor != null && physicsThreadExecutor.isAlive();
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

    @Nullable public PhysicsTransform getTransform(UUID id) {
        return syncedTransforms.get(id);
    }

    public Map<UUID, Vec3> getSyncedLinearVelocities() {
        return syncedLinearVelocities;
    }

    @Nullable public Vec3 getLinearVelocity(UUID id) {
        return syncedLinearVelocities.get(id);
    }

    public Map<UUID, Vec3> getSyncedAngularVelocities() {
        return syncedAngularVelocities;
    }

    @Nullable public Vec3 getAngularVelocity(UUID id) {
        return syncedAngularVelocities.get(id);
    }

    public Map<UUID, Boolean> getSyncedActiveStates() {
        return syncedActiveStates;
    }

    @Nullable public Boolean isActive(UUID id) {
        return syncedActiveStates.get(id);
    }

    public Map<UUID, float[]> getSyncedSoftBodyVertexData() {
        return syncedSoftBodyVertexData;
    }

    @Nullable public float[] getSoftBodyVertexData(UUID id) {
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