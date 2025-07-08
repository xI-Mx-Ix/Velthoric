// net/xmx/xbullet/physics/object/global/registry/GlobalPhysicsObjectRegistry.java
package net.xmx.xbullet.physics.object.physicsobject.registry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.object.physicsobject.EObjectType;
import net.xmx.xbullet.physics.object.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.physicsobject.properties.IPhysicsObjectProperties;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GlobalPhysicsObjectRegistry {

    @FunctionalInterface
    public interface PhysicsObjectFactory {
        IPhysicsObject create(UUID id, Level level, String typeId, PhysicsTransform transform, IPhysicsObjectProperties properties, @Nullable CompoundTag nbt);
    }

    public record RegistrationData(
            EObjectType objectType,
            IPhysicsObjectProperties properties,
            Class<? extends IPhysicsObject> objectClass,
            PhysicsObjectFactory factory
    ) {}

    private static final Map<String, RegistrationData> REGISTERED_TYPES = new ConcurrentHashMap<>();

    private GlobalPhysicsObjectRegistry() {}

    public static void register(String typeId, EObjectType objectType, IPhysicsObjectProperties properties, Class<? extends IPhysicsObject> clazz) {
        if (REGISTERED_TYPES.containsKey(typeId)) {
            XBullet.LOGGER.warn("Attempted to re-register PhysicsObject type '{}'. Skipping.", typeId);
            return;
        }

        try {
            Constructor<? extends IPhysicsObject> ctor = clazz.getDeclaredConstructor(UUID.class, Level.class, String.class, PhysicsTransform.class, IPhysicsObjectProperties.class, CompoundTag.class);
            PhysicsObjectFactory factory = (id, level, type, transform, props, nbt) -> {
                try {
                    return ctor.newInstance(id, level, type, transform, props, nbt);
                } catch (Exception e) {
                    XBullet.LOGGER.error("Failed to instantiate physics object of type {}", type, e);
                    return null;
                }
            };
            REGISTERED_TYPES.put(typeId, new RegistrationData(objectType, properties, clazz, factory));
            XBullet.LOGGER.debug("Globally registered PhysicsObject type: {} -> {}", typeId, clazz.getName());
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Class " + clazz.getName() + " must have a constructor: (UUID, Level, String, PhysicsTransform, IPhysicsObjectProperties, @Nullable CompoundTag)", e);
        }
    }

    @Nullable
    public static RegistrationData getRegistrationData(String typeId) {
        return REGISTERED_TYPES.get(typeId);
    }

    public static Map<String, RegistrationData> getRegisteredTypes() {
        return Collections.unmodifiableMap(REGISTERED_TYPES);
    }
}