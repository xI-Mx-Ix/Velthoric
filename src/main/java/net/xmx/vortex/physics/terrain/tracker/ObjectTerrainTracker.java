package net.xmx.vortex.physics.terrain.tracker;

import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import net.xmx.vortex.physics.object.physicsobject.IPhysicsObject;
import net.xmx.vortex.physics.terrain.TerrainSystem;
import net.xmx.vortex.physics.terrain.model.VxSectionPos;

import java.util.HashSet;
import java.util.Set;

public class ObjectTerrainTracker {

    private final IPhysicsObject physicsObject;
    private final TerrainSystem terrainSystem;
    private final Set<VxSectionPos> currentPreloadedChunks = new HashSet<>();

    private static final int ACTIVATION_RADIUS = 2;
    private static final int PRELOAD_RADIUS = 5;
    private static final float PREDICTION_SECONDS = 0.5f;

    public ObjectTerrainTracker(IPhysicsObject physicsObject, TerrainSystem terrainSystem) {
        this.physicsObject = physicsObject;
        this.terrainSystem = terrainSystem;
    }

    public Set<VxSectionPos> update() {
        if (physicsObject.getBody() == null) {
            releaseAll();
            return new HashSet<>();
        }

        RVec3 pos = physicsObject.getBody().getPosition();
        Vec3 vel = physicsObject.getBody().getLinearVelocity();

        Set<VxSectionPos> newPreloadChunks = calculateRequiredChunks(pos, vel, PRELOAD_RADIUS);
        Set<VxSectionPos> newActivationChunks = calculateRequiredChunks(pos, vel, ACTIVATION_RADIUS);

        Set<VxSectionPos> toRelease = new HashSet<>(currentPreloadedChunks);
        toRelease.removeAll(newPreloadChunks);

        Set<VxSectionPos> toRequest = new HashSet<>(newPreloadChunks);
        toRequest.removeAll(currentPreloadedChunks);

        toRelease.forEach(terrainSystem::releaseChunk);
        toRequest.forEach(terrainSystem::requestChunk);

        currentPreloadedChunks.clear();
        currentPreloadedChunks.addAll(newPreloadChunks);

        return newActivationChunks;
    }

    private Set<VxSectionPos> calculateRequiredChunks(RVec3 position, Vec3 velocity, int radius) {
        Set<VxSectionPos> needed = new HashSet<>();

        double currentX = position.xx();
        double currentY = position.yy();
        double currentZ = position.zz();

        double predictedX = currentX + velocity.getX() * PREDICTION_SECONDS;
        double predictedY = currentY + velocity.getY() * PREDICTION_SECONDS;
        double predictedZ = currentZ + velocity.getZ() * PREDICTION_SECONDS;

        addChunksInRadius(needed, currentX, currentY, currentZ, radius);
        addChunksInRadius(needed, predictedX, predictedY, predictedZ, radius);

        return needed;
    }

    private void addChunksInRadius(Set<VxSectionPos> needed, double centerX, double centerY, double centerZ, int radius) {
        VxSectionPos centerVPos = VxSectionPos.fromWorldSpace(centerX, centerY, centerZ);
        for (int y = -radius; y <= radius; ++y) {
            for (int z = -radius; z <= radius; ++z) {
                for (int x = -radius; x <= radius; ++x) {
                    VxSectionPos pos = new VxSectionPos(centerVPos.x() + x, centerVPos.y() + y, centerVPos.z() + z);
                    if (pos.isWithinWorldHeight(terrainSystem.getLevel())) {
                        needed.add(pos);
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