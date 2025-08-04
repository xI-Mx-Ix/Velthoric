package net.xmx.vortex.physics.terrain.tracker;

import com.github.stephengold.joltjni.AaBox;
import com.github.stephengold.joltjni.Body;
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

public class ObjectTerrainTracker {

    private final IPhysicsObject physicsObject;
    private final TerrainSystem terrainSystem;

    private final Set<VxSectionPos> currentPreloadedChunks = new HashSet<>();
    private final Map<VxSectionPos, VxTaskPriority> requiredChunks = new HashMap<>();
    private final Set<VxSectionPos> chunksToRelease = new HashSet<>();
    private final Vec3 tempDisplacement = new Vec3();
    private final RVec3 tempRVec3Min = new RVec3();
    private final RVec3 tempRVec3Max = new RVec3();
    private final Vec3 tempVec3Min = new Vec3();
    private final Vec3 tempVec3Max = new Vec3();
    private final AaBox predictedAabb = new AaBox();
    private final Set<VxSectionPos> criticalChunksToReturn = new HashSet<>();

    private int updateCooldown = 0;
    private static final int UPDATE_INTERVAL_TICKS = 10;
    private static final float MAX_SPEED_FOR_COOLDOWN_SQR = 100f * 100f;

    private static final int PRELOAD_RADIUS_CHUNKS = 5;
    private static final int ACTIVATION_RADIUS_CHUNKS = 2;
    private static final float PREDICTION_SECONDS = 0.5f;

    public ObjectTerrainTracker(IPhysicsObject physicsObject, TerrainSystem terrainSystem) {
        this.physicsObject = physicsObject;
        this.terrainSystem = terrainSystem;
    }

    public Set<VxSectionPos> update() {
        Body body = physicsObject.getBody();
        if (body == null || !physicsObject.isPhysicsInitialized()) {
            releaseAll();
            criticalChunksToReturn.clear();
            return criticalChunksToReturn;
        }

        Vec3 vel = body.getLinearVelocity();
        if (updateCooldown > 0 && vel.lengthSq() < MAX_SPEED_FOR_COOLDOWN_SQR) {
            updateCooldown--;
            return criticalChunksToReturn;
        }
        updateCooldown = UPDATE_INTERVAL_TICKS;

        ConstAaBox aabb = body.getWorldSpaceBounds();
        calculateAndPrioritizeChunks(aabb, vel);

        chunksToRelease.clear();
        chunksToRelease.addAll(currentPreloadedChunks);
        chunksToRelease.removeAll(requiredChunks.keySet());
        chunksToRelease.forEach(terrainSystem::releaseChunk);

        for (Map.Entry<VxSectionPos, VxTaskPriority> entry : requiredChunks.entrySet()) {
            VxSectionPos pos = entry.getKey();
            if (!currentPreloadedChunks.contains(pos)) {
                terrainSystem.requestChunk(pos);
            }
            terrainSystem.prioritizeChunk(pos, entry.getValue());
        }

        currentPreloadedChunks.clear();
        currentPreloadedChunks.addAll(requiredChunks.keySet());

        criticalChunksToReturn.clear();
        for (Map.Entry<VxSectionPos, VxTaskPriority> entry : requiredChunks.entrySet()) {
            if (entry.getValue() == VxTaskPriority.CRITICAL) {
                criticalChunksToReturn.add(entry.getKey());
            }
        }
        return criticalChunksToReturn;
    }

    private void calculateAndPrioritizeChunks(ConstAaBox currentAabb, Vec3 velocity) {
        requiredChunks.clear();

        addChunksForAabb(currentAabb, PRELOAD_RADIUS_CHUNKS, VxTaskPriority.MEDIUM);
        addChunksForAabb(currentAabb, ACTIVATION_RADIUS_CHUNKS, VxTaskPriority.CRITICAL);

        tempDisplacement.set(velocity);
        Op.starEquals(tempDisplacement, PREDICTION_SECONDS);

        tempRVec3Min.set(currentAabb.getMin());
        Op.plusEquals(tempRVec3Min, tempDisplacement);

        tempRVec3Max.set(currentAabb.getMax());
        Op.plusEquals(tempRVec3Max, tempDisplacement);

        tempVec3Min.set(tempRVec3Min);
        tempVec3Max.set(tempRVec3Max);
        predictedAabb.setMin(tempVec3Min);
        predictedAabb.setMax(tempVec3Max);

        addChunksForAabb(predictedAabb, PRELOAD_RADIUS_CHUNKS, VxTaskPriority.MEDIUM);
        addChunksForAabb(predictedAabb, ACTIVATION_RADIUS_CHUNKS, VxTaskPriority.CRITICAL);
    }

    private void addChunksForAabb(ConstAaBox aabb, int radiusInChunks, VxTaskPriority priority) {
        tempRVec3Min.set(aabb.getMin());
        tempRVec3Max.set(aabb.getMax());

        VxSectionPos minSection = VxSectionPos.fromWorldSpace(tempRVec3Min.xx(), tempRVec3Min.yy(), tempRVec3Min.zz());
        VxSectionPos maxSection = VxSectionPos.fromWorldSpace(tempRVec3Max.xx(), tempRVec3Max.yy(), tempRVec3Max.zz());

        final int worldMinY = terrainSystem.getLevel().getMinBuildHeight() >> 4;
        final int worldMaxY = terrainSystem.getLevel().getMaxBuildHeight() >> 4;

        for (int y = minSection.y() - radiusInChunks; y <= maxSection.y() + radiusInChunks; ++y) {
            if (y < worldMinY || y >= worldMaxY) continue;
            for (int z = minSection.z() - radiusInChunks; z <= maxSection.z() + radiusInChunks; ++z) {
                for (int x = minSection.x() - radiusInChunks; x <= maxSection.x() + radiusInChunks; ++x) {
                    VxSectionPos pos = new VxSectionPos(x, y, z);
                    requiredChunks.merge(pos, priority, (oldP, newP) -> newP.ordinal() > oldP.ordinal() ? newP : oldP);
                }
            }
        }
    }

    public void releaseAll() {
        currentPreloadedChunks.forEach(terrainSystem::releaseChunk);
        currentPreloadedChunks.clear();
    }
}