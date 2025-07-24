package net.xmx.vortex.physics.constraint.manager;

import net.minecraft.world.level.ChunkPos;

public class ConstraintLifecycleManager {

    private final VxConstraintManager constraintManager;
    private final ConstraintDataSystem dataSystem;

    public ConstraintLifecycleManager(VxConstraintManager constraintManager) {
        this.constraintManager = constraintManager;
        this.dataSystem = constraintManager.getDataSystem();
    }

    public void handleChunkUnload(ChunkPos chunkPos) {
        dataSystem.unloadConstraintsInChunk(chunkPos);
    }

    public void handleLevelSave() {
        dataSystem.saveAll(constraintManager.getManagedConstraints());
    }
}