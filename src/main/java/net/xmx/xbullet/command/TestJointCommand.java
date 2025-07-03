package net.xmx.xbullet.command;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.xbullet.builtin.box.BoxRigidPhysicsObject;
import net.xmx.xbullet.physics.constraint.ManagedConstraint;
import net.xmx.xbullet.physics.constraint.manager.ConstraintManager;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.ObjectManager;
import net.xmx.xbullet.physics.object.rigidphysicsobject.RigidPhysicsObject;
import net.xmx.xbullet.physics.object.rigidphysicsobject.builder.RigidPhysicsObjectBuilder;
import net.xmx.xbullet.physics.world.PhysicsWorld;

import java.util.UUID;

public class TestJointCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("testjoint")
                .requires(source -> source.hasPermission(2))
                .executes(TestJointCommand::run));
    }

    private static int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PhysicsWorld physicsWorld = PhysicsWorld.get(player.serverLevel().dimension());

        if (physicsWorld == null || !physicsWorld.isRunning()) {
            player.sendSystemMessage(Component.literal("Physics system not initialized for this dimension."));
            return 0;
        }

        ObjectManager objectManager = physicsWorld.getObjectManager();

        CompoundTag props1 = new CompoundTag();
        props1.putString("motionType", EMotionType.Kinematic.name());

        RigidPhysicsObject box1 = new RigidPhysicsObjectBuilder()
                .level(player.level())
                .type(BoxRigidPhysicsObject.TYPE_IDENTIFIER)
                .position(player.getX(), player.getY() + 1, player.getZ())
                .customNBTData(props1)
                .spawn(objectManager);

        RigidPhysicsObject box2 = new RigidPhysicsObjectBuilder()
                .level(player.level())
                .type(BoxRigidPhysicsObject.TYPE_IDENTIFIER)
                .position(player.getX() + 1.0, player.getY() + 1, player.getZ())
                .spawn(objectManager);

        if (box1 == null || box2 == null) {
            player.sendSystemMessage(Component.literal("Error: Failed to spawn one or more boxes."));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Queued creation for two boxes."));

        final UUID box1Id = box1.getPhysicsId();
        final UUID box2Id = box2.getPhysicsId();

        physicsWorld.execute(() -> {
            IPhysicsObject physBox1 = objectManager.getObject(box1Id).orElse(null);
            IPhysicsObject physBox2 = objectManager.getObject(box2Id).orElse(null);

            if (physBox1 == null || physBox2 == null || physBox1.getBodyId() == 0 || physBox2.getBodyId() == 0) {
                player.getServer().execute(() -> player.sendSystemMessage(Component.literal("CRITICAL: Failed to get physics bodies when creating joint.")));
                return;
            }

            int b1Id = physBox1.getBodyId();
            int b2Id = physBox2.getBodyId();

            BodyInterface bodyInterface = physicsWorld.getBodyInterface();
            if (bodyInterface == null) {
                player.getServer().execute(() -> player.sendSystemMessage(Component.literal("CRITICAL: BodyInterface is null in physics thread.")));
                return;
            }

            ConstraintManager constraintManager = physicsWorld.getConstraintManager();
            UUID jointId = UUID.randomUUID();
            String constraintType = "xbullet:hinge";

            try (HingeConstraintSettings settings = new HingeConstraintSettings()) {

                settings.setSpace(EConstraintSpace.LocalToBodyCOM);

                settings.setPoint1(new RVec3(0.5, 0.0, 0.0));

                settings.setPoint2(new RVec3(-0.5, 0.0, 0.0));

                Vec3 hingeAxis = new Vec3(0, 1, 0);
                settings.setHingeAxis1(hingeAxis);
                settings.setHingeAxis2(hingeAxis);

                TwoBodyConstraint joltConstraint = bodyInterface.createConstraint(settings, b1Id, b2Id);

                if (joltConstraint != null) {
                    // 1. Erstelle SOFORT die sichere Referenz.
                    TwoBodyConstraintRef constraintRef = joltConstraint.toRef();

                    // 2. Übergib die sichere Referenz an den neuen, korrekten Konstruktor.
                    ManagedConstraint managedConstraint = new ManagedConstraint(jointId, box1Id, box2Id, constraintRef, constraintType);

                    constraintManager.addManagedConstraint(managedConstraint);
                    player.getServer().execute(() -> player.sendSystemMessage(Component.literal("Successfully created hinge joint: " + jointId.toString().substring(0, 8))));
                } else {
                    player.getServer().execute(() -> player.sendSystemMessage(Component.literal("Failed to create Jolt constraint object via BodyInterface.")));
                }
            }
        });

        return 1;
    }
}