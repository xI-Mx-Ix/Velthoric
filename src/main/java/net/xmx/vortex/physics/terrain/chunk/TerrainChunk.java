package net.xmx.vortex.physics.terrain.chunk;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.minecraft.server.level.ServerLevel;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.physics.terrain.TerrainSystem;
import net.xmx.vortex.physics.terrain.job.VxTaskPriority;
import net.xmx.vortex.physics.terrain.job.VxTerrainJobSystem;
import net.xmx.vortex.physics.terrain.loader.ChunkSnapshot;
import net.xmx.vortex.physics.terrain.loader.TerrainGenerator;
import net.xmx.vortex.physics.terrain.model.VxSectionPos;
import net.xmx.vortex.physics.world.VxPhysicsWorld;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class TerrainChunk {

    public enum State {
        UNLOADED,
        LOADING_SCHEDULED,
        LOADING_SHAPE_GENERATION,
        READY_INACTIVE,
        READY_ACTIVE,
        REMOVING,
        REMOVED
    }

    private final VxSectionPos pos;
    private final ServerLevel level;
    private final VxPhysicsWorld physicsWorld;
    private final TerrainGenerator terrainGenerator;
    private final TerrainSystem terrainSystem;

    private volatile int bodyId = -1;
    private final AtomicReference<State> state = new AtomicReference<>(State.UNLOADED);
    private final AtomicInteger rebuildVersion = new AtomicInteger(0);
    private volatile ShapeRefC currentShapeRef = null;
    private volatile boolean isPlaceholderShape = true;

    private volatile State previousStateBeforeLoading = State.UNLOADED;

    public TerrainChunk(VxSectionPos pos, ServerLevel level, VxPhysicsWorld physicsWorld, TerrainGenerator terrainGenerator, TerrainSystem terrainSystem) {
        this.pos = pos;
        this.level = level;
        this.physicsWorld = physicsWorld;
        this.terrainGenerator = terrainGenerator;
        this.terrainSystem = terrainSystem;
    }

    public void scheduleInitialBuild() {
        if (state.get() == State.UNLOADED) {
            final int version = rebuildVersion.incrementAndGet();
            this.isPlaceholderShape = true;
            this.previousStateBeforeLoading = state.getAndSet(State.LOADING_SCHEDULED);
            terrainSystem.requestSnapshot(pos, version, true, VxTaskPriority.CRITICAL);
        }
    }

    public void scheduleRebuild(VxTaskPriority priority) {
        State currentState = state.get();

        if (currentState == State.REMOVING || currentState == State.REMOVED || currentState == State.LOADING_SCHEDULED || currentState == State.LOADING_SHAPE_GENERATION) {
            return;
        }

        final int version = rebuildVersion.incrementAndGet();
        this.previousStateBeforeLoading = state.getAndSet(State.LOADING_SCHEDULED);
        terrainSystem.requestSnapshot(pos, version, false, priority);
    }

    public void resetStateAfterFailedSnapshot() {

        state.compareAndSet(State.LOADING_SCHEDULED, previousStateBeforeLoading);
    }

    public void processSnapshot(ChunkSnapshot snapshot, int snapshotVersion, boolean isInitialBuild) {

        if (snapshotVersion < rebuildVersion.get()) {
            return;
        }

        State oldState = state.getAndSet(State.LOADING_SHAPE_GENERATION);
        if (oldState != State.LOADING_SCHEDULED) {

            state.set(oldState);
            return;
        }

        final boolean wasActive = (previousStateBeforeLoading == State.READY_ACTIVE);

        CompletableFuture.supplyAsync(() -> {

                    if (isInitialBuild) return terrainGenerator.generatePlaceholderShape(snapshot);
                    else return terrainGenerator.generateShape(level, snapshot);
                }, VxTerrainJobSystem.getInstance().getExecutor())
                .thenAcceptAsync(generatedShape -> {

                    if (snapshotVersion < rebuildVersion.get() || state.get() == State.REMOVING || state.get() == State.REMOVED) {
                        if (generatedShape != null) generatedShape.close();

                        if (state.get() != State.REMOVING && state.get() != State.REMOVED) {
                            state.set(previousStateBeforeLoading);
                        }
                        return;
                    }

                    BodyInterface bodyInterface = physicsWorld.getBodyInterface();
                    if (bodyInterface == null) {
                        if (generatedShape != null) generatedShape.close();
                        state.set(State.REMOVED);
                        return;
                    }

                    if (generatedShape == null) {

                        if (this.bodyId != -1) {
                            closeBodyAndShape(bodyInterface);
                        }

                        state.set(previousStateBeforeLoading);
                        return;
                    }

                    if (this.bodyId != -1) {
                        final ShapeRefC oldShape = this.currentShapeRef;
                        bodyInterface.setShape(this.bodyId, generatedShape, true, EActivation.Activate);
                        this.currentShapeRef = generatedShape;

                        if (oldShape != null && !oldShape.equals(generatedShape)) oldShape.close();
                    } else {
                        try (BodyCreationSettings bcs = new BodyCreationSettings(
                                generatedShape,
                                new RVec3(pos.getOrigin().getX(), pos.getOrigin().getY(), pos.getOrigin().getZ()),
                                Quat.sIdentity(),
                                EMotionType.Static,
                                VxPhysicsWorld.Layers.STATIC)) {
                            Body body = bodyInterface.createBody(bcs);
                            if (body != null) {
                                this.bodyId = body.getId();
                                this.currentShapeRef = generatedShape;
                                body.close();
                            } else {
                                VxMainClass.LOGGER.error("Failed to create terrain body for chunk {}", pos);
                                generatedShape.close();
                            }
                        }
                    }

                    this.isPlaceholderShape = isInitialBuild;

                    state.set(wasActive ? State.READY_ACTIVE : State.READY_INACTIVE);

                    if (wasActive) {

                        activate();
                    }

                }, physicsWorld)
                .exceptionally(ex -> {

                    VxMainClass.LOGGER.error("Exception during terrain shape generation for {}", pos, ex);

                    state.set(previousStateBeforeLoading);
                    return null;
                });
    }

    public void activate() {
        State currentState = state.get();

        if ((currentState == State.READY_ACTIVE || currentState == State.READY_INACTIVE) && this.bodyId == -1) {
            VxMainClass.LOGGER.warn("Detected stuck terrain chunk {} in ready state with no body. Forcing high-prio rebuild.", pos);
            scheduleRebuild(VxTaskPriority.HIGH);
            return;
        }

        if (bodyId != -1 && currentState == State.READY_INACTIVE) {
            if (state.compareAndSet(State.READY_INACTIVE, State.READY_ACTIVE)) {
                physicsWorld.execute(() -> {
                    BodyInterface bodyInterface = physicsWorld.getBodyInterface();
                    if (bodyInterface != null && bodyId != -1 && !bodyInterface.isAdded(bodyId)) {
                        bodyInterface.addBody(bodyId, EActivation.Activate);
                    }
                });
            }
        }

        if (isPlaceholderShape && bodyId != -1) {
            scheduleRebuild(VxTaskPriority.CRITICAL);
        }
    }

    public void deactivate() {
        if (bodyId != -1 && state.compareAndSet(State.READY_ACTIVE, State.READY_INACTIVE)) {
            physicsWorld.execute(() -> {
                BodyInterface bodyInterface = physicsWorld.getBodyInterface();
                if (bodyInterface != null && bodyInterface.isAdded(bodyId)) {
                    bodyInterface.removeBody(bodyId);
                }
            });
        }
    }

    public void scheduleRemoval() {
        State oldState = state.getAndSet(State.REMOVING);
        if (oldState == State.REMOVING || oldState == State.REMOVED) return;

        rebuildVersion.incrementAndGet();

        physicsWorld.execute(this::closeResources);
    }

    private void closeBodyAndShape(BodyInterface bodyInterface) {
        if (bodyId != -1) {
            if (bodyInterface.isAdded(bodyId)) {
                bodyInterface.removeBody(bodyId);
            }
            bodyInterface.destroyBody(bodyId);
            bodyId = -1;
        }
        if (currentShapeRef != null) {
            currentShapeRef.close();
            currentShapeRef = null;
        }
    }

    private void closeResources() {
        BodyInterface bodyInterface = physicsWorld.getBodyInterface();
        if (bodyInterface != null) {
            closeBodyAndShape(bodyInterface);
        }
        state.set(State.REMOVED);
    }

    public boolean isReady() {
        State s = state.get();
        return s == State.READY_ACTIVE || s == State.READY_INACTIVE;
    }

    public boolean isPlaceholder() {
        return this.isPlaceholderShape;
    }

    public int getBodyId() {
        return bodyId;
    }
}