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
        if (world == null || !world.isRunning()) return Optional.empty();

        Optional<PhysicsRaytracing.RayHitInfo> hitInfoOpt = PhysicsRaytracing.rayCastPhysics(
                player.serverLevel(), getPlayerEyePos(player), getPlayerLookVec(player), MAX_GRAB_RANGE);

        if (hitInfoOpt.isPresent()) {
            PhysicsRaytracing.RayHitInfo physicsHit = hitInfoOpt.get();
            RVec3 hitPoint = physicsHit.calculateHitPoint(getPlayerEyePos(player), getPlayerLookVec(player));

            if (PhysicsRaytracing.isBlockInTheWay(player.serverLevel(), getPlayerEyePos(player), hitPoint)) {
                return Optional.empty();
            }

            Optional<IPhysicsObject> pcoOpt = world.findPhysicsObjectByBodyId(physicsHit.getBodyId());
            if (pcoOpt.isPresent()) {
                return Optional.of(new HitResult(pcoOpt.get(), hitPoint.toVec3()));
            }
        }
        return Optional.empty();
    }

    public static int findClosestNode(Body softBody, com.github.stephengold.joltjni.Vec3 hitPoint) {
        // Diese Methode wird innerhalb eines Locks aufgerufen, daher ist der Zugriff auf softBody sicher.
        // MotionProperties muss jedoch korrekt verwaltet werden.
        try (SoftBodyMotionProperties mp = (SoftBodyMotionProperties) softBody.getMotionProperties()) {
            if (mp == null) {
                return -1;
            }

            int bestNode = -1;
            float minDistSq = Float.MAX_VALUE;
            SoftBodyVertex[] vertices = mp.getVertices();

            for (int i = 0; i < vertices.length; i++) {
                // Jedes Vertex-Objekt ist AutoCloseable und muss in einem try-Block verwendet werden.
                try (SoftBodyVertex vertex = vertices[i]) {
                    com.github.stephengold.joltjni.Vec3 nodePos = vertex.getPosition();
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

    private static com.github.stephengold.joltjni.Vec3 getPlayerLookVec(ServerPlayer player) {
        net.minecraft.world.phys.Vec3 lookVec = player.getViewVector(1.0f);
        return new com.github.stephengold.joltjni.Vec3((float)lookVec.x, (float)lookVec.y, (float)lookVec.z);
    }

    public record HitResult(IPhysicsObject physicsObject, com.github.stephengold.joltjni.Vec3 hitPoint) {}
}