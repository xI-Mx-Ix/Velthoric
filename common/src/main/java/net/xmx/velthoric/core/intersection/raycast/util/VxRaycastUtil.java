/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.intersection.raycast.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Utility class for Minecraft-specific raycasting (Blocks and Entities).
 * <p>
 * This class is decoupled from the Jolt physics raycaster to maintain a clean separation
 * between vanilla world queries and physics engine queries.
 *
 * @author xI-Mx-Ix
 */
public final class VxRaycastUtil {

    private VxRaycastUtil() {
        // Prevent instantiation
    }

    /**
     * Performs a standard Minecraft block raycast.
     *
     * @param level  The level to cast in.
     * @param start  Start position.
     * @param end    End position.
     * @param block  Block collision mode.
     * @param fluid  Fluid collision mode.
     * @param entity The context entity (usually the player).
     * @return An Optional containing the BlockHitResult if something was hit.
     */
    public static Optional<BlockHitResult> raycastBlocks(Level level, Vec3 start, Vec3 end, ClipContext.Block block, ClipContext.Fluid fluid, Entity entity) {
        ClipContext context = new ClipContext(start, end, block, fluid, entity);
        BlockHitResult result = level.clip(context);
        return result.getType() != HitResult.Type.MISS ? Optional.of(result) : Optional.empty();
    }

    /**
     * Performs an entity raycast using Minecraft's ProjectileUtil.
     *
     * @param level           The level to cast in.
     * @param entity          The searching entity.
     * @param start           Start position.
     * @param end             End position.
     * @param boundingBox     The search area.
     * @param entityPredicate Predicate to filter entities.
     * @return An Optional containing the EntityHitResult.
     */
    public static Optional<EntityHitResult> raycastEntities(Level level, Entity entity, Vec3 start, Vec3 end, AABB boundingBox, Predicate<Entity> entityPredicate) {
        EntityHitResult result = ProjectileUtil.getEntityHitResult(level, entity, start, end, boundingBox, entityPredicate);
        return result != null ? Optional.of(result) : Optional.empty();
    }
}