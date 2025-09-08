package net.xmx.velthoric.physics.raycasting.click;

import com.github.stephengold.joltjni.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.xmx.velthoric.init.registry.ItemRegistry;
import net.xmx.velthoric.physics.object.manager.VxRemovalReason;
import net.xmx.velthoric.physics.riding.Rideable;
import net.xmx.velthoric.physics.raycasting.VxClipContext;
import net.xmx.velthoric.physics.raycasting.VxHitResult;
import net.xmx.velthoric.physics.raycasting.VxRaytracing;
import net.xmx.velthoric.physics.raycasting.click.packet.VxClickPacket;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.Optional;

public final class VxClickManager {

    private VxClickManager() {}

    public static void processClick(VxClickPacket msg, ServerPlayer sender) {
        if (sender == null) return;
        ServerLevel level = sender.serverLevel();

        net.minecraft.world.phys.Vec3 rayOriginMc = new net.minecraft.world.phys.Vec3(msg.rayOriginX(), msg.rayOriginY(), msg.rayOriginZ());
        net.minecraft.world.phys.Vec3 rayDirectionMc = new net.minecraft.world.phys.Vec3(msg.rayDirectionX(), msg.rayDirectionY(), msg.rayDirectionZ()).normalize();
        net.minecraft.world.phys.Vec3 rayEndMc = rayOriginMc.add(rayDirectionMc.scale(VxRaytracing.DEFAULT_MAX_DISTANCE));

        VxClipContext context = new VxClipContext(rayOriginMc, rayEndMc, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sender, true, true);

        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(level.dimension());
        if (physicsWorld == null || !physicsWorld.isRunning()) {
            return;
        }

        physicsWorld.execute(() -> {
            Optional<VxHitResult> hitResultOpt = VxRaytracing.raycast(level, context);

            hitResultOpt.ifPresent(hitResult -> hitResult.getPhysicsHit().ifPresent(physicsHit ->
                    physicsWorld.getObjectManager().getByBodyId(physicsHit.bodyId())
                            .ifPresent(targetObject -> sender.getServer().execute(() -> {
                                if (msg.isRightClick()) {
                                    if (hitResult.isSeatHit() && targetObject instanceof Rideable rideable) {
                                        hitResult.getSeatHit()
                                                .flatMap(seatHit -> physicsWorld.getRidingManager().getSeat(rideable.getPhysicsId(), seatHit.seatName()))
                                                .ifPresent(seat -> {
                                                    if (!seat.isLocked()) {
                                                        physicsWorld.getRidingManager().startRiding(sender, rideable, seat);
                                                    }
                                                });
                                        return;
                                    }

                                    if (sender.getMainHandItem().getItem() == ItemRegistry.PHYSICS_REMOVER_STICK.get()) {
                                        var objectManager = physicsWorld.getObjectManager();
                                        objectManager.removeObject(targetObject.getPhysicsId(), VxRemovalReason.DISCARD);
                                        return;
                                    }
                                }

                                if (targetObject instanceof Clickable clickable) {
                                    net.minecraft.world.phys.Vec3 location = hitResult.getLocation();
                                    Vec3 hitPoint = new Vec3((float) location.x(), (float) location.y(), (float) location.z());
                                    Vec3 hitNormal = physicsHit.hitNormal();
                                    if (msg.isRightClick()) {
                                        clickable.onRightClick(sender, hitPoint, hitNormal);
                                    } else {
                                        clickable.onLeftClick(sender, hitPoint, hitNormal);
                                    }
                                }
                            }))
            ));
        });
    }
}