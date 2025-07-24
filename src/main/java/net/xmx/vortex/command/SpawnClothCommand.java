package net.xmx.vortex.command;

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
import net.xmx.vortex.builtin.cloth.ClothSoftBody;
import net.xmx.vortex.math.VxTransform;
import net.xmx.vortex.physics.object.physicsobject.IPhysicsObject;
import net.xmx.vortex.physics.object.physicsobject.manager.VxObjectManager;
import net.xmx.vortex.physics.object.physicsobject.registry.GlobalPhysicsObjectRegistry;
import net.xmx.vortex.physics.world.VxPhysicsWorld;

import java.util.UUID;

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

        float compliance = 0.001f;

        VxObjectManager manager = VxPhysicsWorld.getObjectManager(level.dimension());
        if (manager == null || !manager.isInitialized()) {
            source.sendFailure(Component.literal("Physics system for this dimension is not initialized."));
            return 0;
        }

        GlobalPhysicsObjectRegistry.RegistrationData regData = GlobalPhysicsObjectRegistry.getRegistrationData(ClothSoftBody.TYPE_IDENTIFIER);
        if (regData == null) {
            source.sendFailure(Component.literal("Cloth object type '" + ClothSoftBody.TYPE_IDENTIFIER + "' is not registered."));
            return 0;
        }

        VxTransform transform = new VxTransform(new RVec3(pos.x(), pos.y(), pos.z()), Quat.sIdentity());

        IPhysicsObject cloth = new ClothSoftBody(
                UUID.randomUUID(),
                level,
                transform,
                regData.properties(),
                segmentsWidth,
                segmentsHeight,
                width,
                height,
                mass,
                compliance
        );

        IPhysicsObject registeredCloth = manager.spawnObject(cloth);
        if (registeredCloth != null) {
            return 1;
        } else {
            source.sendFailure(Component.literal("Failed to register the cloth. Check server logs."));
            return 0;
        }
    }
}