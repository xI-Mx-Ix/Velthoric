package net.xmx.velthoric.physics.riding;

import com.github.stephengold.joltjni.Vec3;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.riding.seat.Seat;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

public interface Rideable {

    UUID getPhysicsId();

    VxPhysicsWorld getWorld();

    VxTransform getGameTransform();

    default Seat[] defineSeats() {
        return new Seat[0];
    }

    void onStartRiding(ServerPlayer player, Seat seat);

    void onStopRiding(ServerPlayer player);

    default void handleRightClick(ServerPlayer player, Vec3 hitPointWorld) {
        Seat[] seats = defineSeats();
        if (seats == null || seats.length == 0) {
            return;
        }

        VxTransform worldTransform = this.getGameTransform();
        Quaternionf invRotation = worldTransform.getRotation(new Quaternionf()).conjugate();
        Vector3f translation = worldTransform.getTranslation(new Vector3f());

        Vector3f localHitPointVec = new Vector3f(hitPointWorld.getX(), hitPointWorld.getY(), hitPointWorld.getZ());
        localHitPointVec.sub(translation);
        invRotation.transform(localHitPointVec);
        net.minecraft.world.phys.Vec3 localHitPoint = new net.minecraft.world.phys.Vec3(localHitPointVec.x(), localHitPointVec.y(), localHitPointVec.z());

        Optional<Seat> closestSeat = Arrays.stream(seats)
                .filter(seat -> seat.getLocalAABB().contains(localHitPoint))
                .min(Comparator.comparingDouble(seat -> localHitPoint.distanceToSqr(seat.getLocalAABB().getCenter())));

        closestSeat.ifPresent(seat -> this.getWorld().getRidingManager().startRiding(player, this, seat));
    }
}