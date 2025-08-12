package net.xmx.vortex.physics.object.raycast;

import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.vortex.init.registry.ItemRegistry;
import net.xmx.vortex.physics.object.physicsobject.manager.VxRemovalReason;
import net.xmx.vortex.physics.object.raycast.info.PhysicsHitInfo;
import net.xmx.vortex.physics.object.raycast.packet.PhysicsClickPacket;
import net.xmx.vortex.physics.object.raycast.result.CombinedHitResult;
import net.xmx.vortex.physics.object.riding.Rideable;
import net.xmx.vortex.physics.world.VxPhysicsWorld;

import java.util.Optional;

public final class VxClickManager {

    private VxClickManager() {
    }

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
                    .flatMap(physicsHit -> physicsWorld.getObjectManager().getObjectContainer().getByBodyId(physicsHit.getBodyId()))
                    .ifPresent(targetObject -> {
                        sender.getServer().execute(() -> {
                            PhysicsHitInfo physicsHit = combinedHitOpt.get().getPhysicsHit().get();
                            RVec3 hitPointR = physicsHit.calculateHitPoint(rayOrigin, rayDirection, VxRaytracing.DEFAULT_MAX_DISTANCE);
                            Vec3 hitPoint = hitPointR.toVec3();
                            Vec3 hitNormal = physicsHit.getHitNormal();

                            if (targetObject instanceof Clickable clickable) {
                                if (msg.isRightClick()) {
                                    clickable.onRightClick(sender, hitPoint, hitNormal);

                                    if (sender.getMainHandItem().getItem() == ItemRegistry.PHYSICS_REMOVER_STICK.get()) {
                                        var objectManager = physicsWorld.getObjectManager();
                                        objectManager.removeObject(targetObject.getPhysicsId(), VxRemovalReason.DISCARD);
                                    }
                                } else {
                                    clickable.onLeftClick(sender, hitPoint, hitNormal);
                                }
                            }

                            if (msg.isRightClick() && targetObject instanceof Rideable rideable) {
                                rideable.handleRightClick(sender, hitPoint);
                            }
                        });
                    });
        });
    }
}