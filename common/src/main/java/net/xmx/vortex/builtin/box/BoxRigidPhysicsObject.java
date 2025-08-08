package net.xmx.vortex.builtin.box;

import com.github.stephengold.joltjni.BoxShapeSettings;
import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.Vec3;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.xmx.vortex.physics.object.physicsobject.PhysicsObjectType;
import net.xmx.vortex.physics.object.physicsobject.type.rigid.RigidPhysicsObject;
import net.xmx.vortex.physics.object.riding.seat.Seat;
import org.joml.Vector3f;

public class BoxRigidPhysicsObject extends RigidPhysicsObject {

    private Vec3 halfExtents;

    public BoxRigidPhysicsObject(PhysicsObjectType<? extends RigidPhysicsObject> type, Level level) {
        super(type, level);
        this.halfExtents = new Vec3(0.5f, 0.5f, 0.5f);
    }

    public void setHalfExtents(Vec3 halfExtents) {
        this.halfExtents = halfExtents;
        this.markDataDirty();
    }

    public Vec3 getHalfExtents() {
        return halfExtents;
    }

    @Override
    public Seat[] defineSeats() {
        Seat leftSeat = new Seat("leftSeat",
                new AABB(-2f, -1f, -1f, 0f, 1f, 1f),   // linker Sitzbereich links vom Ursprung
                new Vector3f(-1f, 0f, 0f)              // Rider-Offset links
        );

        Seat rightSeat = new Seat("rightSeat",
                new AABB(0f, -1f, -1f, 2f, 1f, 1f),    // rechter Sitzbereich rechts vom Ursprung
                new Vector3f(1f, 0f, 0f)               // Rider-Offset rechts
        );

        return new Seat[]{leftSeat, rightSeat};
    }


    @Override
    public ShapeSettings buildShapeSettings() {
        BoxShapeSettings settings = new BoxShapeSettings(this.halfExtents);
        return settings;
    }

    @Override
    protected void addAdditionalSpawnData(FriendlyByteBuf buf) {
        buf.writeFloat(this.halfExtents.getX());
        buf.writeFloat(this.halfExtents.getY());
        buf.writeFloat(this.halfExtents.getZ());
    }

    @Override
    protected void readAdditionalSpawnData(FriendlyByteBuf buf) {
        this.halfExtents = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
    }
}