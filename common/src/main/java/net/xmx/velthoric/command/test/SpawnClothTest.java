/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.command.test;

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
import net.xmx.velthoric.builtin.VxRegisteredBodies;
import net.xmx.velthoric.builtin.cloth.ClothSoftBody;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.body.manager.VxBodyManager;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

public class SpawnClothTest implements IVxTestCommand {

    @Override
    public String getName() {
        return "spawnCloth";
    }

    @Override
    public void registerArguments(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(Commands.argument("pos", Vec3Argument.vec3(true))
                .then(Commands.argument("width", FloatArgumentType.floatArg(0.1f))
                        .then(Commands.argument("height", FloatArgumentType.floatArg(0.1f))
                                .then(Commands.argument("mass", FloatArgumentType.floatArg(0.1f))
                                        .then(Commands.argument("segmentsWidth", IntegerArgumentType.integer(2, 50))
                                                .then(Commands.argument("segmentsHeight", IntegerArgumentType.integer(2, 50))
                                                        .executes(this::execute)
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        Vec3 pos = Vec3Argument.getVec3(context, "pos");
        float width = FloatArgumentType.getFloat(context, "width");
        float height = FloatArgumentType.getFloat(context, "height");
        float mass = FloatArgumentType.getFloat(context, "mass");
        int segmentsWidth = IntegerArgumentType.getInteger(context, "segmentsWidth");
        int segmentsHeight = IntegerArgumentType.getInteger(context, "segmentsHeight");

        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(level.dimension());
        if (physicsWorld == null) {
            source.sendFailure(Component.literal("Physics system for this dimension is not initialized."));
            return 0;
        }
        VxBodyManager manager = physicsWorld.getBodyManager();
        VxTransform transform = new VxTransform(new RVec3(pos.x(), pos.y(), pos.z()), Quat.sIdentity());

        ClothSoftBody spawnedCloth = manager.createSoftBody(
                VxRegisteredBodies.CLOTH,
                transform,
                cloth -> cloth.setConfiguration(segmentsWidth, segmentsHeight, width, height, mass, 0.001f)
        );

        if (spawnedCloth != null) {
            source.sendSuccess(() -> Component.literal("Successfully spawned cloth."), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("Failed to spawn the cloth. Check server logs."));
            return 0;
        }
    }
}