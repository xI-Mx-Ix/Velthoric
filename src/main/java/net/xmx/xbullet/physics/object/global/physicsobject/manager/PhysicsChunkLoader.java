package net.xmx.xbullet.physics.object.global.physicsobject.manager;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.XBulletSavedData;
import net.xmx.xbullet.physics.constraint.IConstraint;
import net.xmx.xbullet.physics.constraint.manager.ConstraintManager;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectLoader;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManager;
import net.xmx.xbullet.physics.object.global.physicsobject.pcmd.ActivateBodyCommand;
import net.xmx.xbullet.physics.world.PhysicsWorld;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class PhysicsChunkLoader {

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.isClientSide()) {
            return;
        }

        PhysicsWorld world = PhysicsWorld.get(level.dimension());
        if (world == null || !world.isRunning()) {
            return;
        }

        ChunkPos chunkPos = event.getChunk().getPos();
        PhysicsObjectManager objectManager = world.getObjectManager();
        ConstraintManager constraintManager = world.getConstraintManager();
        XBulletSavedData savedData = objectManager.getSavedData();

        if (objectManager == null || constraintManager == null || savedData == null) {
            return;
        }

        List<UUID> objectsToLoad = savedData.getAllObjectEntries().stream()
                .filter(entry -> isObjectInChunk(entry.getValue(), chunkPos))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (objectsToLoad.isEmpty()) return;

        List<UUID> constraintsToLoad = savedData.getAllJointEntries().stream()
                .filter(entry -> isJointAssociatedWithBodies(entry.getValue(), objectsToLoad, objectManager))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        PhysicsObjectLoader loader = objectManager.getObjectLoader();

        List<CompletableFuture<IPhysicsObject>> objectFutures = objectsToLoad.stream()
                .map(id -> loader.scheduleObjectLoad(id, false))
                .collect(Collectors.toList());

        CompletableFuture<Void> allObjectsLoaded = CompletableFuture.allOf(objectFutures.toArray(new CompletableFuture[0]));

        CompletableFuture<Void> allConstraintsLoaded = allObjectsLoaded.thenComposeAsync(v -> {
            if (constraintsToLoad.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            List<CompletableFuture<IConstraint>> constraintFutures = constraintsToLoad.stream()
                    .map(constraintManager::getOrLoadConstraint)
                    .collect(Collectors.toList());
            return CompletableFuture.allOf(constraintFutures.toArray(new CompletableFuture[0]));
        }, level.getServer());

        allConstraintsLoaded.thenRunAsync(() -> {
            for (CompletableFuture<IPhysicsObject> future : objectFutures) {
                IPhysicsObject obj = future.getNow(null);
                if (obj != null && obj.getBodyId() != 0) {
                    world.queueCommand(new ActivateBodyCommand(obj.getBodyId()));
                }
            }
        }, level.getServer());
    }

    private static boolean isObjectInChunk(CompoundTag objTag, ChunkPos chunkPos) {
        if (objTag.contains("transform", 10)) {
            CompoundTag transformTag = objTag.getCompound("transform");
            if (transformTag.contains("pos", 9)) {
                ListTag posList = transformTag.getList("pos", Tag.TAG_DOUBLE);
                if (posList.size() == 3) {
                    double x = posList.getDouble(0);
                    double z = posList.getDouble(2);
                    return ((int) Math.floor(x / 16.0)) == chunkPos.x &&
                            ((int) Math.floor(z / 16.0)) == chunkPos.z;
                }
            }
        }
        return false;
    }

    private static boolean isJointAssociatedWithBodies(CompoundTag jointTag, List<UUID> bodyIds, PhysicsObjectManager objectManager) {
        if (!jointTag.hasUUID("bodyId1")) return false;
        UUID body1 = jointTag.getUUID("bodyId1");
        if (bodyIds.contains(body1)) return true;
        if (jointTag.hasUUID("bodyId2")) {
            UUID body2 = jointTag.getUUID("bodyId2");
            return bodyIds.contains(body2);
        }
        return false;
    }
}