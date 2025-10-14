/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.raycasting;

import com.github.stephengold.joltjni.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Optional;

/**
 * Extended HitResult class to include additional information for raycasting.
 *
 * @author xI-Mx-Ix
 */
public class VxHitResult extends HitResult {

    private final HitResult minecraftHit;
    private final PhysicsHit physicsHit;

    public VxHitResult(HitResult minecraftHit) {
        super(minecraftHit.getLocation());
        this.minecraftHit = minecraftHit;
        this.physicsHit = null;
    }

    public VxHitResult(net.minecraft.world.phys.Vec3 location, int bodyId, Vec3 hitNormal, float hitFraction) {
        super(location);
        this.minecraftHit = null;
        this.physicsHit = new PhysicsHit(bodyId, hitNormal, hitFraction);
    }

    @Override
    public Type getType() {
        if (isPhysicsHit()) {
            return Type.BLOCK;
        }
        return minecraftHit != null ? minecraftHit.getType() : Type.MISS;
    }

    public boolean isPhysicsHit() {
        return physicsHit != null;
    }

    public Optional<PhysicsHit> getPhysicsHit() {
        return Optional.ofNullable(physicsHit);
    }

    public Optional<BlockHitResult> getBlockHit() {
        if (minecraftHit instanceof BlockHitResult blockHit) {
            return Optional.of(blockHit);
        }
        return Optional.empty();
    }

    public Optional<EntityHitResult> getEntityHit() {
        if (minecraftHit instanceof EntityHitResult entityHit) {
            return Optional.of(entityHit);
        }
        return Optional.empty();
    }

    public record PhysicsHit(int bodyId, Vec3 hitNormal, float hitFraction) {}
}
