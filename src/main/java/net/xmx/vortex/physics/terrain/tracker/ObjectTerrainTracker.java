package net.xmx.vortex.physics.terrain.tracker;

import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
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

    private static final int PRELOAD_RADIUS = 5;
    private static final int ACTIVATION_RADIUS = 2;
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

        RVec3 pos = physicsObject.getBody().getPosition();
        Vec3 vel = physicsObject.getBody().getLinearVelocity();

        Map<VxSectionPos, VxTaskPriority> requiredChunks = calculateAndPrioritizeChunks(pos, vel);
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

    private Map<VxSectionPos, VxTaskPriority> calculateAndPrioritizeChunks(RVec3 position, Vec3 velocity) {
        Map<VxSectionPos, VxTaskPriority> needed = new HashMap<>();

        double currentX = position.xx();
        double currentY = position.yy();
        double currentZ = position.zz();

        double predictedX = currentX + velocity.getX() * PREDICTION_SECONDS;
        double predictedY = currentY + velocity.getY() * PREDICTION_SECONDS;
        double predictedZ = currentZ + velocity.getZ() * PREDICTION_SECONDS;

        addChunksWithPriority(needed, currentX, currentY, currentZ, PRELOAD_RADIUS, VxTaskPriority.MEDIUM);
        addChunksWithPriority(needed, predictedX, predictedY, predictedZ, PRELOAD_RADIUS, VxTaskPriority.MEDIUM);

        addChunksWithPriority(needed, currentX, currentY, currentZ, ACTIVATION_RADIUS, VxTaskPriority.CRITICAL);
        addChunksWithPriority(needed, predictedX, predictedY, predictedZ, ACTIVATION_RADIUS, VxTaskPriority.CRITICAL);

        return needed;
    }

    private void addChunksWithPriority(Map<VxSectionPos, VxTaskPriority> needed, double centerX, double centerY, double centerZ, int radius, VxTaskPriority priority) {
        VxSectionPos centerVPos = VxSectionPos.fromWorldSpace(centerX, centerY, centerZ);
        for (int y = -radius; y <= radius; ++y) {
            for (int z = -radius; z <= radius; ++z) {
                for (int x = -radius; x <= radius; ++x) {
                    VxSectionPos pos = new VxSectionPos(centerVPos.x() + x, centerVPos.y() + y, centerVPos.z() + z);
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