package net.xmx.xbullet.physics.object.global.click;

import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.xmx.xbullet.physics.object.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.world.PhysicsWorld;
import net.xmx.xbullet.physics.object.global.PhysicsRaytracing;

import java.util.Optional;
import java.util.function.Supplier;

public final class PhysicsClickManager {

    private PhysicsClickManager() {}

    public static void processClick(PhysicsClickPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) return;
            ServerLevel level = sender.serverLevel();

            RVec3 rayOrigin = new RVec3(msg.rayOriginX(), msg.rayOriginY(), msg.rayOriginZ());
            Vec3 rayDirection = new Vec3(msg.rayDirectionX(), msg.rayDirectionY(), msg.rayDirectionZ());

            PhysicsWorld physicsWorld = PhysicsWorld.get(level.dimension());
            if (physicsWorld == null || !physicsWorld.isRunning()) {
                return;
            }

            physicsWorld.execute(() -> {
                Optional<PhysicsRaytracing.CombinedHitResult> combinedHitOpt = PhysicsRaytracing.rayCast(
                        level, rayOrigin, rayDirection, PhysicsRaytracing.DEFAULT_MAX_DISTANCE
                );

                if (combinedHitOpt.isPresent() && combinedHitOpt.get().isPhysicsHit()) {
                    PhysicsRaytracing.PhysicsHitInfo physicsHit = combinedHitOpt.get().getPhysicsHit().get();
                    int bodyId = physicsHit.getBodyId();

                    Optional<IPhysicsObject> targetObjectOpt = physicsWorld.findPhysicsObjectByBodyId(bodyId);

                    if (targetObjectOpt.isPresent()) {
                        final IPhysicsObject finalTargetObject = targetObjectOpt.get();

                        sender.getServer().execute(() -> {
                            RVec3 hitPoint = physicsHit.calculateHitPoint(rayOrigin, rayDirection, PhysicsRaytracing.DEFAULT_MAX_DISTANCE);
                            Vec3 hitNormal = physicsHit.getHitNormal();

                            if (msg.isRightClick()) {
                                finalTargetObject.tryStartRiding(sender, hitPoint.toVec3(), hitNormal);
                                finalTargetObject.onRightClick(sender, hitPoint.toVec3(), hitNormal);
                                finalTargetObject.onRightClickWithTool(sender);
                            } else {
                                finalTargetObject.onLeftClick(sender, hitPoint.toVec3(), hitNormal);
                            }
                        });
                    }
                }
            });
        });
        context.setPacketHandled(true);
    }
}