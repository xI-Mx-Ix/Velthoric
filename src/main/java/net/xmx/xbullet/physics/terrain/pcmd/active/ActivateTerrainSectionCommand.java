package net.xmx.xbullet.physics.terrain.pcmd.active;

import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.enumerate.EActivation;
import net.xmx.xbullet.physics.world.PhysicsWorld;
import net.xmx.xbullet.physics.world.pcmd.ICommand;
import net.xmx.xbullet.physics.terrain.chunk.TerrainSection;

public record ActivateTerrainSectionCommand(TerrainSection section) implements ICommand {
    @Override
    public void execute(PhysicsWorld world) {
        if (section.getBodyId() == 0 || section.getState() != TerrainSection.State.READY_INACTIVE) {
            return;
        }

        BodyInterface bodyInterface = world.getBodyInterface();
        if (bodyInterface != null && !bodyInterface.isAdded(section.getBodyId())) {
            bodyInterface.addBody(section.getBodyId(), EActivation.DontActivate);
            section.setState(TerrainSection.State.READY_ACTIVE);
        }
    }
}