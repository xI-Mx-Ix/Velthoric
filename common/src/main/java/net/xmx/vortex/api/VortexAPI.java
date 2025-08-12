package net.xmx.vortex.api;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.physics.object.physicsobject.PhysicsObjectType;
import net.xmx.vortex.physics.object.physicsobject.client.ClientObjectDataManager;
import net.xmx.vortex.physics.object.physicsobject.type.rigid.VxRigidBody;
import net.xmx.vortex.physics.object.physicsobject.type.soft.VxSoftBody;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class VortexAPI {

    private static VortexAPI instance;

    private final Map<String, PhysicsObjectType<?>> queuedRegistrations = new ConcurrentHashMap<>();

    private VortexAPI() {}

    public static VortexAPI getInstance() {
        if (instance == null) {
            synchronized (VortexAPI.class) {
                if (instance == null) {
                    instance = new VortexAPI();
                    VxMainClass.LOGGER.debug("VortexAPI singleton created.");
                }
            }
        }
        return instance;
    }

    public void registerPhysicsObjectType(PhysicsObjectType<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("PhysicsObjectType must not be null.");
        }
        String typeId = type.getTypeId();
        if (typeId == null || typeId.trim().isEmpty()) {
            throw new IllegalArgumentException("PhysicsObjectType typeId must not be null or empty.");
        }

        if (queuedRegistrations.containsKey(typeId)) {
            VxMainClass.LOGGER.warn("PhysicsObjectType '{}' is already queued for registration. Overwriting.", typeId);
        }

        queuedRegistrations.put(typeId, type);
        VxMainClass.LOGGER.debug("Queued factory registration for PhysicsObjectType '{}'.", typeId);
    }

    @Environment(EnvType.CLIENT)
    public void registerRigidRenderer(String typeIdentifier, Supplier<VxRigidBody.Renderer> factory) {
        ClientObjectDataManager.getInstance().registerRigidRendererFactory(typeIdentifier, factory);
    }

    @Environment(EnvType.CLIENT)
    public void registerSoftRenderer(String typeIdentifier, Supplier<VxSoftBody.Renderer> factory) {
        ClientObjectDataManager.getInstance().registerSoftRendererFactory(typeIdentifier, factory);
    }

    public Map<String, PhysicsObjectType<?>> getQueuedRegistrations() {
        return Map.copyOf(queuedRegistrations);
    }
}