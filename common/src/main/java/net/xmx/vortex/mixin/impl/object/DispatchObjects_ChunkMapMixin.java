package net.xmx.vortex.mixin.impl.object;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
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

    @Shadow
    private volatile Long2ObjectLinkedOpenHashMap<ChunkHolder> visibleChunkMap;

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("TAIL"))
    private void vortex$onTick(BooleanSupplier hasTimeLeft, CallbackInfo ci) {

        VxPhysicsWorld world = VxPhysicsWorld.get(this.level.dimension());
        if (world == null || world.getObjectManager() == null) {
            return;
        }

        VxObjectManager manager = world.getObjectManager();
        if (manager.getObjectContainer().getAllObjects().isEmpty() && this.level.players().isEmpty()) {
            return;
        }

        vortex$updatePhysicsObjectTracking(manager);
    }

    @Unique
    private void vortex$updatePhysicsObjectTracking(VxObjectManager manager) {
        VxObjectNetworkDispatcher dispatcher = manager.getNetworkDispatcher();
        ChunkMap self = (ChunkMap) (Object) this;

        Long2ObjectMap<List<IPhysicsObject>> objectsByChunk = new Long2ObjectOpenHashMap<>();
        for (IPhysicsObject obj : manager.getObjectContainer().getAllObjects()) {
            if (!obj.isRemoved()) {
                long chunkKey = VxObjectManager.getObjectChunkPos(obj).toLong();
                objectsByChunk.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(obj);
            }
        }

        Map<UUID, Set<IPhysicsObject>> playerVisibleObjects = new HashMap<>();
        for (ServerPlayer player : this.level.players()) {
            playerVisibleObjects.put(player.getUUID(), new HashSet<>());
        }

        for (Long2ObjectMap.Entry<ChunkHolder> entry : this.visibleChunkMap.long2ObjectEntrySet()) {
            long chunkPosLong = entry.getLongKey();
            List<IPhysicsObject> objectsInChunk = objectsByChunk.get(chunkPosLong);

            if (objectsInChunk == null || objectsInChunk.isEmpty()) {
                continue;
            }

            ChunkPos chunkPos = new ChunkPos(chunkPosLong);
            List<ServerPlayer> playersWatching = self.getPlayers(chunkPos, false);

            for (ServerPlayer player : playersWatching) {
                Set<IPhysicsObject> visibleSet = playerVisibleObjects.get(player.getUUID());
                if (visibleSet != null) {
                    visibleSet.addAll(objectsInChunk);
                }
            }
        }

        for (ServerPlayer player : this.level.players()) {
            Set<IPhysicsObject> visibleSet = playerVisibleObjects.get(player.getUUID());
            if (visibleSet != null) {
                dispatcher.updatePlayerTracking(player, visibleSet);
            }
        }
    }
}