/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.bridge.collision.entity;

/**
 * Interface used for duck-typing the Entity class to access {@link VxEntityAttachmentData}.
 *
 * @author xI-Mx-Ix
 */
public interface IVxEntityAttachmentData {
    /**
     * Retrieves the attachment data for the entity.
     *
     * @return The data store for physics body attachment state.
     */
    VxEntityAttachmentData getAttachmentData();
}