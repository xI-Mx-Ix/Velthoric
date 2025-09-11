/*
This file is part of Velthoric.
Licensed under LGPL 3.0.
*/
package net.xmx.velthoric.command;

import com.github.stephengold.joltjni.HingeConstraintSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.builtin.VxRegisteredObjects;
import net.xmx.velthoric.builtin.box.BoxRigidBody;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.constraint.VxConstraint;
import net.xmx.velthoric.physics.constraint.manager.VxConstraintManager;
import net.xmx.velthoric.physics.object.manager.VxObjectManager;
import net.xmx.velthoric.physics.object.manager.VxRemovalReason;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

public final class SpawnConnectedBoxesCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("spawnconnectedboxes")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("position", Vec3Argument.vec3(true))
                                .executes(SpawnConnectedBoxesCommand::execute)
                        )
        );
    }

    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel serverLevel = source.getLevel();
        net.minecraft.world.phys.Vec3 centerPos = Vec3Argument.getVec3(context, "position");

        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(serverLevel.dimension());
        if (physicsWorld == null) {
            source.sendFailure(Component.literal("Physics system for this dimension is not initialized."));
            return 0;
        }

        physicsWorld.execute(() -> {
            VxObjectManager objectManager = physicsWorld.getObjectManager();
            VxConstraintManager constraintManager = physicsWorld.getConstraintManager();

            float halfExtent = 0.5f;
            float spacing = 0.1f;
            Vec3 halfExtents = new Vec3(halfExtent, halfExtent, halfExtent);

            RVec3 pos1 = new RVec3(centerPos.x - halfExtent - spacing, centerPos.y, centerPos.z);
            BoxRigidBody box1 = objectManager.createRigidBody(
                    VxRegisteredObjects.BOX,
                    new VxTransform(pos1, Quat.sIdentity()),
                    box -> box.setHalfExtents(halfExtents)
            );

            if (box1 == null) {
                source.sendFailure(Component.literal("Failed to spawn the first box."));
                return;
            }

            RVec3 pos2 = new RVec3(centerPos.x + halfExtent + spacing, centerPos.y, centerPos.z);
            BoxRigidBody box2 = objectManager.createRigidBody(
                    VxRegisteredObjects.BOX,
                    new VxTransform(pos2, Quat.sIdentity()),
                    box -> box.setHalfExtents(halfExtents)
            );

            if (box2 == null) {
                source.sendFailure(Component.literal("Failed to spawn the second box."));
                objectManager.removeObject(box1.getPhysicsId(), VxRemovalReason.DISCARD);
                return;
            }

            try (HingeConstraintSettings settings = new HingeConstraintSettings()) {
                settings.setSpace(EConstraintSpace.WorldSpace);

                RVec3 worldPivot = new RVec3(centerPos.x, centerPos.y, centerPos.z);
                Vec3 worldHingeAxis = new Vec3(0, 1, 0);
                Vec3 worldNormalAxis = new Vec3(1, 0, 0);

                settings.setPoint1(worldPivot);
                settings.setPoint2(worldPivot);
                settings.setHingeAxis1(worldHingeAxis);
                settings.setNormalAxis1(worldNormalAxis);
                settings.setHingeAxis2(worldHingeAxis);
                settings.setNormalAxis2(worldNormalAxis);

                VxConstraint constraint = constraintManager.createConstraint(settings, box1.getPhysicsId(), box2.getPhysicsId());

                if (constraint != null) {
                    source.sendSuccess(() -> Component.literal(String.format("Spawned two boxes (%s, %s) connected by a hinge constraint (%s).",
                            box1.getPhysicsId().toString().substring(0, 8),
                            box2.getPhysicsId().toString().substring(0, 8),
                            constraint.getConstraintId().toString().substring(0, 8))), true);
                } else {
                    source.sendFailure(Component.literal("Failed to create the hinge constraint."));
                }
            }
        });

        return 1;
    }
}