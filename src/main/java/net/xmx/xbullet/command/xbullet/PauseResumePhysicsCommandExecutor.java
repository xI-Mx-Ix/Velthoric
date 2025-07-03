package net.xmx.xbullet.command.xbullet;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.ObjectManager;
import net.xmx.xbullet.physics.world.PhysicsWorld;

class PauseResumePhysicsCommandExecutor {

    static int executePause(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();

        PhysicsWorld physicsWorld = getPhysicsWorld(source, level);
        if (physicsWorld == null) return 0;

        if (physicsWorld.isPaused()) {
            source.sendFailure(Component.literal("Physics simulation is already paused in this dimension."));
            return 0;
        }

        physicsWorld.pause();
        source.sendSuccess(() -> Component.literal("Physics simulation paused for dimension: " + level.dimension().location()), true);
        return 1;
    }

    static int executeResume(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel serverLevel = source.getLevel();

        PhysicsWorld physicsWorld = getPhysicsWorld(source, serverLevel);
        if (physicsWorld == null) return 0;

        if (!physicsWorld.isPaused()) {
            source.sendFailure(Component.literal("Physics simulation is not currently paused in this dimension."));
            return 0;
        }

        if (!physicsWorld.isPaused()) {
            source.sendFailure(Component.literal("Physics simulation is not currently paused in this dimension."));
            return 0;
        }

        physicsWorld.resume();
        source.sendSuccess(() -> Component.literal("Physics simulation resumed for dimension: " + serverLevel.dimension().location()), true);
        return 1;
    }

    private static PhysicsWorld getPhysicsWorld(CommandSourceStack source, ServerLevel level) {
        ObjectManager manager = PhysicsWorld.getObjectManager(level.dimension());
        if (manager == null || !manager.isInitialized()) {
            source.sendFailure(Component.literal("Physics system for this dimension is not running."));
            XBullet.LOGGER.warn("Attempted to pause/resume in dimension {} but PhysicsObjectManager is not initialized.", level.dimension().location());
            return null;
        }
        return manager.getPhysicsWorld();
    }
}