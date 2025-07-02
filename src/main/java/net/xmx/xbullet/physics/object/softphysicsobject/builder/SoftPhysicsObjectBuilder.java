package net.xmx.xbullet.physics.object.softphysicsobject.builder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManager;
import net.xmx.xbullet.physics.object.softphysicsobject.SoftPhysicsObject;
import net.xmx.xbullet.physics.world.PhysicsWorld;

import java.util.UUID;

public class SoftPhysicsObjectBuilder {
    private UUID id;
    private Level level;
    private PhysicsTransform transform = new PhysicsTransform();
    private String typeIdentifier;
    protected final CompoundTag initialNbt = new CompoundTag();

    public SoftPhysicsObjectBuilder id(UUID id) { this.id = id; return this; }
    public SoftPhysicsObjectBuilder level(Level level) { this.level = level; return this; }
    public SoftPhysicsObjectBuilder transform(PhysicsTransform transform) { this.transform = transform.copy(); return this; }
    public SoftPhysicsObjectBuilder position(double x, double y, double z) { this.transform.getTranslation().set(x, y, z); return this; }
    public SoftPhysicsObjectBuilder rotation(float x, float y, float z, float w) { this.transform.getRotation().set(x, y, z, w); return this; }
    public SoftPhysicsObjectBuilder type(String typeIdentifier) { this.typeIdentifier = typeIdentifier; return this; }
    public SoftPhysicsObjectBuilder nbt(CompoundTag nbt) { if (nbt != null) this.initialNbt.merge(nbt); return this; }

    public SoftPhysicsObject spawn() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            XBullet.LOGGER.error("Builder: Level is not a ServerLevel or null.");
            return null;
        }
        PhysicsObjectManager manager = PhysicsWorld.getObjectManager(serverLevel.dimension());
        return spawn(manager);
    }

    public SoftPhysicsObject spawn(PhysicsObjectManager manager) {
        if (manager == null) return null;
        if (id == null) id = UUID.randomUUID();
        if (typeIdentifier == null) {
            XBullet.LOGGER.error("Builder: Type identifier not set.");
            return null;
        }
        var obj = manager.createPhysicsObject(typeIdentifier, id, level, transform, initialNbt);
        if (obj instanceof SoftPhysicsObject spo) {
            return (SoftPhysicsObject) manager.registerObject(spo);
        }
        return null;
    }
}