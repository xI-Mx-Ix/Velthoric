package net.xmx.xbullet.item.physicsgun;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.operator.Op;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.xbullet.physics.object.global.PhysicsRaytracing;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.physicsworld.PhysicsWorld;
import net.xmx.xbullet.physics.physicsworld.PhysicsWorldRegistry;
import java.util.Optional;

public class PhysicsGunServerRaycastHandler {

    private static final float MAX_GRAB_RANGE = 30.0f;

    public static Optional<HitResult> performRaycast(ServerPlayer player) {
        PhysicsWorld world = PhysicsWorldRegistry.getInstance().getPhysicsWorld(player.serverLevel().dimension());
        if (world == null || !world.isRunning()) {
            return Optional.empty();
        }

        RVec3 eyePos = getPlayerEyePos(player);
        Vec3 lookVec = getPlayerLookVec(player);

        Optional<PhysicsRaytracing.CombinedHitResult> combinedHit = PhysicsRaytracing.rayCast(
                player.serverLevel(), eyePos, lookVec, MAX_GRAB_RANGE
        );

        if (combinedHit.isPresent() && combinedHit.get().isPhysicsHit()) {
            PhysicsRaytracing.PhysicsHitInfo physicsHit = combinedHit.get().getPhysicsHit().get();

            Optional<IPhysicsObject> pcoOpt = world.findPhysicsObjectByBodyId(physicsHit.getBodyId());
            if (pcoOpt.isPresent()) {
                RVec3 hitPointRVec = physicsHit.calculateHitPoint(eyePos, lookVec, MAX_GRAB_RANGE);
                return Optional.of(new HitResult(pcoOpt.get(), hitPointRVec.toVec3()));
            }
        }

        return Optional.empty();
    }

    public static int findClosestNode(Body softBody, Vec3 hitPoint) {
        if (!softBody.isSoftBody()) {
            return -1;
        }

        try (SoftBodyMotionProperties mp = (SoftBodyMotionProperties) softBody.getMotionProperties()) {
            if (mp == null) {
                return -1;
            }

            int bestNode = -1;
            float minDistSq = Float.MAX_VALUE;

            int numVertices = mp.getSettings().countVertices();

            for (int i = 0; i < numVertices; i++) {

                try (SoftBodyVertex vertex = mp.getVertex(i)) {
                    if (vertex == null) continue;

                    Vec3 nodePos = vertex.getPosition();
                    float distSq = Op.minus(nodePos, hitPoint).lengthSq();

                    if (distSq < minDistSq) {
                        minDistSq = distSq;
                        bestNode = i;
                    }
                }
            }
            return bestNode;
        }
    }

    private static RVec3 getPlayerEyePos(ServerPlayer player) {
        net.minecraft.world.phys.Vec3 eyePos = player.getEyePosition();
        return new RVec3(eyePos.x, eyePos.y, eyePos.z);
    }

    private static Vec3 getPlayerLookVec(ServerPlayer player) {
        net.minecraft.world.phys.Vec3 lookVec = player.getViewVector(1.0f);
        return new Vec3((float)lookVec.x, (float)lookVec.y, (float)lookVec.z);
    }

    public record HitResult(IPhysicsObject physicsObject, Vec3 hitPoint) {}
}