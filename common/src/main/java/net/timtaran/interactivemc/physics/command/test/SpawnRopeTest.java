/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.command.test;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.timtaran.interactivemc.physics.builtin.VxRegisteredBodies;
import net.timtaran.interactivemc.physics.builtin.rope.RopeSoftBody;
import net.timtaran.interactivemc.physics.math.VxTransform;
import net.timtaran.interactivemc.physics.physics.body.manager.VxBodyManager;
import net.timtaran.interactivemc.physics.physics.world.VxPhysicsWorld;

public final class SpawnRopeTest implements IVxTestCommand {

    @Override
    public String getName() {
        return "spawnRope";
    }

    @Override
    public void registerArguments(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(Commands.argument("position", Vec3Argument.vec3(true))
                .then(Commands.argument("length", FloatArgumentType.floatArg(0.1f))
                        .then(Commands.argument("radius", FloatArgumentType.floatArg(0.01f))
                                .then(Commands.argument("mass", FloatArgumentType.floatArg(0.1f))
                                        .then(Commands.argument("segments", IntegerArgumentType.integer(2, 100))
                                                .executes(this::execute)
                                        )
                                )
                        )
                )
        );
    }

    private int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        Vec3 pos = Vec3Argument.getVec3(context, "position");
        float length = FloatArgumentType.getFloat(context, "length");
        float radius = FloatArgumentType.getFloat(context, "radius");
        float mass = FloatArgumentType.getFloat(context, "mass");
        int segments = IntegerArgumentType.getInteger(context, "segments");

        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(level.dimension());
        if (physicsWorld == null) {
            source.sendFailure(Component.literal("Physics system for this dimension is not initialized."));
            return 0;
        }
        VxBodyManager manager = physicsWorld.getBodyManager();
        VxTransform transform = new VxTransform(new RVec3(pos.x(), pos.y(), pos.z()), Quat.sIdentity());

        RopeSoftBody spawnedRope = manager.createSoftBody(
                VxRegisteredBodies.ROPE,
                transform,
                rope -> rope.setConfiguration(length, segments, radius, mass, 0.001f)
        );

        if (spawnedRope != null) {
            source.sendSuccess(() -> Component.literal(
                    String.format("Successfully spawned rope with ID %s at %.2f, %.2f, %.2f",
                            spawnedRope.getPhysicsId().toString().substring(0, 8),
                            pos.x(), pos.y(), pos.z())
            ), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("Failed to spawn the rope. Check logs for details."));
            return 0;
        }
    }
}