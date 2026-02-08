/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.listener;

import net.xmx.velthoric.core.body.type.VxBody;

/**
 * Interface for components that need to react to physics body lifecycle events
 * such as spawning and removal.
 *
 * @author xI-Mx-Ix
 */
public interface VxBodyLifecycleListener {

    /**
     * Called immediately after a body has been registered and initialized.
     *
     * @param body The newly added body instance.
     */
    void onBodyAdded(VxBody body);

    /**
     * Called immediately before a body is removed from management.
     *
     * @param body The body instance being removed.
     */
    void onBodyRemoved(VxBody body);
}