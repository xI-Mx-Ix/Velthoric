/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.world.pcmd;

import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A concrete, pooled implementation of {@link ICommand} designed to execute a standard {@link Runnable} task.
 * This class uses an object pool to reduce garbage collection overhead by recycling command instances.
 * It provides a convenient way to run arbitrary code on the physics thread.
 *
 * @author xI-Mx-Ix
 */
public final class RunTaskCommand implements ICommand {

    /** A thread-safe queue acting as an object pool for this command. */
    private static final Queue<RunTaskCommand> POOL = new ConcurrentLinkedQueue<>();
    /** The maximum number of command instances to keep in the pool. */
    private static final int MAX_POOL_SIZE = 1024;

    private Runnable task;

    /**
     * Private constructor to enforce the use of the object pool.
     */
    private RunTaskCommand() {
    }

    /**
     * Sets the task to be executed by this command instance.
     *
     * @param task The runnable task.
     */
    private void setTask(Runnable task) {
        this.task = task;
    }

    /**
     * Acquires a command instance from the pool, or creates a new one if the pool is empty.
     *
     * @return A ready-to-use {@link RunTaskCommand} instance.
     */
    private static RunTaskCommand acquire() {
        RunTaskCommand command = POOL.poll();
        if (command == null) {
            command = new RunTaskCommand();
        }
        return command;
    }

    /**
     * Releases a command instance back to the pool for reuse.
     *
     * @param command The command to release.
     */
    private static void release(RunTaskCommand command) {
        command.setTask(null); // Clear the reference to the task.
        if (POOL.size() < MAX_POOL_SIZE) {
            POOL.offer(command);
        }
    }

    /**
     * A static factory method to acquire, configure, and queue a command.
     *
     * @param physicsWorld The world to queue the command in.
     * @param task         The task to be executed.
     */
    public static void queue(VxPhysicsWorld physicsWorld, Runnable task) {
        if (physicsWorld == null || task == null) {
            return;
        }

        RunTaskCommand command = acquire();
        command.setTask(task);
        physicsWorld.queueCommand(command);
    }

    /**
     * Executes the contained task and then releases this command instance back to the pool.
     *
     * @param world The physics world instance.
     */
    @Override
    public void execute(VxPhysicsWorld world) {
        if (this.task != null) {
            try {
                this.task.run();
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Exception during execution of pooled task for dimension {}: {}", world.getDimensionKey().location(), e.getMessage(), e);
            }
        }
        // Always release the command back to the pool after execution.
        release(this);
    }
}