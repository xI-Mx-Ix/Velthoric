package net.xmx.velthoric.mixin.impl.object;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.object.physicsobject.VxAbstractBody;
import net.xmx.velthoric.physics.object.physicsobject.manager.VxObjectManager;
import net.xmx.velthoric.physics.object.physicsobject.manager.VxObjectNetworkDispatcher;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
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
    private ServerLevel level;

    @Unique
    private final Map<UUID, Set<VxAbstractBody>> velthoric$playerVisibleObjects = new HashMap<>();
    @Unique
    private final Long2ObjectMap<List<VxAbstractBody>> velthoric$objectsByChunk = new Long2ObjectOpenHashMap<>();

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("TAIL"))
    private void velthoric$onTick(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        VxPhysicsWorld world = VxPhysicsWorld.get(this.level.dimension());
        if (world == null) return;

        VxObjectManager manager = world.getObjectManager();
        if (manager == null || (manager.getObjectContainer().getAllObjects().isEmpty() && this.level.players().isEmpty())) {
            return;
        }

        velthoric$updatePhysicsObjectTracking(manager);
    }

    @Unique
    private ChunkPos velthoric$getObjectChunkPos(VxAbstractBody obj) {
        VxTransform transform = obj.getGameTransform();
        return new ChunkPos(
                SectionPos.posToSectionCoord(transform.getTranslation().x()),
                SectionPos.posToSectionCoord(transform.getTranslation().z())
        );
    }

    @Unique
    private void velthoric$updatePhysicsObjectTracking(VxObjectManager manager) {
        VxObjectNetworkDispatcher dispatcher = manager.getNetworkDispatcher();
        List<ServerPlayer> players = this.level.players();

        velthoric$playerVisibleObjects.clear();
        for (ServerPlayer player : players) {
            velthoric$playerVisibleObjects.put(player.getUUID(), new HashSet<>());
        }

        velthoric$objectsByChunk.clear();
        for (VxAbstractBody obj : manager.getObjectContainer().getAllObjects()) {
            if (obj.getBodyId() != 0) {
                long chunkKey = velthoric$getObjectChunkPos(obj).toLong();
                velthoric$objectsByChunk.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(obj);
            }
        }

        if (velthoric$objectsByChunk.isEmpty()) {
            for (ServerPlayer player : players) {
                dispatcher.updatePlayerTracking(player, Collections.emptySet());
            }
            return;
        }

        for (ServerPlayer player : players) {
            Set<VxAbstractBody> visibleSet = velthoric$playerVisibleObjects.get(player.getUUID());
            if (visibleSet == null) continue;

            int viewDistance = player.server.getPlayerList().getViewDistance();
            ChunkPos playerChunkPos = player.chunkPosition();

            for (int cz = playerChunkPos.z - viewDistance; cz <= playerChunkPos.z + viewDistance; ++cz) {
                for (int cx = playerChunkPos.x - viewDistance; cx <= playerChunkPos.x + viewDistance; ++cx) {
                    long chunkKey = ChunkPos.asLong(cx, cz);
                    List<VxAbstractBody> objectsInChunk = velthoric$objectsByChunk.get(chunkKey);
                    if (objectsInChunk != null) {
                        visibleSet.addAll(objectsInChunk);
                    }
                }
            }
        }

        for (ServerPlayer player : players) {
            Set<VxAbstractBody> visibleSet = velthoric$playerVisibleObjects.get(player.getUUID());
            dispatcher.updatePlayerTracking(player, visibleSet != null ? visibleSet : Collections.emptySet());
        }
    }
}