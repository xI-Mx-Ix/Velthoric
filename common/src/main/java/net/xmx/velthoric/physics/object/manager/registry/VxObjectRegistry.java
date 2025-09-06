package net.xmx.velthoric.physics.object.manager.registry;

import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.VxAbstractBody;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VxObjectRegistry {

    private final Map<String, VxObjectType<?>> registeredTypes = new ConcurrentHashMap<>();

    public void register(VxObjectType<?> type) {
        if (registeredTypes.containsKey(type.getTypeId())) {
            VxMainClass.LOGGER.warn("PhysicsObjectType '{}' is already registered. Overwriting.", type.getTypeId());
        }
        registeredTypes.put(type.getTypeId(), type);
    }

    @Nullable
    public VxAbstractBody create(String typeId, VxPhysicsWorld world, UUID id) {
        VxObjectType<?> type = registeredTypes.get(typeId);
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
    public VxAbstractBody createAndDeserialize(String typeId, UUID id, VxPhysicsWorld world, VxByteBuf data) {
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
    public VxObjectType<?> getRegistrationData(String typeId) {
        return registeredTypes.get(typeId);
    }

    public Map<String, VxObjectType<?>> getRegisteredTypes() {
        return Map.copyOf(registeredTypes);
    }
}