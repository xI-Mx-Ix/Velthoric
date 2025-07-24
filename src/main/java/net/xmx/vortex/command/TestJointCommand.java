package net.xmx.vortex.command;

import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.vortex.builtin.box.BoxRigidPhysicsObject;
import net.xmx.vortex.math.VxTransform;
import net.xmx.vortex.physics.constraint.manager.VxConstraintManager;
import net.xmx.vortex.physics.object.physicsobject.IPhysicsObject;
import net.xmx.vortex.physics.object.physicsobject.manager.VxObjectManager;
import net.xmx.vortex.physics.world.VxPhysicsWorld;

import java.util.UUID;

public class TestJointCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("testjoint")
                .requires(source -> source.hasPermission(2))
                .executes(TestJointCommand::run));
    }

    private static int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(player.serverLevel().dimension());

        if (physicsWorld == null || !physicsWorld.isRunning()) {
            context.getSource().sendFailure(Component.literal("Physics system not initialized for this dimension."));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("Queuing creation of two boxes and a hinge joint..."), true);

        physicsWorld.execute(() -> {
            VxObjectManager objectManager = physicsWorld.getObjectManager();
            VxConstraintManager constraintManager = physicsWorld.getConstraintManager();

            if (objectManager == null || constraintManager == null || !objectManager.isInitialized() || !constraintManager.isInitialized()) {

                player.getServer().execute(() -> context.getSource().sendFailure(Component.literal("Object or Constraint Manager is not available/initialized.")));
                return;
            }

            VxTransform transform1 = new VxTransform(new RVec3(player.getX(), player.getY() + 3, player.getZ()), Quat.sIdentity());
            IPhysicsObject box1 = objectManager.createPhysicsObject(
                    BoxRigidPhysicsObject.TYPE_IDENTIFIER, UUID.randomUUID(), player.level(), transform1, null
            );

            VxTransform transform2 = new VxTransform(new RVec3(player.getX() + 1.0, player.getY() + 3, player.getZ()), Quat.sIdentity());
            IPhysicsObject box2 = objectManager.createPhysicsObject(
                    BoxRigidPhysicsObject.TYPE_IDENTIFIER, UUID.randomUUID(), player.level(), transform2, null
            );

            objectManager.spawnObject(box1);
            objectManager.spawnObject(box2);

            constraintManager.createHinge()
                    .between(box1, box2)
                    .inSpace(EConstraintSpace.LocalToBodyCOM)
                    .atPoints(new RVec3(0.5, 0.0, 0.0), new RVec3(-0.5, 0.0, 0.0))
                    .withHingeAxes(new Vec3(0, 1, 0), new Vec3(0, 1, 0))
                    .withNormalAxes(new Vec3(1, 0, 0), new Vec3(1, 0, 0))
                    .withLimits((float) -Math.PI / 4, (float) Math.PI / 4)
                    .build();

        });

        return 1;
    }
}