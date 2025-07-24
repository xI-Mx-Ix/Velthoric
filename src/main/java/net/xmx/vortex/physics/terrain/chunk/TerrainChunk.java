package net.xmx.vortex.physics.terrain.chunk;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.minecraft.server.level.ServerLevel;
import net.xmx.vortex.init.VxMainClass;
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
        LOADING,
        READY_INACTIVE,
        READY_ACTIVE,
        REMOVING,
        REMOVED
    }

    private final VxSectionPos pos;
    private final ServerLevel level;
    private final VxPhysicsWorld physicsWorld;
    private final TerrainGenerator terrainGenerator;

    private volatile int bodyId = -1;
    private final AtomicReference<State> state = new AtomicReference<>(State.UNLOADED);
    private final AtomicInteger rebuildVersion = new AtomicInteger(0);
    private volatile ShapeRefC currentShapeRef = null;
    private volatile boolean isPlaceholderShape = true;

    public TerrainChunk(VxSectionPos pos, ServerLevel level, VxPhysicsWorld physicsWorld, TerrainGenerator terrainGenerator) {
        this.pos = pos;
        this.level = level;
        this.physicsWorld = physicsWorld;
        this.terrainGenerator = terrainGenerator;
    }

    public void scheduleInitialBuild() {
        if (state.compareAndSet(State.UNLOADED, State.LOADING)) {
            final int version = rebuildVersion.incrementAndGet();

            CompletableFuture.supplyAsync(() -> ChunkSnapshot.snapshot(level, pos), VxTerrainJobSystem.getInstance().getExecutor())
                    .thenCompose(snapshot -> {
                        if (version != rebuildVersion.get() || state.get() == State.REMOVING)
                            return CompletableFuture.completedFuture(null);
                        if (snapshot.shapes().isEmpty()) {
                            physicsWorld.execute(() -> state.set(State.READY_INACTIVE)); // Empty but "ready"
                            return CompletableFuture.completedFuture(null);
                        }
                        return CompletableFuture.supplyAsync(() -> terrainGenerator.generatePlaceholderShape(snapshot), VxTerrainJobSystem.getInstance().getExecutor());
                    })
                    .thenAcceptAsync(placeholderShape -> {
                        if (version != rebuildVersion.get() || state.get() == State.REMOVING) {
                            if (placeholderShape != null) placeholderShape.close();
                            return;
                        }

                        if (placeholderShape != null) {
                            try (BodyCreationSettings bcs = new BodyCreationSettings(
                                    placeholderShape,
                                    new RVec3(pos.getOrigin().getX(), pos.getOrigin().getY(), pos.getOrigin().getZ()),
                                    Quat.sIdentity(),
                                    EMotionType.Static,
                                    VxPhysicsWorld.Layers.STATIC
                            )) {
                                Body body = physicsWorld.getBodyInterface().createBody(bcs);
                                if (body != null) {
                                    this.bodyId = body.getId();
                                    this.currentShapeRef = placeholderShape;
                                    this.isPlaceholderShape = true;
                                    state.set(State.READY_INACTIVE);
                                    body.close();
                                } else {
                                    VxMainClass.LOGGER.error("Failed to create placeholder terrain body for chunk {}", pos);
                                    placeholderShape.close();
                                    state.set(State.UNLOADED);
                                }
                            }
                        } else {
                            state.set(State.READY_INACTIVE);
                        }
                    }, physicsWorld)
                    .exceptionally(ex -> {
                        VxMainClass.LOGGER.error("Failed during terrain initial build for {}", pos, ex);
                        state.set(State.UNLOADED);
                        return null;
                    });
        }
    }

    public void scheduleRebuild() {
        scheduleDetailedBuild(true);
    }

    private void scheduleDetailedBuild(boolean isForcedRebuild) {
        if (!isForcedRebuild && !isPlaceholderShape) {
            return;
        }

        State currentState = state.get();
        if (currentState == State.REMOVING || currentState == State.REMOVED || currentState == State.LOADING) {
            return;
        }

        if (state.compareAndSet(State.READY_ACTIVE, State.LOADING) || state.compareAndSet(State.READY_INACTIVE, State.LOADING)) {
            final int version = rebuildVersion.incrementAndGet();
            final boolean wasActive = (currentState == State.READY_ACTIVE);

            CompletableFuture.supplyAsync(() -> ChunkSnapshot.snapshot(level, pos), VxTerrainJobSystem.getInstance().getExecutor())
                    .thenCompose(snapshot -> {
                        if (version != rebuildVersion.get() || state.get() == State.REMOVING)
                            return CompletableFuture.completedFuture(null);
                        if (snapshot.shapes().isEmpty()) {
                            return CompletableFuture.completedFuture(null);
                        }
                        return CompletableFuture.supplyAsync(() -> terrainGenerator.generateShape(level, snapshot), VxTerrainJobSystem.getInstance().getExecutor());
                    })
                    .thenAcceptAsync(detailedShape -> {
                        if (version != rebuildVersion.get() || state.get() == State.REMOVING) {
                            if (detailedShape != null) detailedShape.close();
                            return;
                        }

                        BodyInterface bodyInterface = physicsWorld.getBodyInterface();
                        if (detailedShape == null) {
                            if (this.bodyId != -1) {
                                bodyInterface.removeBody(this.bodyId);
                                bodyInterface.destroyBody(this.bodyId);
                                this.bodyId = -1;
                                if (this.currentShapeRef != null) {
                                    this.currentShapeRef.close();
                                    this.currentShapeRef = null;
                                }
                            }
                            this.isPlaceholderShape = true;
                            state.set(State.READY_INACTIVE);
                            return;
                        }

                        if (this.bodyId != -1) {
                            final ShapeRefC oldShape = this.currentShapeRef;
                            bodyInterface.setShape(this.bodyId, detailedShape, true, EActivation.Activate);
                            this.currentShapeRef = detailedShape;
                            this.isPlaceholderShape = false;
                            if (oldShape != null && oldShape != detailedShape) {
                                oldShape.close();
                            }
                        } else {
                            try (BodyCreationSettings bcs = new BodyCreationSettings(
                                    detailedShape,
                                    new RVec3(pos.getOrigin().getX(), pos.getOrigin().getY(), pos.getOrigin().getZ()),
                                    Quat.sIdentity(),
                                    EMotionType.Static,
                                    VxPhysicsWorld.Layers.STATIC
                            )) {
                                Body body = bodyInterface.createBody(bcs);
                                if (body != null) {
                                    this.bodyId = body.getId();
                                    this.currentShapeRef = detailedShape;
                                    this.isPlaceholderShape = false;
                                    body.close();
                                } else {
                                    VxMainClass.LOGGER.error("Failed to create detailed terrain body for chunk {}", pos);
                                    detailedShape.close();
                                    state.set(wasActive ? State.READY_ACTIVE : State.READY_INACTIVE);
                                    return;
                                }
                            }
                        }

                        if (wasActive) {
                            if (!bodyInterface.isAdded(bodyId)) {
                                bodyInterface.addBody(bodyId, EActivation.Activate);
                            }
                            state.set(State.READY_ACTIVE);
                        } else {
                            state.set(State.READY_INACTIVE);
                        }

                    }, physicsWorld)
                    .exceptionally(ex -> {
                        VxMainClass.LOGGER.error("Failed during terrain detailed build for {}", pos, ex);
                        state.set(wasActive ? State.READY_ACTIVE : State.READY_INACTIVE);
                        return null;
                    });
        }
    }


    public void activate() {
        if (bodyId != -1 && state.compareAndSet(State.READY_INACTIVE, State.READY_ACTIVE)) {
            physicsWorld.execute(() -> {
                BodyInterface bodyInterface = physicsWorld.getBodyInterface();
                if (bodyInterface != null && bodyId != -1 && !bodyInterface.isAdded(bodyId)) {
                    bodyInterface.addBody(bodyId, EActivation.Activate);
                }
            });
        }

        if (isPlaceholderShape && bodyId != -1) {
            scheduleDetailedBuild(false);
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

    private void closeResources() {
        if (bodyId != -1) {
            BodyInterface bodyInterface = physicsWorld.getBodyInterface();
            if (bodyInterface != null) {
                if (bodyInterface.isAdded(bodyId)) {
                    bodyInterface.removeBody(bodyId);
                }
                bodyInterface.destroyBody(bodyId);
            }
            bodyId = -1;
        }
        if (currentShapeRef != null) {
            currentShapeRef.close();
            currentShapeRef = null;
        }
        state.set(State.REMOVED);
    }

    public State getState() {
        return state.get();
    }

    public boolean isReady() {
        State s = state.get();
        return s == State.READY_ACTIVE || s == State.READY_INACTIVE;
    }

    public int getBodyId() {
        return bodyId;
    }

    public VxSectionPos getPos() {
        return pos;
    }
}