package net.xmx.xbullet.physics.object.raycast.info;

import net.minecraft.world.phys.HitResult;

public final class MinecraftHitInfo {
    private final HitResult hitResult;
    private final float hitFraction;

    public MinecraftHitInfo(HitResult hitResult, float hitFraction) {
        this.hitResult = hitResult;
        this.hitFraction = hitFraction;
    }

    public HitResult getHitResult() {
        return hitResult;
    }

    public float getHitFraction() {
        return hitFraction;
    }
}