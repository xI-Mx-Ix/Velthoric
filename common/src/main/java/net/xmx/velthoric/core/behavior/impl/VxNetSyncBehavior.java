/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.behavior.impl;

import net.xmx.velthoric.core.behavior.VxBehavior;
import net.xmx.velthoric.core.behavior.VxBehaviorId;
import net.xmx.velthoric.core.behavior.VxBehaviors;

/**
 * A marker behavior for network synchronization.
 * <p>
 * Bodies with this behavior attached will have their physical state and custom properties
 * synchronized to active clients within tracking distance. If a body lacks this behavior,
 * it exists purely on the server and will not send spawn or update packets.
 *
 * @author xI-Mx-Ix
 */
public class VxNetSyncBehavior implements VxBehavior {

    @Override
    public VxBehaviorId getId() {
        return VxBehaviors.NET_SYNC;
    }
}