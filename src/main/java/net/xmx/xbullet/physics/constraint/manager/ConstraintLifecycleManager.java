package net.xmx.xbullet.physics.constraint.manager;

import net.minecraft.world.level.ChunkPos;

public class ConstraintLifecycleManager {

    private final ConstraintManager constraintManager;
    private final ConstraintDataSystem dataSystem;

    public ConstraintLifecycleManager(ConstraintManager constraintManager) {
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