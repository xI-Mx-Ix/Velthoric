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
import net.xmx.xbullet.builtin.cloth.ClothSoftBody;
import net.xmx.xbullet.physics.object.physicsobject.type.soft.SoftPhysicsObject;

public class SpawnClothCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spawncloth")

                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("pos", Vec3Argument.vec3(true))

                        .then(Commands.argument("width", FloatArgumentType.floatArg(0.1f))

                                .then(Commands.argument("height", FloatArgumentType.floatArg(0.1f))

                                        .then(Commands.argument("mass", FloatArgumentType.floatArg(0.1f))

                                                .then(Commands.argument("segmentsWidth", IntegerArgumentType.integer(2, 50))

                                                        .then(Commands.argument("segmentsHeight", IntegerArgumentType.integer(2, 50))
                                                                .executes(SpawnClothCommand::execute)
                                                        )
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
        Vec3 pos = Vec3Argument.getVec3(context, "pos");
        float width = FloatArgumentType.getFloat(context, "width");
        float height = FloatArgumentType.getFloat(context, "height");
        float mass = FloatArgumentType.getFloat(context, "mass");
        int segmentsWidth = IntegerArgumentType.getInteger(context, "segmentsWidth");
        int segmentsHeight = IntegerArgumentType.getInteger(context, "segmentsHeight");

        ClothSoftBody.Builder builder = ClothSoftBody.builder();
        SoftPhysicsObject cloth = builder
                .size(width, height)
                .segments(segmentsWidth, segmentsHeight)
                .mass(mass)
                .level(level)
                .position(pos.x(), pos.y(), pos.z())
                .spawn();

        if (cloth != null) {

            source.sendSuccess(() -> Component.literal(
                    String.format("Tuch mit ID %s bei %.2f, %.2f, %.2f gespawnt.",
                            cloth.getPhysicsId().toString().substring(0, 8),
                            pos.x(), pos.y(), pos.z())), true);
            return 1;
        } else {

            source.sendFailure(Component.literal("Fehler beim Spawnen des Tuchs. Überprüfe die Server-Logs."));
            return 0;
        }
    }
}