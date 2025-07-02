package net.xmx.xbullet.physics.terrain.pcmd.active;

import com.github.stephengold.joltjni.BodyInterface;
import net.xmx.xbullet.physics.world.PhysicsWorld;
import net.xmx.xbullet.physics.world.pcmd.ICommand;
import net.xmx.xbullet.physics.terrain.chunk.TerrainSection;

public record DeactivateTerrainSectionCommand(TerrainSection section) implements ICommand {
    @Override
    public void execute(PhysicsWorld world) {
        if (section.getBodyId() == 0 || section.getState() != TerrainSection.State.READY_ACTIVE) {
            return;
        }

        BodyInterface bodyInterface = world.getBodyInterface();
        if (bodyInterface != null && bodyInterface.isAdded(section.getBodyId())) {
            bodyInterface.removeBody(section.getBodyId());
            section.setState(TerrainSection.State.READY_INACTIVE);
        }
    }
}