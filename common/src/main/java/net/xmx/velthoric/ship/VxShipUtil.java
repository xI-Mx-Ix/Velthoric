/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.ship;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.ship.body.VxShipBody;
import net.xmx.velthoric.ship.plot.ShipPlotInfo;
import net.xmx.velthoric.ship.plot.VxClientPlotManager;
import net.xmx.velthoric.ship.plot.VxPlotManager;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.UUID;
import java.util.function.Supplier;

public class VxShipUtil {

    public static Vec3 worldToShip(Vec3 worldVec, VxShipBody ship) {
        VxTransform transform = ship.getGameTransform();
        RVec3 shipPos = transform.getTranslation();
        Quat shipRot = transform.getRotation();
        BlockPos plotOrigin = ship.getPlotCenter().getWorldPosition();

        Vector3d relativeVec = new Vector3d(worldVec.x() - shipPos.x(), worldVec.y() - shipPos.y(), worldVec.z() - shipPos.z());
        new Quaterniond(shipRot.getX(), shipRot.getY(), shipRot.getZ(), shipRot.getW()).invert().transform(relativeVec);
        return new Vec3(relativeVec.x + plotOrigin.getX(), relativeVec.y + plotOrigin.getY(), relativeVec.z + plotOrigin.getZ());
    }

    public static Vec3 worldToShip(Vec3 worldVec, RVec3 shipPosition, Quat shipRotation, BlockPos plotOrigin) {
        Vector3d relativeVec = new Vector3d(worldVec.x() - shipPosition.x(), worldVec.y() - shipPosition.y(), worldVec.z() - shipPosition.z());
        new Quaterniond(shipRotation.getX(), shipRotation.getY(), shipRotation.getZ(), shipRotation.getW()).invert().transform(relativeVec);
        return new Vec3(relativeVec.x + plotOrigin.getX(), relativeVec.y + plotOrigin.getY(), relativeVec.z + plotOrigin.getZ());
    }

    public static Vec3 shipToWorld(Vec3 shipLocalVec, RVec3 shipPosition, Quat shipRotation, BlockPos plotOrigin) {
        Vector3d relativeVec = new Vector3d(shipLocalVec.x() - plotOrigin.getX(), shipLocalVec.y() - plotOrigin.getY(), shipLocalVec.z() - plotOrigin.getZ());
        new Quaterniond(shipRotation.getX(), shipRotation.getY(), shipRotation.getZ(), shipRotation.getW()).transform(relativeVec);
        return new Vec3(relativeVec.x + shipPosition.x(), relativeVec.y + shipPosition.y(), relativeVec.z + shipPosition.z());
    }

    public static VxShipBody getShipManagingPosition(ServerLevel serverLevel, Vec3 pos) {
        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(serverLevel.dimension());
        if (physicsWorld != null && physicsWorld.getPlotManager() != null) {
            ChunkPos chunkPos = new ChunkPos(BlockPos.containing(pos.x, pos.y, pos.z));
            return physicsWorld.getPlotManager().getShipManaging(chunkPos);
        }
        return null;
    }

    public static boolean isOnShip(Level level, Vec3 pos) {
        if (level.isClientSide()) {
            VxClientPlotManager plotManager = VxClientPlotManager.getInstance();
            ChunkPos chunkPos = new ChunkPos(BlockPos.containing(pos.x, pos.y, pos.z));
            return plotManager.getShipInfoForChunk(chunkPos) != null;
        }
        return false;
    }

