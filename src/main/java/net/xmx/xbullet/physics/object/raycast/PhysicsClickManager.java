package net.xmx.xbullet.physics.object.raycast;

import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.xmx.xbullet.physics.object.raycast.info.PhysicsHitInfo;
import net.xmx.xbullet.physics.object.raycast.packet.PhysicsClickPacket;
import net.xmx.xbullet.physics.object.raycast.result.CombinedHitResult;
import net.xmx.xbullet.physics.world.PhysicsWorld;

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
                Optional<CombinedHitResult> combinedHitOpt = PhysicsRaytracing.rayCast(
                        level, rayOrigin, rayDirection, PhysicsRaytracing.DEFAULT_MAX_DISTANCE
                );

                combinedHitOpt
                        .flatMap(CombinedHitResult::getPhysicsHit)
                        .flatMap(physicsHit -> physicsWorld.findPhysicsObjectByBodyId(physicsHit.getBodyId()))
                        .ifPresent(targetObject -> {

                            sender.getServer().execute(() -> {
                                PhysicsHitInfo physicsHit = combinedHitOpt.get().getPhysicsHit().get();
                                RVec3 hitPoint = physicsHit.calculateHitPoint(rayOrigin, rayDirection, PhysicsRaytracing.DEFAULT_MAX_DISTANCE);
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
        });
        context.setPacketHandled(true);
    }
}