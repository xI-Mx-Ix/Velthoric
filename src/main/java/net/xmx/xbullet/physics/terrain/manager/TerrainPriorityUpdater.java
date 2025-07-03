package net.xmx.xbullet.physics.terrain.manager;

import com.github.stephengold.joltjni.RVec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.ObjectManager;
import net.xmx.xbullet.physics.terrain.chunk.TerrainSection;
import net.xmx.xbullet.physics.terrain.pcmd.active.ActivateTerrainSectionCommand;
import net.xmx.xbullet.physics.terrain.pcmd.active.DeactivateTerrainSectionCommand;
import net.xmx.xbullet.physics.world.PhysicsWorld;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class TerrainPriorityUpdater {

    private final ServerLevel level;
    private final ObjectManager physicsObjectManager;
    private final PhysicsWorld physicsWorld;

    private static final double ACTIVATION_RADIUS = 48.0;
    private static final double MESHING_RADIUS = 128.0;
    private static final double PHYSICS_OBJECT_WEIGHT = 4.0;
    private static final double ACTIVATION_RADIUS_SQ = ACTIVATION_RADIUS * ACTIVATION_RADIUS;
    private static final double MESHING_RADIUS_SQ = MESHING_RADIUS * MESHING_RADIUS;

    public TerrainPriorityUpdater(ServerLevel level, ObjectManager physicsObjectManager, PhysicsWorld physicsWorld) {
        this.level = level;
        this.physicsObjectManager = physicsObjectManager;
        this.physicsWorld = physicsWorld;
    }

    public PriorityQueue<TerrainSection> updateAndCreateQueue(TerrainChunkManager chunkManager) {
        List<RVec3> playerPOIs = gatherPlayerPOIs();
        List<RVec3> physicsObjectPOIs = gatherPhysicsObjectPOIs();

        if (playerPOIs.isEmpty() && physicsObjectPOIs.isEmpty()) {
            chunkManager.getManagedSections().stream()
                    .filter(s -> s.getState() == TerrainSection.State.READY_ACTIVE)
                    .forEach(s -> physicsWorld.queueCommand(new DeactivateTerrainSectionCommand(s)));
            return new PriorityQueue<>();
        }

        PriorityQueue<TerrainSection> meshingQueue = new PriorityQueue<>(Comparator.comparingDouble(TerrainSection::getPriority).reversed());

        for (TerrainSection section : chunkManager.getManagedSections()) {
            double weightedMinDistanceSq = calculateWeightedMinDistanceSq(section, playerPOIs, physicsObjectPOIs);
            double realMinDistanceSq = calculateRealMinDistanceSq(section, playerPOIs, physicsObjectPOIs);

            handleActivation(section, realMinDistanceSq);
            handleMeshingPriority(section, weightedMinDistanceSq, meshingQueue);
        }

        return meshingQueue;
    }

    private void handleActivation(TerrainSection section, double realMinDistanceSq) {
        final boolean shouldBeActive = realMinDistanceSq <= ACTIVATION_RADIUS_SQ;
        final TerrainSection.State currentState = section.getState();

        if (shouldBeActive && currentState == TerrainSection.State.READY_INACTIVE) {
            physicsWorld.queueCommand(new ActivateTerrainSectionCommand(section));
        } else if (!shouldBeActive && currentState == TerrainSection.State.READY_ACTIVE) {
            physicsWorld.queueCommand(new DeactivateTerrainSectionCommand(section));
        }
    }

    private void handleMeshingPriority(TerrainSection section, double weightedMinDistanceSq, PriorityQueue<TerrainSection> queue) {
        if (section.getState() != TerrainSection.State.PLACEHOLDER) {
            return;
        }

        if (weightedMinDistanceSq > MESHING_RADIUS_SQ) {
            if (section.getPriority() != Double.MAX_VALUE) {
                return;
            }
        }

        if (section.getPriority() != Double.MAX_VALUE) {
            section.setPriority(1.0 / (1.0 + Math.sqrt(weightedMinDistanceSq)));
        }

        queue.add(section);
    }

    private double calculateRealMinDistanceSq(TerrainSection section, List<RVec3> playerPOIs, List<RVec3> physicsObjectPOIs) {
        double minPlayerDistSq = calculateMinDistanceSqForList(section, playerPOIs);
        double minPhysicsObjDistSq = calculateMinDistanceSqForList(section, physicsObjectPOIs);
        return Math.min(minPlayerDistSq, minPhysicsObjDistSq);
    }

    private double calculateWeightedMinDistanceSq(TerrainSection section, List<RVec3> playerPOIs, List<RVec3> physicsObjectPOIs) {
        double minPlayerDistSq = calculateMinDistanceSqForList(section, playerPOIs);
        double minPhysicsObjDistSq = calculateMinDistanceSqForList(section, physicsObjectPOIs) / (PHYSICS_OBJECT_WEIGHT * PHYSICS_OBJECT_WEIGHT);
        return Math.min(minPlayerDistSq, minPhysicsObjDistSq);
    }

    private double calculateMinDistanceSqForList(TerrainSection section, List<RVec3> pointsOfInterest) {
        if (pointsOfInterest.isEmpty()) {
            return Double.MAX_VALUE;
        }
        final double centerX = section.getPos().center().getX();
        final double centerY = section.getPos().center().getY();
        final double centerZ = section.getPos().center().getZ();
        return pointsOfInterest.stream()
                .mapToDouble(poi -> {
                    double dx = poi.xx() - centerX;
                    double dy = poi.yy() - centerY;
                    double dz = poi.zz() - centerZ;
                    return dx * dx + dy * dy + dz * dz;
                })
                .min()
                .orElse(Double.MAX_VALUE);
    }

    private List<RVec3> gatherPlayerPOIs() {
        List<RVec3> points = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            points.add(new RVec3(player.getX(), player.getY(), player.getZ()));
        }
        return points;
    }

    private List<RVec3> gatherPhysicsObjectPOIs() {
        List<RVec3> points = new ArrayList<>();
        if (this.physicsObjectManager == null || !this.physicsObjectManager.isInitialized()) {
            return points;
        }

        for (IPhysicsObject physicsObject : this.physicsObjectManager.getManagedObjects().values()) {
            if (physicsObject.isPhysicsInitialized() && physicsObject.getBodyId() != 0) {
                points.add(physicsObject.getCurrentTransform().getTranslation());
            }
        }
        return points;
    }
}