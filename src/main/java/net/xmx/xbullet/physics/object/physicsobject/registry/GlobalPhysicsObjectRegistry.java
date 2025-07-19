package net.xmx.xbullet.physics.object.physicsobject.registry;

import net.minecraft.network.FriendlyByteBuf;
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

public final class GlobalPhysicsObjectRegistry {

    @FunctionalInterface
    public interface PhysicsObjectFactory {
        /**
         * Creates an instance of a physics object.
         * The initialData buffer is used to load saved state. It can be null if the object is new.
         */
        IPhysicsObject create(UUID id, Level level, String typeId, PhysicsTransform transform, IPhysicsObjectProperties properties, @Nullable FriendlyByteBuf initialData);
    }

    public record RegistrationData(
            EObjectType objectType,
            IPhysicsObjectProperties properties,
            Class<? extends IPhysicsObject> objectClass,
            PhysicsObjectFactory factory
    ) {}

    private static final Map<String, RegistrationData> REGISTERED_TYPES = new ConcurrentHashMap<>();

    private GlobalPhysicsObjectRegistry() {}

    /**
     * Registers a new type of physics object.
     * The provided class MUST have a specific constructor that matches the parameters of the factory.
     *
     * @param typeId The unique identifier for this object type.
     * @param objectType The general type (RIGID_BODY or SOFT_BODY).
     * @param properties The default physical properties for this object type.
     * @param clazz The implementation class for this object.
     */
    public static void register(String typeId, EObjectType objectType, IPhysicsObjectProperties properties, Class<? extends IPhysicsObject> clazz) {
        if (REGISTERED_TYPES.containsKey(typeId)) {
            XBullet.LOGGER.warn("Attempted to re-register PhysicsObject type '{}'. Skipping.", typeId);
            return;
        }

        try {
            // The constructor signature is now updated to use FriendlyByteBuf.
            Constructor<? extends IPhysicsObject> ctor = clazz.getDeclaredConstructor(UUID.class, Level.class, String.class, PhysicsTransform.class, IPhysicsObjectProperties.class, FriendlyByteBuf.class);

            // The factory lambda now passes the FriendlyByteBuf to the constructor.
            PhysicsObjectFactory factory = (id, level, type, transform, props, initialData) -> {
                try {
                    return ctor.newInstance(id, level, type, transform, props, initialData);
                } catch (Exception e) {
                    XBullet.LOGGER.error("Failed to instantiate physics object of type {}", type, e);
                    return null;
                }
            };
            REGISTERED_TYPES.put(typeId, new RegistrationData(objectType, properties, clazz, factory));
            XBullet.LOGGER.debug("Globally registered PhysicsObject type: {} -> {}", typeId, clazz.getName());
        } catch (NoSuchMethodException e) {
            // The error message is updated to inform developers of the correct, new constructor signature.
            throw new IllegalArgumentException("Class " + clazz.getName() + " must have a constructor: (UUID, Level, String, PhysicsTransform, IPhysicsObjectProperties, @Nullable FriendlyByteBuf)", e);
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