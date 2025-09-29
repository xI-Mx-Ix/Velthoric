/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.client.body;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.AABB;
import net.xmx.velthoric.physics.object.client.VxClientObjectDataStore;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.client.VxRenderState;
import net.xmx.velthoric.physics.object.sync.VxSynchronizedData;
import net.xmx.velthoric.physics.object.sync.VxDataAccessor;
import net.xmx.velthoric.physics.object.sync.VxDataSerializer;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An abstract representation of a physics object on the client side.
 * This class acts as a lightweight handle for accessing the object's data,
 * which is stored in the {@link VxClientObjectDataStore} for performance.
 * It encapsulates logic for state calculation, culling, and synchronized data.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxClientBody {

    protected final UUID id;
    protected final VxClientObjectManager manager;
    protected final int dataStoreIndex;
    protected final EBodyType objectType;
    protected final VxSynchronizedData synchronizedData;

    private static final AtomicInteger NEXT_ACCESSOR_ID = new AtomicInteger(0);

    protected VxClientBody(UUID id, VxClientObjectManager manager, int dataStoreIndex, EBodyType objectType) {
        this.id = id;
        this.manager = manager;
        this.dataStoreIndex = dataStoreIndex;
        this.objectType = objectType;
        this.synchronizedData = new VxSynchronizedData(EnvType.CLIENT);
        this.defineSyncData();
    }

    /**
     * Creates a new Data Accessor with a unique ID for this body type.
     * This should be called to initialize static final DataAccessor fields in subclasses.
     * @param serializer The serializer for the data type.
     * @return A new {@link VxDataAccessor}.
     */
    protected static <T> VxDataAccessor<T> createAccessor(VxDataSerializer<T> serializer) {
        return new VxDataAccessor<>(NEXT_ACCESSOR_ID.getAndIncrement(), serializer);
    }

    /**
     * Called in the constructor to define all synchronized data fields for this object type.
     * Implementations should call {@code synchronizedData.define(ACCESSOR, defaultValue)}.
     */
    protected abstract void defineSyncData();

    /**
     * Gets the value of a synchronized data field.
     * @param accessor The accessor for the data.
     * @return The current value.
     */
    public <T> T getSyncData(VxDataAccessor<T> accessor) {
        return this.synchronizedData.get(accessor);
    }

    /**
     * Calculates the final, interpolated render state for the object for the current frame.
     * This method populates the provided output objects with the interpolated transform.
     *
     * @param partialTicks The fraction of a tick that has passed since the last full tick.
     * @param outState     The {@link VxRenderState} object to populate with the final state.
     * @param tempPos      A reusable {@link RVec3} to store the intermediate interpolated position.
     * @param tempRot      A reusable {@link Quat} to store the intermediate interpolated rotation.
     */
    public abstract void calculateRenderState(float partialTicks, VxRenderState outState, RVec3 tempPos, Quat tempRot);

    /**
     * Renders the object in the world.
     * This must be implemented by the final concrete class.
     *
     * @param poseStack    The current pose stack for transformations.
     * @param bufferSource The buffer source for drawing.
     * @param partialTicks The fraction of the current tick.
     * @param packedLight  The calculated light value at the object's position.
     * @param renderState  The final interpolated state to be rendered.
     */
    public abstract void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTicks, int packedLight, VxRenderState renderState);

    public UUID getId() {
        return id;
    }

    public int getDataStoreIndex() {
        return dataStoreIndex;
    }

    public boolean isInitialized() {
        return manager.getStore().render_isInitialized[dataStoreIndex];
    }

    public VxSynchronizedData getSynchronizedData() {
        return synchronizedData;
    }

    public AABB getCullingAABB(float inflation) {
        RVec3 lastPos = manager.getStore().lastKnownPosition[dataStoreIndex];
        return new AABB(
                lastPos.xx() - inflation, lastPos.yy() - inflation, lastPos.zz() - inflation,
                lastPos.xx() + inflation, lastPos.yy() + inflation, lastPos.zz() + inflation
        );
    }
}