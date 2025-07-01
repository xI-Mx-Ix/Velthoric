package net.xmx.xbullet.physics.terrain.manager;

import com.github.stephengold.joltjni.RVec3;
import net.minecraft.server.level.ServerLevel;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManager;
import net.xmx.xbullet.physics.physicsworld.PhysicsWorld;
import net.xmx.xbullet.physics.terrain.chunk.TerrainSection;
import net.xmx.xbullet.physics.terrain.pcmd.active.ActivateTerrainSectionCommand;
import net.xmx.xbullet.physics.terrain.pcmd.active.DeactivateTerrainSectionCommand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class TerrainPriorityUpdater {

    private final ServerLevel level;
    private final PhysicsObjectManager physicsObjectManager;
    private final PhysicsWorld physicsWorld;

    private static final double ACTIVATION_RADIUS = 32.0;
    private static final double ACTIVATION_RADIUS_SQ = ACTIVATION_RADIUS * ACTIVATION_RADIUS;

    public TerrainPriorityUpdater(ServerLevel level, PhysicsObjectManager physicsObjectManager, PhysicsWorld physicsWorld) {
        this.level = level;
        this.physicsObjectManager = physicsObjectManager;
        this.physicsWorld = physicsWorld;
    }

    public PriorityQueue<TerrainSection> createPriorityQueue(TerrainChunkManager chunkManager) {
        List<RVec3> pointsOfInterest = gatherPointsOfInterest();
        if (pointsOfInterest.isEmpty()) {
            return new PriorityQueue<>();
        }

        chunkManager.getManagedSections().forEach(section -> {
            double minDstSq = calculateMinDistanceSq(section, pointsOfInterest);

            handleActivation(section, minDstSq);

            if (section.getState() == TerrainSection.State.PLACEHOLDER) {
                if (section.getPriority() != Double.MAX_VALUE) {
                    section.setPriority(1.0 / (1.0 + Math.sqrt(minDstSq)));
                }
            }
        });

        PriorityQueue<TerrainSection> queue = new PriorityQueue<>(Comparator.comparingDouble(TerrainSection::getPriority).reversed());
        chunkManager.getManagedSections().stream()
                .filter(s -> s.getState() == TerrainSection.State.PLACEHOLDER && s.getPriority() > 0)
                .forEach(queue::add);

        return queue;
    }

    private void handleActivation(TerrainSection section, double minDistanceSq) {
        boolean shouldBeActive = minDistanceSq <= ACTIVATION_RADIUS_SQ;

        if (shouldBeActive && section.getState() == TerrainSection.State.READY_INACTIVE) {

            physicsWorld.queueCommand(new ActivateTerrainSectionCommand(section));
        } else if (!shouldBeActive && section.getState() == TerrainSection.State.READY_ACTIVE) {

            physicsWorld.queueCommand(new DeactivateTerrainSectionCommand(section));
        }
    }

    private double calculateMinDistanceSq(TerrainSection section, List<RVec3> pointsOfInterest) {
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
                .min().orElse(Double.MAX_VALUE);
    }

    private List<RVec3> gatherPointsOfInterest() {
        List<RVec3> points = new ArrayList<>();
        level.players().forEach(p -> points.add(new RVec3(p.getX(), p.getY(), p.getZ())));

        if (physicsObjectManager != null && physicsObjectManager.isInitialized()) {
            physicsObjectManager.getManagedObjects().values().stream()
                    .filter(o -> o.isPhysicsInitialized() && o.getBodyId() != 0)
                    .forEach(o -> points.add(o.getCurrentTransform().getTranslation()));
        }
        return points;
    }
}