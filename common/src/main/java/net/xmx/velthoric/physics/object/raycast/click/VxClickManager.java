package net.xmx.velthoric.physics.object.raycast.click;

import com.github.stephengold.joltjni.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.xmx.velthoric.init.registry.ItemRegistry;
import net.xmx.velthoric.physics.object.physicsobject.manager.VxRemovalReason;
import net.xmx.velthoric.physics.object.raycast.VxClipContext;
import net.xmx.velthoric.physics.object.raycast.VxHitResult;
import net.xmx.velthoric.physics.object.raycast.VxRaytracing;
import net.xmx.velthoric.physics.object.raycast.click.packet.VxClickPacket;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.Optional;

public final class VxClickManager {

    private VxClickManager() {
    }

    public static void processClick(VxClickPacket msg, ServerPlayer sender) {
        if (sender == null) return;
        ServerLevel level = sender.serverLevel();

        net.minecraft.world.phys.Vec3 rayOriginMc = new net.minecraft.world.phys.Vec3(msg.rayOriginX(), msg.rayOriginY(), msg.rayOriginZ());
        net.minecraft.world.phys.Vec3 rayDirectionMc = new net.minecraft.world.phys.Vec3(msg.rayDirectionX(), msg.rayDirectionY(), msg.rayDirectionZ()).normalize();
        net.minecraft.world.phys.Vec3 rayEndMc = rayOriginMc.add(rayDirectionMc.scale(VxRaytracing.DEFAULT_MAX_DISTANCE));

        VxClipContext context = new VxClipContext(rayOriginMc, rayEndMc, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sender, true);

        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(level.dimension());
        if (physicsWorld == null || !physicsWorld.isRunning()) {
            return;
        }

        physicsWorld.execute(() -> {
            Optional<VxHitResult> hitResultOpt = VxRaytracing.raycast(level, context);

            hitResultOpt.ifPresent(
                    hitResult -> hitResult.getPhysicsHit().ifPresent(
                    physicsHit -> physicsWorld.getObjectManager().getObjectContainer().getByBodyId(physicsHit.bodyId())
                    .ifPresent(targetObject ->
                            sender.getServer().execute(() -> {
                        net.minecraft.world.phys.Vec3 location = hitResult.getLocation();
                        Vec3 hitPoint = new Vec3((float) location.x(), (float) location.y(), (float) location.z());
                        Vec3 hitNormal = physicsHit.hitNormal();

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
                    }))));
        });
    }
}