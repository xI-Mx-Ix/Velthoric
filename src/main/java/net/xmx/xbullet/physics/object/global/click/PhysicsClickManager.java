package net.xmx.xbullet.physics.object.global.click;

import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.core.PhysicsWorld;
import net.xmx.xbullet.physics.core.PhysicsWorldRegistry;
import net.xmx.xbullet.physics.object.global.PhysicsRaytracing;

import java.util.Optional;
import java.util.function.Supplier;

public final class PhysicsClickManager {

    private PhysicsClickManager() {}

    public static void processClick(PhysicsClickPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            try {
                ServerPlayer sender = context.getSender();
                if (sender == null) return;
                ServerLevel level = sender.serverLevel();

                RVec3 rayOrigin = new RVec3(msg.rayOriginX(), msg.rayOriginY(), msg.rayOriginZ());
                Vec3 rayDirection = new Vec3(msg.rayDirectionX(), msg.rayDirectionY(), msg.rayDirectionZ());

                PhysicsWorld physicsWorld = PhysicsWorldRegistry.getInstance().getPhysicsWorld(level.dimension());
                if (physicsWorld == null || !physicsWorld.isRunning()) {
                    return;
                }

                physicsWorld.execute(() -> {

                    Optional<PhysicsRaytracing.RayHitInfo> hitInfoOpt = PhysicsRaytracing.rayCastPhysics(
                            level, rayOrigin, rayDirection, PhysicsRaytracing.DEFAULT_MAX_DISTANCE
                    );

                    if (hitInfoOpt.isPresent()) {
                        PhysicsRaytracing.RayHitInfo hitInfo = hitInfoOpt.get();
                        int bodyId = hitInfo.getBodyId();

                        Optional<IPhysicsObject> targetObjectOpt = physicsWorld.findPhysicsObjectByBodyId(bodyId);

                        if (targetObjectOpt.isPresent()) {
                            final IPhysicsObject finalTargetObject = targetObjectOpt.get();
                            RVec3 hitPoint = hitInfo.calculateHitPoint(rayOrigin, rayDirection);
                            Vec3 hitNormal = hitInfo.getHitNormal();

                            MinecraftServer server = level.getServer();
                            if (server != null) {

                                server.execute(() -> handlePhysicsRayHitOnMainThread(
                                        level, finalTargetObject, rayOrigin, hitPoint, hitNormal, msg.isRightClick(), sender
                                ));
                            }
                        }
                    }
                });
            } catch (Exception e) {
                XBullet.LOGGER.error("PhysicsClickManager: Uncaught exception during packet processing.", e);
            }
        });
        context.setPacketHandled(true);
    }

    private static void handlePhysicsRayHitOnMainThread(
            ServerLevel level, IPhysicsObject targetObject, RVec3 rayOrigin,
            RVec3 hitPoint, Vec3 hitNormal, boolean isRightClick, Player clickSourcePlayer) {

        if (PhysicsRaytracing.isBlockInTheWay(level, rayOrigin, hitPoint)) {
            return;
        }

        Vec3 hitPointVec3 = hitPoint.toVec3();

        try {

            if (isRightClick) {
                targetObject.onRightClick(clickSourcePlayer, hitPointVec3, hitNormal);
            } else {
                targetObject.onLeftClick(clickSourcePlayer, hitPointVec3, hitNormal);
            }
        } catch (Exception e) {
            XBullet.LOGGER.error("Exception during click handling for object {}: {}",
                    targetObject.getPhysicsId(), e.getMessage(), e);
        }
    }
}