    public static BlockHitResult clipIncludeShips(Level level, ClipContext context) {

        boolean startOnShip = isOnShip(level, context.getFrom());
        boolean endOnShip = isOnShip(level, context.getTo());

        if (startOnShip != endOnShip) {
            Vec3 vec3 = context.getFrom().subtract(context.getTo());
            return BlockHitResult.miss(
                    context.getTo(),
                    Direction.getNearest(vec3.x, vec3.y, vec3.z),
                    BlockPos.containing(context.getTo())
            );
        }

        BlockHitResult vanillaHit = clipVanilla(level, context, context.getFrom(), context.getTo());
        BlockHitResult closestHit = vanillaHit;
        double closestDistSq = vanillaHit.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : context.getFrom().distanceToSqr(vanillaHit.getLocation());

        if (level.isClientSide()) {
            VxClientObjectManager objectManager = VxClientObjectManager.getInstance();
            VxClientPlotManager plotManager = VxClientPlotManager.getInstance();

            float partialTick = Minecraft.getInstance().getFrameTime();
            RVec3 shipPos = new RVec3();
            Quat shipRot = new Quat();

            for (UUID shipId : plotManager.getAllShipIds()) {
                var index = objectManager.getStore().getIndexForId(shipId);
                if (index == null) continue;

                objectManager.getInterpolator().interpolateFrame(objectManager.getStore(), index, partialTick, shipPos, shipRot);

                ShipPlotInfo plotInfo = plotManager.getShipInfoForShip(shipId);
                if (plotInfo == null) continue;
                BlockPos plotOrigin = plotInfo.plotCenter().getWorldPosition();

                Vec3 fromLocal = worldToShip(context.getFrom(), shipPos, shipRot, plotOrigin);
                Vec3 toLocal = worldToShip(context.getTo(), shipPos, shipRot, plotOrigin);

                BlockHitResult localHit = clipVanilla(level, context, fromLocal, toLocal);

                if (localHit.getType() == HitResult.Type.BLOCK) {
                    Vec3 hitLocationWorld = shipToWorld(localHit.getLocation(), shipPos, shipRot, plotOrigin);
                    double distSq = context.getFrom().distanceToSqr(hitLocationWorld);

                    if (distSq < closestDistSq) {
                        closestDistSq = distSq;

                        closestHit = new BlockHitResult(
                                hitLocationWorld,
                                localHit.getDirection(),
                                localHit.getBlockPos(),
                                localHit.isInside()
                        );
                    }
                }
            }
        } else {

            ServerLevel serverLevel = (ServerLevel) level;
            VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(serverLevel.dimension());
            if (physicsWorld != null && physicsWorld.getPlotManager() != null) {
                VxPlotManager plotManager = physicsWorld.getPlotManager();

                for (VxShipBody ship : plotManager.getUniqueShips()) {
                    VxTransform transform = ship.getGameTransform();
                    RVec3 shipPos = transform.getTranslation();
                    Quat shipRot = transform.getRotation();
                    BlockPos plotOrigin = ship.getPlotCenter().getWorldPosition();

                    Vec3 fromLocal = worldToShip(context.getFrom(), shipPos, shipRot, plotOrigin);
                    Vec3 toLocal = worldToShip(context.getTo(), shipPos, shipRot, plotOrigin);

                    BlockHitResult localHit = clipVanilla(level, context, fromLocal, toLocal);

                    if (localHit.getType() == HitResult.Type.BLOCK) {
                        Vec3 hitLocationWorld = shipToWorld(localHit.getLocation(), shipPos, shipRot, plotOrigin);
                        double distSq = context.getFrom().distanceToSqr(hitLocationWorld);

                        if (distSq < closestDistSq) {
                            closestDistSq = distSq;

                            Direction localDir = localHit.getDirection();
                            Vector3d worldDirVec = new Vector3d(localDir.getStepX(), localDir.getStepY(), localDir.getStepZ());
                            new Quaterniond(shipRot.getX(), shipRot.getY(), shipRot.getZ(), shipRot.getW()).transform(worldDirVec);
                            Direction worldDir = Direction.getNearest(worldDirVec.x, worldDirVec.y, worldDirVec.z);

                            closestHit = new BlockHitResult(
                                    hitLocationWorld,
                                    worldDir,
                                    localHit.getBlockPos(),
                                    localHit.isInside()
                            );
                        }
                    }
                }
            }
        }

        return closestHit;
    }

    private static BlockHitResult clipVanilla(BlockGetter blockGetter, ClipContext context, Vec3 from, Vec3 to) {
        return BlockGetter.traverseBlocks(from, to, context, (ctx, pos) -> {
            BlockState blockState = blockGetter.getBlockState(pos);
            FluidState fluidState = blockGetter.getFluidState(pos);

            VoxelShape blockShape = ctx.getBlockShape(blockState, blockGetter, pos);
            BlockHitResult blockHit = blockGetter.clipWithInteractionOverride(from, to, pos, blockShape, blockState);

            VoxelShape fluidShape = ctx.getFluidShape(fluidState, blockGetter, pos);
            BlockHitResult fluidHit = fluidShape.clip(from, to, pos);

            double blockDist = blockHit == null ? Double.MAX_VALUE : from.distanceToSqr(blockHit.getLocation());
            double fluidDist = fluidHit == null ? Double.MAX_VALUE : from.distanceToSqr(fluidHit.getLocation());

            return blockDist <= fluidDist ? blockHit : fluidHit;
        }, (ctx) -> {
            Vec3 vec3 = from.subtract(to);
            return BlockHitResult.miss(to, Direction.getNearest(vec3.x, vec3.y, vec3.z), BlockPos.containing(to));
        });
    }

    public static Vec3 getTruePosition(Entity entity) {
        if (entity.level().isClientSide()) {
            VxClientPlotManager plotManager = VxClientPlotManager.getInstance();
            ShipPlotInfo plotInfo = plotManager.getShipInfoForChunk(entity.chunkPosition());

            if (plotInfo != null) {
                VxClientObjectManager objectManager = VxClientObjectManager.getInstance();
                UUID shipId = plotInfo.shipId();
                var index = objectManager.getStore().getIndexForId(shipId);
                if (index != null) {
                    RVec3 shipPos = new RVec3();
                    Quat shipRot = new Quat();
                    float partialTick = Minecraft.getInstance().isSameThread() ? Minecraft.getInstance().getFrameTime() : 0.0f;
                    objectManager.getInterpolator().interpolateFrame(objectManager.getStore(), index, partialTick, shipPos, shipRot);

                    BlockPos plotOrigin = plotInfo.plotCenter().getWorldPosition();
                    return shipToWorld(entity.position(), shipPos, shipRot, plotOrigin);
                }
            }
        } else if (entity.level() instanceof ServerLevel serverLevel) {
            VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(serverLevel.dimension());
            if (physicsWorld != null) {
                VxPlotManager plotManager = physicsWorld.getPlotManager();
                if (plotManager != null) {
                    VxShipBody ship = plotManager.getShipManaging(entity.chunkPosition());
                    if (ship != null) {
                        VxTransform transform = ship.getGameTransform();
                        RVec3 shipPos = transform.getTranslation();
                        Quat shipRot = transform.getRotation();
                        BlockPos plotOrigin = ship.getPlotCenter().getWorldPosition();
                        return shipToWorld(entity.position(), shipPos, shipRot, plotOrigin);
                    }
                }
            }
        }
        return entity.position();
    }

