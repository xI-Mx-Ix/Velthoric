package net.xmx.velthoric.physics.object.physicsobject.manager.registry;

import net.minecraft.network.FriendlyByteBuf;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.object.physicsobject.PhysicsObjectType;
import net.xmx.velthoric.physics.object.physicsobject.VxAbstractBody;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VxObjectRegistry {

    private final Map<String, PhysicsObjectType<?>> registeredTypes = new ConcurrentHashMap<>();

    public void register(PhysicsObjectType<?> type) {
        if (registeredTypes.containsKey(type.getTypeId())) {
            VxMainClass.LOGGER.warn("PhysicsObjectType '{}' is already registered. Overwriting.", type.getTypeId());
        }
        registeredTypes.put(type.getTypeId(), type);
    }

    @Nullable
    public VxAbstractBody create(String typeId, VxPhysicsWorld world, UUID id) {
        PhysicsObjectType<?> type = registeredTypes.get(typeId);
        if (type == null) {
            VxMainClass.LOGGER.error("No PhysicsObjectType registered for ID: {}", typeId);
            return null;
        }
        try {
            return type.create(world, id);
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to create physics object of type {}", typeId, e);
            return null;
        }
    }

    @Nullable
    public VxAbstractBody createAndDeserialize(String typeId, UUID id, VxPhysicsWorld world, FriendlyByteBuf data) {
        VxAbstractBody obj = create(typeId, world, id);
        if (obj != null) {
            if (data != null) {
                data.resetReaderIndex();
                obj.readCreationData(data);
            }
        }
        return obj;
    }

    @Nullable
    public PhysicsObjectType<?> getRegistrationData(String typeId) {
        return registeredTypes.get(typeId);
    }

    public Map<String, PhysicsObjectType<?>> getRegisteredTypes() {
        return Map.copyOf(registeredTypes);
    }
}