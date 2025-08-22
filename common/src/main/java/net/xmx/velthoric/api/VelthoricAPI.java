package net.xmx.velthoric.api;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.object.PhysicsObjectType;
import net.xmx.velthoric.physics.object.client.ClientObjectDataManager;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import net.xmx.velthoric.physics.object.type.VxSoftBody;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class VelthoricAPI {

    private static VelthoricAPI instance;

    private final Map<String, PhysicsObjectType<?>> queuedRegistrations = new ConcurrentHashMap<>();

    private VelthoricAPI() {}

    public static VelthoricAPI getInstance() {
        if (instance == null) {
            synchronized (VelthoricAPI.class) {
                if (instance == null) {
                    instance = new VelthoricAPI();
                    VxMainClass.LOGGER.debug("VelthoricAPI singleton created.");
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