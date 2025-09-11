package net.xmx.velthoric.command;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.builtin.VxRegisteredObjects;
import net.xmx.velthoric.builtin.rope.RopeSoftBody;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.object.manager.VxObjectManager;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

public final class SpawnRopeCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("spawnrope")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("position", Vec3Argument.vec3(true))
                                .then(Commands.argument("length", FloatArgumentType.floatArg(0.1f))
                                        .then(Commands.argument("radius", FloatArgumentType.floatArg(0.01f))
                                                .then(Commands.argument("mass", FloatArgumentType.floatArg(0.1f))
                                                        .then(Commands.argument("segments", IntegerArgumentType.integer(2, 100))
                                                                .executes(SpawnRopeCommand::execute)
                                                        )
                                                )
                                        )
                                )
                        )
        );
    }

    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
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
        VxObjectManager manager = physicsWorld.getObjectManager();
        VxTransform transform = new VxTransform(new RVec3(pos.x(), pos.y(), pos.z()), Quat.sIdentity());

        RopeSoftBody spawnedRope = manager.createSoftBody(
                VxRegisteredObjects.ROPE,
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