package net.xmx.vortex.physics.object.riding;

import com.github.stephengold.joltjni.Vec3;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.vortex.math.VxTransform;
import net.xmx.vortex.physics.object.physicsobject.IPhysicsObject;
import net.xmx.vortex.physics.object.riding.seat.Seat;
import net.xmx.vortex.physics.world.VxPhysicsWorld;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

public interface Rideable extends IPhysicsObject {

    default Seat[] defineSeats() {
        return new Seat[0];
    }

    default void handleRightClick(ServerPlayer player, Vec3 hitPointWorld) {
        Seat[] seats = defineSeats();
        if (seats.length == 0) {
            return;
        }

        VxTransform worldTransform = this.getCurrentTransform();
        Quaternionf invRotation = worldTransform.getRotation(new Quaternionf()).conjugate();
        Vector3f translation = worldTransform.getTranslation(new Vector3f());

        Vector3f localHitPointVec = new Vector3f(hitPointWorld.getX(), hitPointWorld.getY(), hitPointWorld.getZ());
        localHitPointVec.sub(translation);
        invRotation.transform(localHitPointVec);
        net.minecraft.world.phys.Vec3 localHitPoint = new net.minecraft.world.phys.Vec3(localHitPointVec.x(), localHitPointVec.y(), localHitPointVec.z());

        Optional<Seat> closestSeat = Arrays.stream(seats)
                .filter(seat -> seat.getLocalAABB().contains(localHitPoint))
                .min(Comparator.comparingDouble(seat -> localHitPoint.distanceToSqr(seat.getLocalAABB().getCenter())));

        closestSeat.ifPresent(seat -> VxPhysicsWorld.get(this.getLevel().dimension())
                .getRidingManager()
                .startRiding(player, this, seat));
    }

    void onStartRiding(ServerPlayer player, Seat seat);

    void onStopRiding(ServerPlayer player);
}