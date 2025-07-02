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
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManager;
import net.xmx.xbullet.physics.object.rigidphysicsobject.RigidPhysicsObject;
import net.xmx.xbullet.physics.object.rigidphysicsobject.builder.RigidPhysicsObjectBuilder;
import net.xmx.xbullet.physics.physicsworld.PhysicsWorld;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class TestJointCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("testjoint")
                .requires(source -> source.hasPermission(2))
                .executes(TestJointCommand::run));
    }

    private static int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        net.minecraft.world.phys.Vec3 playerMcPos = player.position();

        PhysicsObjectManager objectManager = PhysicsWorld.getObjectManager(player.serverLevel().dimension());
        ConstraintManager constraintManager = PhysicsWorld.getConstraintManager(player.serverLevel().dimension());

        if (!objectManager.isInitialized() || !constraintManager.isInitialized()) {
            player.sendSystemMessage(Component.literal("Physics system not initialized for this dimension."));
            return 0;
        }

        CompoundTag props1 = new CompoundTag();
        props1.putString("motionType", EMotionType.Kinematic.name());

        RigidPhysicsObject box1 = new RigidPhysicsObjectBuilder()
                .level(player.level())
                .type(BoxRigidPhysicsObject.TYPE_IDENTIFIER)
                .position(playerMcPos.x(), playerMcPos.y() + 1, playerMcPos.z())
                .customNBTData(props1)
                .spawn(objectManager);

        RigidPhysicsObject box2 = new RigidPhysicsObjectBuilder()
                .level(player.level())
                .type(BoxRigidPhysicsObject.TYPE_IDENTIFIER)
                .position(playerMcPos.x() + 2.0, playerMcPos.y() + 1, playerMcPos.z())
                .spawn(objectManager);

        if (box1 == null || box2 == null) {
            player.sendSystemMessage(Component.literal("Error: Failed to spawn one or more boxes."));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Spawned two boxes: "
                + box1.getPhysicsId().toString().substring(0, 8) + " (anchor) and "
                + box2.getPhysicsId().toString().substring(0, 8)));

        UUID jointId = UUID.randomUUID();
        String constraintType = "xbullet:hinge";

        CompletableFuture<IPhysicsObject> futureBox1 = objectManager.getOrLoadObject(box1.getPhysicsId());
        CompletableFuture<IPhysicsObject> futureBox2 = objectManager.getOrLoadObject(box2.getPhysicsId());

        CompletableFuture.allOf(futureBox1, futureBox2).thenAcceptAsync(v -> {
            IPhysicsObject physBox1 = futureBox1.join();
            IPhysicsObject physBox2 = futureBox2.join();

            if (physBox1 == null || physBox2 == null || physBox1.getBodyId() == 0 || physBox2.getBodyId() == 0) {
                player.sendSystemMessage(Component.literal("Failed to get physics bodies for constraints."));
                return;
            }

            Body b1 = new Body(physBox1.getBodyId());
            Body b2 = new Body(physBox2.getBodyId());

            try (HingeConstraintSettings settings = new HingeConstraintSettings()) {
                settings.setSpace(EConstraintSpace.WorldSpace);

                RVec3 pivotPoint = new RVec3(playerMcPos.x() + 1.0, playerMcPos.y() + 1, playerMcPos.z());

                Vec3 hingeAxis = new Vec3(0, 0, 1);

                settings.setPoint1(pivotPoint);
                settings.setPoint2(pivotPoint);

                settings.setHingeAxis1(hingeAxis);
                settings.setHingeAxis2(hingeAxis);

                TwoBodyConstraint joltConstraint = settings.create(b1, b2);

                if (joltConstraint != null) {
                    ManagedConstraint managedConstraint = new ManagedConstraint(jointId, physBox1.getPhysicsId(), physBox2.getPhysicsId(), joltConstraint, constraintType);
                    constraintManager.addManagedConstraint(managedConstraint);
                    player.sendSystemMessage(Component.literal("Successfully created hinge joint: " + jointId.toString().substring(0, 8)));
                } else {
                    player.sendSystemMessage(Component.literal("Failed to create hinge joint. Check server logs."));
                }
            }
        }, player.getServer());

        return 1;
    }
}