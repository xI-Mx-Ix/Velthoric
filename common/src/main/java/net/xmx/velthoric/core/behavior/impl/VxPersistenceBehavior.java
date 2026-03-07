/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.behavior.impl;

import net.xmx.velthoric.core.behavior.VxBehavior;
import net.xmx.velthoric.core.behavior.VxBehaviorId;
import net.xmx.velthoric.core.behavior.VxBehaviors;
import net.xmx.velthoric.core.body.server.VxServerBodyDataStore;
import net.xmx.velthoric.core.body.type.VxBody;

/**
 * The behavior for persistence (save/load to disk).
 * <p>
 * Bodies with this behavior are serialized to disk when their containing chunk is saved,
 * and deserialized when the chunk is loaded again. Bodies without this behavior are
 * discarded on chunk unload (useful for temporary debris, effects, etc.).
 * <p>
 * This behavior maintains a parallel SoA boolean array tracking persistence state.
 * The "is this body persistent?" check is now a simple bitmask operation.
 *
 * @author xI-Mx-Ix
 */
public class VxPersistenceBehavior implements VxBehavior {

    @Override
    public VxBehaviorId getId() {
        return VxBehaviors.PERSISTENCE;
    }

    @Override
    public void onAttached(int index, VxBody body) {
        // The persistence flag is now tracked via behaviorBits.
        // No separate SoA array needed for the basic persistent/non-persistent flag.
    }

    /**
     * Checks if a body at the given index should be persisted to disk.
     *
     * @param store The data store.
     * @param index The data store index.
     * @return True if the body should be saved.
     */
    public static boolean isPersistent(VxServerBodyDataStore store, int index) {
        return VxBehaviors.PERSISTENCE.isSet(store.behaviorBits[index]);
    }
}