package net.xmx.xbullet.physics.core.pcmd;

import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.core.PhysicsWorld;

public record RunTaskCommand(Runnable task) implements ICommand {

    public static void queue(PhysicsWorld physicsWorld, Runnable task) {
        if (physicsWorld == null) {
            XBullet.LOGGER.warn("Attempted to queue RunTaskCommand with null PhysicsWorld.");
            return;
        }
        if (task == null) {
            XBullet.LOGGER.warn("Attempted to queue null Runnable task.");
            return;
        }

        RunTaskCommand command = new RunTaskCommand(task);
        physicsWorld.queueCommand(command);

        XBullet.LOGGER.trace("Queued RunTaskCommand to PhysicsWorld {}.", physicsWorld.getDimensionKey().location());
    }

    @Override
    public void execute(PhysicsWorld world) {
        if (task() != null) {
            try {
                task().run();
                XBullet.LOGGER.trace("Executed queued task for dimension {}.", world.getDimensionKey().location());
            } catch (Exception e) {
                XBullet.LOGGER.error("Exception during execution of queued task for dimension {}: {}", world.getDimensionKey().location(), e.getMessage(), e);
            }
        } else {
            XBullet.LOGGER.warn("Cannot execute RunTaskCommand - task is null for dimension {}.", world.getDimensionKey().location());
        }
    }
}