package net.xmx.vortex.physics.object.riding;

import com.github.stephengold.joltjni.Vec3;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public interface Rideable {

    Vec3 getRidePositionOffset();

    void setRidePositionOffset(Vec3 offset);

    void onStartRiding(ServerPlayer player, RidingProxyEntity proxy);

    void onStopRiding(ServerPlayer player);

    @Nullable
    RidingProxyEntity getRidingProxy();

    void setRidingProxy(@Nullable RidingProxyEntity proxy);
}