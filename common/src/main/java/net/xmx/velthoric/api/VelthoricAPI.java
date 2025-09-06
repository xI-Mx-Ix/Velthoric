package net.xmx.velthoric.api;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import net.xmx.velthoric.physics.object.type.VxSoftBody;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class VelthoricAPI {

    private static VelthoricAPI instance;

    private final Map<String, VxObjectType<?>> queuedRegistrations = new ConcurrentHashMap<>();

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

    public void registerPhysicsObjectType(VxObjectType<?> type) {
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
        VxClientObjectManager.getInstance().registerRigidRendererFactory(typeIdentifier, factory);
    }

    @Environment(EnvType.CLIENT)
    public void registerSoftRenderer(String typeIdentifier, Supplier<VxSoftBody.Renderer> factory) {
        VxClientObjectManager.getInstance().registerSoftRendererFactory(typeIdentifier, factory);
    }

    public Map<String, VxObjectType<?>> getQueuedRegistrations() {
        return Map.copyOf(queuedRegistrations);
    }
}