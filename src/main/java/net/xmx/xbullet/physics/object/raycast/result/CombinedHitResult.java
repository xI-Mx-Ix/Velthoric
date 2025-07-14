package net.xmx.xbullet.physics.object.raycast.result;

import net.xmx.xbullet.physics.object.raycast.info.MinecraftHitInfo;
import net.xmx.xbullet.physics.object.raycast.info.PhysicsHitInfo;

import java.util.Optional;

public final class CombinedHitResult {
    private final PhysicsHitInfo physicsHit;
    private final MinecraftHitInfo minecraftHit;

    public CombinedHitResult(PhysicsHitInfo physicsHit) {
        this.physicsHit = physicsHit;
        this.minecraftHit = null;
    }

    public CombinedHitResult(MinecraftHitInfo minecraftHit) {
        this.physicsHit = null;
        this.minecraftHit = minecraftHit;
    }

    public boolean isPhysicsHit() {
        return physicsHit != null;
    }

    public boolean isMinecraftHit() {
        return minecraftHit != null;
    }

    public Optional<PhysicsHitInfo> getPhysicsHit() {
        return Optional.ofNullable(physicsHit);
    }

    public Optional<MinecraftHitInfo> getMinecraftHit() {
        return Optional.ofNullable(minecraftHit);
    }
}