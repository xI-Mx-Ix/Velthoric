package net.xmx.vortex.physics.object.raycast.click;

import com.github.stephengold.joltjni.Vec3;
import net.minecraft.server.level.ServerPlayer;

public interface Clickable {
    void onLeftClick(ServerPlayer player, Vec3 hitPoint, Vec3 hitNormal);
    void onRightClick(ServerPlayer player, Vec3 hitPoint, Vec3 hitNormal);
}