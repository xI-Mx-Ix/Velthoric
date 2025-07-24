package net.xmx.vortex.physics.world.pcmd;

import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.physics.world.VxPhysicsWorld;

public record RunTaskCommand(Runnable task) implements ICommand {

    public static void queue(VxPhysicsWorld physicsWorld, Runnable task) {
        if (physicsWorld == null) {
            VxMainClass.LOGGER.warn("Attempted to queue RunTaskCommand with null PhysicsWorld.");
            return;
        }
        if (task == null) {
            VxMainClass.LOGGER.warn("Attempted to queue null Runnable task.");
            return;
        }

        RunTaskCommand command = new RunTaskCommand(task);
        physicsWorld.queueCommand(command);

        VxMainClass.LOGGER.trace("Queued RunTaskCommand to PhysicsWorld {}.", physicsWorld.getDimensionKey().location());
    }

    @Override
    public void execute(VxPhysicsWorld world) {
        if (task() != null) {
            try {
                task().run();
                VxMainClass.LOGGER.trace("Executed queued task for dimension {}.", world.getDimensionKey().location());
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Exception during execution of queued task for dimension {}: {}", world.getDimensionKey().location(), e.getMessage(), e);
            }
        } else {
            VxMainClass.LOGGER.warn("Cannot execute RunTaskCommand - task is null for dimension {}.", world.getDimensionKey().location());
        }
    }
}