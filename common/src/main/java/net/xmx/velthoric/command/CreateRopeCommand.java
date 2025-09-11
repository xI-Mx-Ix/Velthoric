/*
This file is part of Velthoric.
Licensed under LGPL 3.0.
*/
package net.xmx.velthoric.command;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import net.xmx.velthoric.physics.constraint.manager.VxConstraintManager;
import net.xmx.velthoric.physics.object.manager.VxObjectManager;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;

public final class CreateRopeCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("createrope")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("start_position", Vec3Argument.vec3(true))
                                .then(Commands.argument("segments", IntegerArgumentType.integer(2, 1000))
                                        .executes(CreateRopeCommand::execute)
                                )
                        )
        );
    }

    private static BoxRigidBody createRopeSegment(VxPhysicsWorld world, VxObjectManager objectManager, RVec3 position, Vec3 halfExtents, EMotionType bodyType) {
        BoxRigidBody body = VxRegisteredObjects.BOX.create(world, UUID.randomUUID());
        body.getGameTransform().set(new VxTransform(position, Quat.sIdentity()));
        body.setHalfExtents(halfExtents);

        try (ShapeSettings shapeSettings = body.createShapeSettings()) {
            try (ShapeResult shapeResult = shapeSettings.create()) {
                if (shapeResult.hasError()) return null;
                try (ShapeRefC shapeRef = shapeResult.get()) {
                    try (BodyCreationSettings bcs = body.createBodyCreationSettings(shapeRef)) {
                        int bodyId = world.getBodyInterface().createAndAddBody(bcs, EActivation.Activate);
                        if (bodyId == Jolt.cInvalidBodyId) return null;

                        body.setBodyId(bodyId);
                        objectManager.add(body);
                        world.getConstraintManager().getDataSystem().onDependencyLoaded(body.getPhysicsId());
                        return body;
                    }
                }
            }
        }
    }

    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel serverLevel = source.getLevel();
        net.minecraft.world.phys.Vec3 startPos = Vec3Argument.getVec3(context, "start_position");
        int numSegments = IntegerArgumentType.getInteger(context, "segments");

        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(serverLevel.dimension());
        if (physicsWorld == null) {
            source.sendFailure(Component.literal("Physics system for this dimension is not initialized."));
            return 0;
        }

        physicsWorld.execute(() -> {
            VxObjectManager objectManager = physicsWorld.getObjectManager();
            VxConstraintManager constraintManager = physicsWorld.getConstraintManager();

            float segmentLength = 0.5f;
            float segmentRadius = 0.25f;
            Vec3 segmentHalfExtents = new Vec3(segmentRadius, segmentLength / 2.0f, segmentRadius);
            
            RVec3 anchorPosition = new RVec3(startPos.x, startPos.y, startPos.z);
            BoxRigidBody anchorBody = createRopeSegment(physicsWorld, objectManager, anchorPosition, segmentHalfExtents, EMotionType.Kinematic);

            if (anchorBody == null) {
                source.sendFailure(Component.literal("Failed to create rope anchor."));
                return;
            }

            BoxRigidBody previousBody = anchorBody;

            for (int i = 1; i < numSegments; ++i) {
                RVec3 currentPosition = new RVec3(
                        startPos.x,
                        startPos.y - (i * segmentLength),
                        startPos.z
                );

                BoxRigidBody currentBody = createRopeSegment(physicsWorld, objectManager, currentPosition, segmentHalfExtents, EMotionType.Dynamic);
                if (currentBody == null) {
                    source.sendFailure(Component.literal("Failed to create a rope segment. Aborting."));
                    return;
                }

                try (PointConstraintSettings settings = new PointConstraintSettings()) {
                    settings.setSpace(EConstraintSpace.LocalToBodyCom);
                    
                    settings.setNumPositionStepsOverride(4);
                    settings.setNumVelocityStepsOverride(4);

                    RVec3 pivotOnPrevious = new RVec3(0, -segmentLength / 2.0f, 0);
                    RVec3 pivotOnCurrent = new RVec3(0, segmentLength / 2.0f, 0);

                    settings.setPoint1(pivotOnPrevious);
                    settings.setPoint2(pivotOnCurrent);
                    
                    constraintManager.createConstraint(settings, previousBody.getPhysicsId(), currentBody.getPhysicsId());
                }
                previousBody = currentBody;
            }
            source.sendSuccess(() -> Component.literal(String.format("Successfully created a rope with %d segments.", numSegments)), true);
        });

        return 1;
    }
}