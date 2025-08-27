package net.xmx.velthoric.physics.raycasting;

import com.github.stephengold.joltjni.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Optional;

public class VxHitResult extends HitResult {

    private final HitResult minecraftHit;
    private final PhysicsHit physicsHit;
    private final SeatHit seatHit;

    public VxHitResult(HitResult minecraftHit) {
        super(minecraftHit.getLocation());
        this.minecraftHit = minecraftHit;
        this.physicsHit = null;
        this.seatHit = null;
    }

    public VxHitResult(net.minecraft.world.phys.Vec3 location, int bodyId, Vec3 hitNormal, float hitFraction) {
        super(location);
        this.minecraftHit = null;
        this.physicsHit = new PhysicsHit(bodyId, hitNormal, hitFraction);
        this.seatHit = null;
    }

    public VxHitResult(net.minecraft.world.phys.Vec3 location, int bodyId, String seatName, Vec3 hitNormal, float hitFraction) {
        super(location);
        this.minecraftHit = null;
        this.physicsHit = new PhysicsHit(bodyId, hitNormal, hitFraction);
        this.seatHit = new SeatHit(seatName);
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

    public boolean isSeatHit() {
        return seatHit != null;
    }

    public Optional<PhysicsHit> getPhysicsHit() {
        return Optional.ofNullable(physicsHit);
    }

    public Optional<SeatHit> getSeatHit() {
        return Optional.ofNullable(seatHit);
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
    public record SeatHit(String seatName) {}
}