package net.xmx.xbullet.physics.object.riding;

import net.xmx.xbullet.math.PhysicsTransform;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ClientRidingCache {

    public record RidingInfo(UUID physicsObjectId, PhysicsTransform relativeSeatTransform) {}

    private static final ConcurrentMap<UUID, RidingInfo> RIDING_INFO = new ConcurrentHashMap<>();

    public static void startRiding(UUID player, UUID physicsObjectId, PhysicsTransform relativeSeatTransform) {
        RIDING_INFO.put(player, new RidingInfo(physicsObjectId, relativeSeatTransform));
    }

    public static void stopRiding(UUID player) {
        RIDING_INFO.remove(player);
    }

    public static boolean isRiding(UUID player) {
        return RIDING_INFO.containsKey(player);
    }

    @Nullable
    public static RidingInfo getRidingInfo(UUID player) {
        return RIDING_INFO.get(player);
    }
}