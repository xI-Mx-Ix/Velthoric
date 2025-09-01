package net.xmx.velthoric.mixin.impl.ship.chunk;

import com.github.stephengold.joltjni.RVec3;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.ship.VxStructureBody;
import net.xmx.velthoric.ship.plot.VxPlot;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {

    @Shadow @Final ServerLevel level;
    @Shadow int viewDistance;

    @Shadow protected abstract void updateChunkTracking(ServerPlayer player, ChunkPos pos, MutableObject<?> packetCache, boolean wasInRange, boolean isInRange);

    @Unique
    private final Map<UUID, Integer> velthoric$playerVirtualPlotTracker = new HashMap<>();

    @Inject(method = "move", at = @At("TAIL"))
    private void velthoric_onPlayerMove(ServerPlayer player, CallbackInfo ci) {
        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(this.level.dimension());
        if (physicsWorld == null) return;

        UUID playerUUID = player.getUUID();
        int previousPlotId = velthoric$playerVirtualPlotTracker.getOrDefault(playerUUID, -1);
        int currentPlotId = -1;

        Optional<VxStructureBody> closestShip = physicsWorld.getObjectManager().getObjectContainer().getAllObjects().stream()
                .filter(body -> body instanceof VxStructureBody)
                .map(body -> (VxStructureBody) body)
                .min(Comparator.comparingDouble(ship -> {
                    RVec3 shipPos = ship.getGameTransform().getTranslation();
                    net.minecraft.world.phys.Vec3 playerPos = player.position();
                    double dx = playerPos.x() - shipPos.xx();
                    double dy = playerPos.y() - shipPos.yy();
                    double dz = playerPos.z() - shipPos.zz();
                    return dx * dx + dy * dy + dz * dz;
                }));

        if (closestShip.isPresent()) {
            VxStructureBody ship = closestShip.get();
            RVec3 shipPos = ship.getGameTransform().getTranslation();
            net.minecraft.world.phys.Vec3 playerPos = player.position();
            double dx = playerPos.x() - shipPos.xx();
            double dy = playerPos.y() - shipPos.yy();
            double dz = playerPos.z() - shipPos.zz();
            double distanceSq = dx * dx + dy * dy + dz * dz;

            double trackingDistance = this.viewDistance * 16 + 128;
            if (distanceSq < trackingDistance * trackingDistance) {
                VxPlot plot = ship.getPlot();
                if (plot != null) {
                    currentPlotId = plot.getId();
                }
            }
        }

        if (previousPlotId != currentPlotId) {

            if (previousPlotId != -1) {
                VxPlot oldPlot = physicsWorld.getPlotManager().getPlotById(previousPlotId);
                if (oldPlot != null) {
                    velthoric$updateVirtualTracking(player, oldPlot.getBounds(), false);
                }
            }

            if (currentPlotId != -1) {
                VxPlot newPlot = physicsWorld.getPlotManager().getPlotById(currentPlotId);
                if (newPlot != null) {
                    velthoric$updateVirtualTracking(player, newPlot.getBounds(), true);
                }
            }

            if (currentPlotId != -1) {
                velthoric$playerVirtualPlotTracker.put(playerUUID, currentPlotId);
            } else {
                velthoric$playerVirtualPlotTracker.remove(playerUUID);
            }
        }
    }

    @Unique
    private void velthoric$updateVirtualTracking(ServerPlayer player, BoundingBox plotBox, boolean track) {

        int virtualPlayerChunkX = (plotBox.minX() + plotBox.maxX()) / 2 >> 4;
        int virtualPlayerChunkZ = (plotBox.minZ() + plotBox.maxZ()) / 2 >> 4;

        for (int x = virtualPlayerChunkX - viewDistance; x <= virtualPlayerChunkX + viewDistance; x++) {
            for (int z = virtualPlayerChunkZ - viewDistance; z <= virtualPlayerChunkZ + viewDistance; z++) {
                if (ChunkMap.isChunkInRange(x, z, virtualPlayerChunkX, virtualPlayerChunkZ, viewDistance)) {

                    updateChunkTracking(player, new ChunkPos(x, z), new MutableObject<>(), !track, track);
                }
            }
        }
    }
}