    public static Vec3 getTruePosition(Level level, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        ChunkPos chunkPos = new ChunkPos(pos);

        if (level.isClientSide()) {
            VxClientPlotManager plotManager = VxClientPlotManager.getInstance();
            ShipPlotInfo plotInfo = plotManager.getShipInfoForChunk(chunkPos);

            if (plotInfo != null) {
                VxClientObjectManager objectManager = VxClientObjectManager.getInstance();
                UUID shipId = plotInfo.shipId();
                var index = objectManager.getStore().getIndexForId(shipId);
                if (index != null) {
                    RVec3 shipPos = new RVec3();
                    Quat shipRot = new Quat();
                    float partialTick = Minecraft.getInstance().isSameThread() ? Minecraft.getInstance().getFrameTime() : 0.0f;
                    objectManager.getInterpolator().interpolateFrame(objectManager.getStore(), index, partialTick, shipPos, shipRot);

                    BlockPos plotOrigin = plotInfo.plotCenter().getWorldPosition();
                    return shipToWorld(new Vec3(x, y, z), shipPos, shipRot, plotOrigin);
                }
            }
        } else if (level instanceof ServerLevel serverLevel) {
            VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(serverLevel.dimension());
            if (physicsWorld != null && physicsWorld.getPlotManager() != null) {
                VxShipBody ship = physicsWorld.getPlotManager().getShipManaging(chunkPos);
                if (ship != null) {
                    VxTransform transform = ship.getGameTransform();
                    RVec3 shipPos = transform.getTranslation();
                    Quat shipRot = transform.getRotation();
                    BlockPos plotOrigin = ship.getPlotCenter().getWorldPosition();
                    return shipToWorld(new Vec3(x, y, z), shipPos, shipRot, plotOrigin);
                }
            }
        }
        return new Vec3(x, y, z);
    }

    public static double sqrdShips(Entity entity, double x, double y, double z) {
        Vec3 entityEyePos = entity.getEyePosition();
        Vec3 trueEntityEyePos = getTruePosition(entity.level(), entityEyePos.x(), entityEyePos.y(), entityEyePos.z());
        Vec3 trueTargetPos = getTruePosition(entity.level(), x, y, z);
        return trueEntityEyePos.distanceToSqr(trueTargetPos);
    }

    public static double sqrdShips(Entity entity, Entity otherEntity) {
        Vec3 entityTruePos = getTruePosition(entity);
        Vec3 otherTruePos = getTruePosition(otherEntity);
        return entityTruePos.distanceToSqr(otherTruePos);
    }

    public static <T> T transformPlayerTemporarily(ServerPlayer player, Level level, BlockPos interactionPos, Supplier<T> action) {
        if (level.isClientSide()) {
            return action.get();
        }

        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(level.dimension());
        if (physicsWorld == null || physicsWorld.getPlotManager() == null) {
            return action.get();
        }

        VxShipBody ship = physicsWorld.getPlotManager().getShipManaging(new ChunkPos(interactionPos));
        if (ship == null) {
            return action.get();
        }

        double oldX = player.getX();
        double oldY = player.getY();
        double oldZ = player.getZ();
        float oldYRot = player.getYRot();
        float oldXRot = player.getXRot();

        try {

            Vec3 effectivePos = worldToShip(player.position(), ship);
            player.setPos(effectivePos.x, effectivePos.y, effectivePos.z);

            Quat shipRot = ship.getGameTransform().getRotation();
            Quaterniond shipRotInv = new Quaterniond(shipRot.getX(), shipRot.getY(), shipRot.getZ(), shipRot.getW()).invert();

            Vec3 lookAngle = player.getLookAngle();
            Vector3d lookAngleLocal = new Vector3d(lookAngle.x, lookAngle.y, lookAngle.z);
            shipRotInv.transform(lookAngleLocal);

            float newXRot = (float) Math.toDegrees(Math.asin(-lookAngleLocal.y));
            float newYRot = (float) Math.toDegrees(Math.atan2(-lookAngleLocal.x, lookAngleLocal.z));

            player.setYRot(newYRot);
            player.setXRot(newXRot);

            return action.get();
        } finally {

            player.setPos(oldX, oldY, oldZ);
            player.setYRot(oldYRot);
            player.setXRot(oldXRot);
        }
    }
}