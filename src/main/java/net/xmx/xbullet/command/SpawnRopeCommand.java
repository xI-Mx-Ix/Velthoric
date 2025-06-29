package net.xmx.xbullet.command;

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
import net.xmx.xbullet.builtin.rope.RopeSoftBody;
import net.xmx.xbullet.physics.object.softphysicsobject.SoftPhysicsObject;

public class SpawnRopeCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spawnrope")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("pos", Vec3Argument.vec3(true))
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

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        Vec3 pos = Vec3Argument.getVec3(context, "pos");
        float length = FloatArgumentType.getFloat(context, "length");
        float radius = FloatArgumentType.getFloat(context, "radius");
        float mass = FloatArgumentType.getFloat(context, "mass");
        int segments = IntegerArgumentType.getInteger(context, "segments");

        RopeSoftBody.Builder builder = RopeSoftBody.builder();
        SoftPhysicsObject rope = builder
                .ropeLength(length)
                .ropeRadius(radius)
                .numSegments(segments)
                .mass(mass)
                .level(level)
                .position(pos.x(), pos.y(), pos.z())
                .spawn();

        if (rope != null) {
            source.sendSuccess(() -> Component.literal(
                    String.format("Seil mit ID %s bei %.2f, %.2f, %.2f gespawnt.",
                            rope.getPhysicsId().toString().substring(0, 8),
                            pos.x(), pos.y(), pos.z())), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("Fehler beim Spawnen des Seils. Überprüfe die Server-Logs."));
            return 0;
        }
    }
}