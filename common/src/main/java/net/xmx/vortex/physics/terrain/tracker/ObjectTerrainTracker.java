package net.xmx.vortex.physics.terrain.tracker;

import com.github.stephengold.joltjni.AaBox;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.operator.Op;
import com.github.stephengold.joltjni.readonly.ConstAaBox;
import net.xmx.vortex.physics.object.physicsobject.IPhysicsObject;
import net.xmx.vortex.physics.terrain.TerrainSystem;
import net.xmx.vortex.physics.terrain.job.VxTaskPriority;
import net.xmx.vortex.physics.terrain.model.VxSectionPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ObjectTerrainTracker {
    private final IPhysicsObject physicsObject;
    private final TerrainSystem terrainSystem;
    private final Set<VxSectionPos> currentPreloadedChunks = new HashSet<>();

    private static final int PRELOAD_RADIUS_CHUNKS = 5;
    private static final int ACTIVATION_RADIUS_CHUNKS = 2;
    private static final float PREDICTION_SECONDS = 0.5f;

    public ObjectTerrainTracker(IPhysicsObject physicsObject, TerrainSystem terrainSystem) {
        this.physicsObject = physicsObject;
        this.terrainSystem = terrainSystem;
    }

    public Set<VxSectionPos> update() {
        if (physicsObject.getBody() == null || !physicsObject.isPhysicsInitialized()) {
            releaseAll();
            return new HashSet<>();
        }

        ConstAaBox aabb = physicsObject.getBody().getWorldSpaceBounds();
        Vec3 vel = physicsObject.getBody().getLinearVelocity();

        Map<VxSectionPos, VxTaskPriority> requiredChunks = calculateAndPrioritizeChunks(aabb, vel);
        Set<VxSectionPos> newPreloadSet = requiredChunks.keySet();

        Set<VxSectionPos> toRelease = new HashSet<>(currentPreloadedChunks);
        toRelease.removeAll(newPreloadSet);

        Set<VxSectionPos> toRequest = new HashSet<>(newPreloadSet);
        toRequest.removeAll(currentPreloadedChunks);

        toRelease.forEach(terrainSystem::releaseChunk);
        toRequest.forEach(terrainSystem::requestChunk);

        requiredChunks.forEach(terrainSystem::prioritizeChunk);

        currentPreloadedChunks.clear();
        currentPreloadedChunks.addAll(newPreloadSet);

        return requiredChunks.entrySet().stream()
                .filter(e -> e.getValue() == VxTaskPriority.CRITICAL)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private Map<VxSectionPos, VxTaskPriority> calculateAndPrioritizeChunks(ConstAaBox currentAabb, Vec3 velocity) {
        Map<VxSectionPos, VxTaskPriority> needed = new HashMap<>();

        addChunksForAabb(needed, currentAabb, PRELOAD_RADIUS_CHUNKS, VxTaskPriority.MEDIUM);
        addChunksForAabb(needed, currentAabb, ACTIVATION_RADIUS_CHUNKS, VxTaskPriority.CRITICAL);

        Vec3 displacement = Op.star(velocity, PREDICTION_SECONDS);

        RVec3 predictedMin = Op.plus(new RVec3(currentAabb.getMin()), displacement);
        RVec3 predictedMax = Op.plus(new RVec3(currentAabb.getMax()), displacement);

        try (AaBox predictedAabb = new AaBox(predictedMin, predictedMax)) {
            addChunksForAabb(needed, predictedAabb, PRELOAD_RADIUS_CHUNKS, VxTaskPriority.MEDIUM);
            addChunksForAabb(needed, predictedAabb, ACTIVATION_RADIUS_CHUNKS, VxTaskPriority.CRITICAL);
        }

        return needed;
    }

    private void addChunksForAabb(Map<VxSectionPos, VxTaskPriority> needed, ConstAaBox aabb, int radiusInChunks, VxTaskPriority priority) {

        RVec3 min = new RVec3(aabb.getMin());
        RVec3 max = new RVec3(aabb.getMax());

        VxSectionPos minSection = VxSectionPos.fromWorldSpace(min.xx(), min.yy(), min.zz());
        VxSectionPos maxSection = VxSectionPos.fromWorldSpace(max.xx(), max.yy(), max.zz());

        for (int y = minSection.y() - radiusInChunks; y <= maxSection.y() + radiusInChunks; ++y) {
            for (int z = minSection.z() - radiusInChunks; z <= maxSection.z() + radiusInChunks; ++z) {
                for (int x = minSection.x() - radiusInChunks; x <= maxSection.x() + radiusInChunks; ++x) {
                    VxSectionPos pos = new VxSectionPos(x, y, z);
                    if (pos.isWithinWorldHeight(terrainSystem.getLevel())) {

                        needed.merge(pos, priority, (oldP, newP) -> newP.ordinal() > oldP.ordinal() ? newP : oldP);
                    }
                }
            }
        }
    }

    public void releaseAll() {
        currentPreloadedChunks.forEach(terrainSystem::releaseChunk);
        currentPreloadedChunks.clear();
    }
}