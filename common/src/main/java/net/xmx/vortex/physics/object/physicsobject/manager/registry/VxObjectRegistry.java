package net.xmx.vortex.physics.object.physicsobject.manager.registry;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.physics.object.physicsobject.IPhysicsObject;
import net.xmx.vortex.physics.object.physicsobject.PhysicsObjectType;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VxObjectRegistry {

    private final Map<String, PhysicsObjectType<?>> registeredTypes = new ConcurrentHashMap<>();

    public void register(PhysicsObjectType<?> type) {
        if (registeredTypes.containsKey(type.getTypeId())) {
            VxMainClass.LOGGER.warn("PhysicsObjectType '{}' ist bereits registriert. Wird überschrieben.", type.getTypeId());
        }
        registeredTypes.put(type.getTypeId(), type);
    }

    @Nullable
    public IPhysicsObject create(String typeId, Level level) {
        PhysicsObjectType<?> type = registeredTypes.get(typeId);
        if (type == null) {
            VxMainClass.LOGGER.error("Kein PhysicsObjectType für die ID registriert: {}", typeId);
            return null;
        }

        try {
            return type.create(level);
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Fehler beim Erstellen des Physikobjekts vom Typ {}", typeId, e);
            return null;
        }
    }

    @Nullable
    public IPhysicsObject createAndDeserialize(String typeId, UUID id, Level level, FriendlyByteBuf data) {
        IPhysicsObject obj = create(typeId, level);
        if (obj != null) {
            obj.setPhysicsId(id);
            if (data != null) {
                data.resetReaderIndex();
                obj.readSpawnData(data);
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