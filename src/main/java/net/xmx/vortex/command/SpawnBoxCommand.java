package net.xmx.vortex.command;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.xmx.vortex.builtin.box.BoxRigidPhysicsObject;
import net.xmx.vortex.math.VxTransform;
import net.xmx.vortex.physics.object.physicsobject.IPhysicsObject;
import net.xmx.vortex.physics.object.physicsobject.manager.VxObjectManager;
import net.xmx.vortex.physics.world.VxPhysicsWorld;

import java.util.UUID;

public final class SpawnBoxCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("spawnbox")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("position", Vec3Argument.vec3(true))

                                .then(Commands.argument("halfWidth", FloatArgumentType.floatArg(0.01f))
                                        .then(Commands.argument("halfHeight", FloatArgumentType.floatArg(0.01f))
                                                .then(Commands.argument("halfDepth", FloatArgumentType.floatArg(0.01f))
                                                        .executes(SpawnBoxCommand::executeFull)
                                                )
                                        )
                                )

                                .then(Commands.argument("size", FloatArgumentType.floatArg(0.01f))
                                        .executes(SpawnBoxCommand::executeSized)
                                )

                                .executes(SpawnBoxCommand::executeDefault)
                        )
        );
    }

    private static int executeFull(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        float halfWidth = FloatArgumentType.getFloat(context, "halfWidth");
        float halfHeight = FloatArgumentType.getFloat(context, "halfHeight");
        float halfDepth = FloatArgumentType.getFloat(context, "halfDepth");

        com.github.stephengold.joltjni.Vec3 halfExtents = new com.github.stephengold.joltjni.Vec3(halfWidth, halfHeight, halfDepth);
        return spawn(context, halfExtents);
    }

    private static int executeSized(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        float size = FloatArgumentType.getFloat(context, "size");
        float halfExtent = size / 2.0f;

        com.github.stephengold.joltjni.Vec3 halfExtents = new com.github.stephengold.joltjni.Vec3(halfExtent, halfExtent, halfExtent);
        return spawn(context, halfExtents);
    }

    private static int executeDefault(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        com.github.stephengold.joltjni.Vec3 halfExtents = new com.github.stephengold.joltjni.Vec3(0.5f, 0.5f, 0.5f);
        return spawn(context, halfExtents);
    }

    private static int spawn(CommandContext<CommandSourceStack> context, com.github.stephengold.joltjni.Vec3 halfExtents) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel serverLevel = source.getLevel();
        Vec3 spawnPosMc = Vec3Argument.getVec3(context, "position");

        if (halfExtents.getX() <= 0 || halfExtents.getY() <= 0 || halfExtents.getZ() <= 0) {
            source.sendFailure(Component.literal("Box dimensions (halfExtents) must be positive."));
            return 0;
        }

        VxObjectManager manager = VxPhysicsWorld.getObjectManager(serverLevel.dimension());
        if (manager == null || !manager.isInitialized()) {
            source.sendFailure(Component.literal("Physics system for this dimension is not initialized."));
            return 0;
        }

        VxTransform transform = new VxTransform(new RVec3(spawnPosMc.x, spawnPosMc.y, spawnPosMc.z), Quat.sIdentity());

        // Create the object using its specific type identifier.
        IPhysicsObject physicsObject = manager.createPhysicsObject(BoxRigidPhysicsObject.TYPE_IDENTIFIER, UUID.randomUUID(), serverLevel, transform, null);

        // Check if the object was created and is of the correct type.
        // Use pattern matching for instanceof to safely cast and assign.
        if (!(physicsObject instanceof BoxRigidPhysicsObject box)) {
            source.sendFailure(Component.literal("Failed to create a box object. Check logs for factory errors."));
            return 0;
        }

        // Set the box-specific properties BEFORE spawning it.
        // Spawning triggers physics initialization, which needs the shape data.
        box.setHalfExtents(halfExtents);

        // Spawn the configured object into the physics world.
        IPhysicsObject registeredObject = manager.spawnObject(physicsObject);
        if (registeredObject != null) {
            source.sendSuccess(() -> Component.literal(
                    String.format("Successfully spawned box (%.2f x %.2f x %.2f) with ID: %s",
                            halfExtents.getX() * 2, halfExtents.getY() * 2, halfExtents.getZ() * 2, registeredObject.getPhysicsId())
            ), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("Failed to register the box. Check logs for details (e.g., duplicate UUID)."));
            return 0;
        }
    }
}