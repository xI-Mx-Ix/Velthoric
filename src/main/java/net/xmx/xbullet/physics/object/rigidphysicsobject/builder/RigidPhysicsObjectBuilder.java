package net.xmx.xbullet.physics.object.rigidphysicsobject.builder;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.readonly.RVec3Arg;
import com.github.stephengold.joltjni.readonly.Vec3Arg;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManager;
import net.xmx.xbullet.physics.object.rigidphysicsobject.RigidPhysicsObject;
import net.xmx.xbullet.physics.world.PhysicsWorld;

import java.util.UUID;

public class RigidPhysicsObjectBuilder {
    private UUID id;
    private Level level;
    private PhysicsTransform transform = new PhysicsTransform();
    private String typeIdentifier;
    protected final CompoundTag initialNbt = new CompoundTag();

    public RigidPhysicsObjectBuilder id(UUID id) { this.id = id; return this; }
    public RigidPhysicsObjectBuilder level(Level level) { this.level = level; return this; }
    public RigidPhysicsObjectBuilder transform(PhysicsTransform transform) { this.transform = transform.copy(); return this; }
    public RigidPhysicsObjectBuilder position(double x, double y, double z) { this.transform.getTranslation().set(x, y, z); return this; }
    public RigidPhysicsObjectBuilder position(Vec3Arg position) { this.transform.getTranslation().set(position.getX(), position.getY(), position.getZ()); return this; }
    public RigidPhysicsObjectBuilder position(RVec3Arg position) { this.transform.getTranslation().set(position); return this; }
    public RigidPhysicsObjectBuilder rotation(float x, float y, float z, float w) { this.transform.getRotation().set(x, y, z, w); return this; }
    public RigidPhysicsObjectBuilder rotation(Quat rotation) { this.transform.getRotation().set(rotation); return this; }
    public RigidPhysicsObjectBuilder type(String typeIdentifier) { this.typeIdentifier = typeIdentifier; return this; }
    public RigidPhysicsObjectBuilder mass(float mass) { this.initialNbt.putFloat("mass", mass); return this; }
    public RigidPhysicsObjectBuilder friction(float friction) { this.initialNbt.putFloat("friction", friction); return this; }
    public RigidPhysicsObjectBuilder restitution(float restitution) { this.initialNbt.putFloat("restitution", restitution); return this; }

    /**
     * Fügt benutzerdefinierte NBT-Daten hinzu, die an das Physikobjekt übergeben werden.
     * Diese werden mit allen anderen NBT-Daten zusammengeführt.
     * @param customData Die benutzerdefinierten Daten.
     * @return Der Builder für Chaining.
     */
    public RigidPhysicsObjectBuilder customNBTData(CompoundTag customData) {
        if (customData != null) {
            this.initialNbt.merge(customData);
        }
        return this;
    }

    public RigidPhysicsObject spawn() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            XBullet.LOGGER.error("Builder: Level is not a ServerLevel or null.");
            return null;
        }
        PhysicsObjectManager manager = PhysicsWorld.getObjectManager(serverLevel.dimension());
        return spawn(manager);
    }

    public RigidPhysicsObject spawn(PhysicsObjectManager manager) {
        if (manager == null) {
            XBullet.LOGGER.error("Builder: PhysicsObjectManager is null.");
            return null;
        }
        if (id == null) id = UUID.randomUUID();
        if (typeIdentifier == null) {
            XBullet.LOGGER.error("Builder: Type identifier not set.");
            return null;
        }
        var obj = manager.createPhysicsObject(typeIdentifier, id, level, transform, initialNbt);
        if (obj instanceof RigidPhysicsObject rpo) {
            return (RigidPhysicsObject) manager.registerObject(rpo);
        }
        return null;
    }
}