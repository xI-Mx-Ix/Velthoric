package net.xmx.vortex.physics.object.raycast;

import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.vortex.physics.object.raycast.info.PhysicsHitInfo;
import net.xmx.vortex.physics.object.raycast.packet.PhysicsClickPacket;
import net.xmx.vortex.physics.object.raycast.result.CombinedHitResult;
import net.xmx.vortex.physics.world.VxPhysicsWorld;

import java.util.Optional;

public final class PhysicsClickManager {

    private PhysicsClickManager() {}

    public static void processClick(PhysicsClickPacket msg, ServerPlayer sender) {
        if (sender == null) return;
        ServerLevel level = sender.serverLevel();

        RVec3 rayOrigin = new RVec3(msg.rayOriginX(), msg.rayOriginY(), msg.rayOriginZ());
        Vec3 rayDirection = new Vec3(msg.rayDirectionX(), msg.rayDirectionY(), msg.rayDirectionZ());

        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(level.dimension());
        if (physicsWorld == null || !physicsWorld.isRunning()) {
            return;
        }

        physicsWorld.execute(() -> {
            Optional<CombinedHitResult> combinedHitOpt = VxRaytracing.rayCast(
                    level, rayOrigin, rayDirection, VxRaytracing.DEFAULT_MAX_DISTANCE, sender
            );

            combinedHitOpt
                    .flatMap(CombinedHitResult::getPhysicsHit)
                    .flatMap(physicsHit -> physicsWorld.findPhysicsObjectByBodyId(physicsHit.getBodyId()))
                    .ifPresent(targetObject -> {
                        sender.getServer().execute(() -> {
                            PhysicsHitInfo physicsHit = combinedHitOpt.get().getPhysicsHit().get();
                            RVec3 hitPoint = physicsHit.calculateHitPoint(rayOrigin, rayDirection, VxRaytracing.DEFAULT_MAX_DISTANCE);
                            Vec3 hitNormal = physicsHit.getHitNormal();

                            if (msg.isRightClick()) {
                                targetObject.onRightClick(sender, hitPoint.toVec3(), hitNormal);
                                targetObject.onRightClickWithTool(sender);
                            } else {
                                targetObject.onLeftClick(sender, hitPoint.toVec3(), hitNormal);
                            }
                        });
                    });
        });
    }
}