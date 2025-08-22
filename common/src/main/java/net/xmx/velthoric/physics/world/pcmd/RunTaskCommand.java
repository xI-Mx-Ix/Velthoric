package net.xmx.velthoric.physics.world.pcmd;

import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class RunTaskCommand implements ICommand {

    private static final Queue<RunTaskCommand> POOL = new ConcurrentLinkedQueue<>();
    private static final int MAX_POOL_SIZE = 1024;

    private Runnable task;

    private RunTaskCommand() {
    }

    private void setTask(Runnable task) {
        this.task = task;
    }

    private static RunTaskCommand acquire() {
        RunTaskCommand command = POOL.poll();
        if (command == null) {
            command = new RunTaskCommand();
        }
        return command;
    }

    private static void release(RunTaskCommand command) {
        command.setTask(null);
        if (POOL.size() < MAX_POOL_SIZE) {
            POOL.offer(command);
        }
    }

    public static void queue(VxPhysicsWorld physicsWorld, Runnable task) {
        if (physicsWorld == null || task == null) {
            return;
        }

        RunTaskCommand command = acquire();
        command.setTask(task);
        physicsWorld.queueCommand(command);
    }

    @Override
    public void execute(VxPhysicsWorld world) {
        if (this.task != null) {
            try {
                this.task.run();
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Exception during execution of pooled task for dimension {}: {}", world.getDimensionKey().location(), e.getMessage(), e);
            }
        }
        release(this);
    }
}