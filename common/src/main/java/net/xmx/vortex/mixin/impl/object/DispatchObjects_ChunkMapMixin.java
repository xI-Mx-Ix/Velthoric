package net.xmx.vortex.mixin.impl.object;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.xmx.vortex.physics.object.physicsobject.IPhysicsObject;
import net.xmx.vortex.physics.object.physicsobject.manager.VxObjectManager;
import net.xmx.vortex.physics.object.physicsobject.manager.VxObjectNetworkDispatcher;
import net.xmx.vortex.physics.world.VxPhysicsWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;
import java.util.function.BooleanSupplier;

@Mixin(ChunkMap.class)
public abstract class DispatchObjects_ChunkMapMixin {

    @Shadow @Final
    ServerLevel level;

    @Unique
    private final Map<UUID, Set<IPhysicsObject>> vortex$playerVisibleObjects = new HashMap<>();
    @Unique
    private final Long2ObjectMap<List<IPhysicsObject>> vortex$objectsByChunk = new Long2ObjectOpenHashMap<>();

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("TAIL"))
    private void vortex$onTick(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        VxPhysicsWorld world = VxPhysicsWorld.get(this.level.dimension());
        if (world == null) return;

        VxObjectManager manager = world.getObjectManager();
        if (manager == null || (manager.getObjectContainer().getAllObjects().isEmpty() && this.level.players().isEmpty())) {
            return;
        }

        vortex$updatePhysicsObjectTracking(manager);
    }

    @Unique
    private void vortex$updatePhysicsObjectTracking(VxObjectManager manager) {
        VxObjectNetworkDispatcher dispatcher = manager.getNetworkDispatcher();
        List<ServerPlayer> players = this.level.players();

        vortex$playerVisibleObjects.clear();
        for (ServerPlayer player : players) {
            vortex$playerVisibleObjects.put(player.getUUID(), new HashSet<>());
        }

        vortex$objectsByChunk.clear();
        for (IPhysicsObject obj : manager.getObjectContainer().getAllObjects()) {
            if (!obj.isRemoved()) {
                long chunkKey = VxObjectManager.getObjectChunkPos(obj).toLong();
                vortex$objectsByChunk.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(obj);
            }
        }

        if (vortex$objectsByChunk.isEmpty()) {

            for (ServerPlayer player : players) {
                dispatcher.updatePlayerTracking(player, Collections.emptySet());
            }
            return;
        }

        for (ServerPlayer player : players) {
            Set<IPhysicsObject> visibleSet = vortex$playerVisibleObjects.get(player.getUUID());
            if (visibleSet == null) continue;

            int viewDistance = player.server.getPlayerList().getViewDistance();
            ChunkPos playerChunkPos = player.chunkPosition();

            for (int cz = playerChunkPos.z - viewDistance; cz <= playerChunkPos.z + viewDistance; ++cz) {
                for (int cx = playerChunkPos.x - viewDistance; cx <= playerChunkPos.x + viewDistance; ++cx) {
                    long chunkKey = ChunkPos.asLong(cx, cz);
                    List<IPhysicsObject> objectsInChunk = vortex$objectsByChunk.get(chunkKey);
                    if (objectsInChunk != null) {
                        visibleSet.addAll(objectsInChunk);
                    }
                }
            }
        }

        for (ServerPlayer player : players) {
            Set<IPhysicsObject> visibleSet = vortex$playerVisibleObjects.get(player.getUUID());
            dispatcher.updatePlayerTracking(player, visibleSet != null ? visibleSet : Collections.emptySet());
        }
    }
}