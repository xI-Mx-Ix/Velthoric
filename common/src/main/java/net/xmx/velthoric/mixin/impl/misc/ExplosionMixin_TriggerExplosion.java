package net.xmx.velthoric.mixin.impl.misc;

import com.github.stephengold.joltjni.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.xmx.velthoric.physics.object.util.VxExplosionUtil;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Explosion.class)
public abstract class ExplosionMixin_TriggerExplosion {

    @Unique
    private final VxExplosionUtil velthoric$explosionUtil = new VxExplosionUtil();

    @Shadow @Final private Level level;
    @Shadow @Final private double x;
    @Shadow @Final private double y;
    @Shadow @Final private double z;
    @Shadow public float radius;

    @Inject(method = "explode", at = @At("TAIL"))
    private void velthoric_onExplode(CallbackInfo ci) {
        if (this.level instanceof ServerLevel) {
            VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(this.level.dimension());
            if (physicsWorld != null && physicsWorld.isRunning()) {

                float physicsRadius = this.radius * 2.5f;

                float explosionStrength = physicsRadius * physicsRadius * 100.0f;

                Vec3 explosionCenter = new Vec3((float)this.x, (float)this.y, (float)this.z);

                velthoric$explosionUtil.applyExplosion(physicsWorld, explosionCenter, physicsRadius, explosionStrength);
            }
        }
    }
}