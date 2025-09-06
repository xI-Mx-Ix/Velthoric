package net.xmx.velthoric.api;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.object.VxAbstractBody;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import net.xmx.velthoric.physics.object.type.VxSoftBody;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class VelthoricAPI {

    private static volatile VelthoricAPI instance;

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

    public void registerObjectType(VxObjectType<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("VxObjectType must not be null.");
        }
        String typeId = type.getTypeId();
        if (typeId == null || typeId.trim().isEmpty()) {
            throw new IllegalArgumentException("VxObjectType typeId must not be null or empty.");
        }

        if (queuedRegistrations.containsKey(typeId)) {
            VxMainClass.LOGGER.warn("VxObjectType '{}' is already queued for registration. Overwriting.", typeId);
        }

        queuedRegistrations.put(typeId, type);
        VxMainClass.LOGGER.debug("Queued factory registration for VxObjectType '{}'.", typeId);
    }

    @Environment(EnvType.CLIENT)
    public void registerRendererFactory(String typeIdentifier, Supplier<VxAbstractBody.Renderer> factory) {
        if (factory.get() instanceof VxRigidBody.Renderer) {
            VxClientObjectManager.getInstance().registerRigidRendererFactory(typeIdentifier,
                    () -> (VxRigidBody.Renderer) factory.get());
        } else if (factory.get() instanceof VxSoftBody.Renderer) {
            VxClientObjectManager.getInstance().registerSoftRendererFactory(typeIdentifier,
                    () -> (VxSoftBody.Renderer) factory.get());
        } else {
            throw new IllegalArgumentException("Renderer factory must be an instance of VxRigidBody.Renderer or VxSoftBody.Renderer.");
        }
    }

    public Map<String, VxObjectType<?>> getQueuedRegistrations() {
        return Map.copyOf(queuedRegistrations);
    }
}