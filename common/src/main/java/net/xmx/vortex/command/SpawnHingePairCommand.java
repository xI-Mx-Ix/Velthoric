package net.xmx.vortex.command;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.xmx.vortex.builtin.VxRegisteredObjects;
import net.xmx.vortex.builtin.box.BoxRigidPhysicsObject;
import net.xmx.vortex.math.VxTransform;
import net.xmx.vortex.physics.constraint.builder.HingeConstraintBuilder;
import net.xmx.vortex.physics.constraint.manager.VxConstraintManager;
import net.xmx.vortex.physics.object.physicsobject.VxAbstractBody;
import net.xmx.vortex.physics.object.physicsobject.manager.VxObjectManager;
import net.xmx.vortex.physics.object.physicsobject.manager.VxRemovalReason;
import net.xmx.vortex.physics.world.VxPhysicsWorld;

import java.util.Optional;

public final class SpawnHingePairCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("spawnhingepair")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("position", Vec3Argument.vec3(true))
                                .executes(SpawnHingePairCommand::execute)
                                .then(Commands.argument("size", FloatArgumentType.floatArg(0.1f))
                                        .executes(SpawnHingePairCommand::execute)
                                        .then(Commands.argument("spacing", FloatArgumentType.floatArg(0.0f))
                                                .executes(SpawnHingePairCommand::execute)
                                        )
                                )
                        )
        );
    }

    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel serverLevel = source.getLevel();
        net.minecraft.world.phys.Vec3 centerPosMc = Vec3Argument.getVec3(context, "position");

        float size;
        try {
            size = FloatArgumentType.getFloat(context, "size");
        } catch (IllegalArgumentException e) {
            size = 1.0f;
        }

        float spacing;
        try {
            spacing = FloatArgumentType.getFloat(context, "spacing");
        } catch (IllegalArgumentException e) {
            spacing = 0.1f;
        }

        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(serverLevel.dimension());
        if (physicsWorld == null) {
            source.sendFailure(Component.literal("Physics system for this dimension is not initialized."));
            return 0;
        }
        VxObjectManager objectManager = physicsWorld.getObjectManager();
        VxConstraintManager constraintManager = physicsWorld.getConstraintManager();

        float halfSize = size / 2.0f;
        float totalOffset = halfSize + (spacing / 2.0f);
        com.github.stephengold.joltjni.Vec3 halfExtents = new com.github.stephengold.joltjni.Vec3(halfSize, halfSize, halfSize);

        RVec3 pos1 = new RVec3(centerPosMc.x - totalOffset, centerPosMc.y, centerPosMc.z);
        RVec3 pos2 = new RVec3(centerPosMc.x + totalOffset, centerPosMc.y, centerPosMc.z);
        VxTransform transform1 = new VxTransform(pos1, Quat.sIdentity());
        VxTransform transform2 = new VxTransform(pos2, Quat.sIdentity());

        Optional<BoxRigidPhysicsObject> box1Opt = objectManager.createRigidBody(VxRegisteredObjects.BOX, transform1, box -> box.setHalfExtents(halfExtents));
        Optional<BoxRigidPhysicsObject> box2Opt = objectManager.createRigidBody(VxRegisteredObjects.BOX, transform2, box -> box.setHalfExtents(halfExtents));

        if (box1Opt.isEmpty() || box2Opt.isEmpty()) {
            source.sendFailure(Component.literal("Failed to spawn one or both boxes. Aborting."));
            box1Opt.ifPresent(obj -> objectManager.removeObject(obj.getPhysicsId(), VxRemovalReason.DISCARD));
            box2Opt.ifPresent(obj -> objectManager.removeObject(obj.getPhysicsId(), VxRemovalReason.DISCARD));
            return 0;
        }

        VxAbstractBody box1 = box1Opt.get();
        VxAbstractBody box2 = box2Opt.get();

        HingeConstraintBuilder hingeBuilder = constraintManager.createHinge();
        RVec3 hingePoint = new RVec3(centerPosMc.x, centerPosMc.y, centerPosMc.z);
        Vec3 hingeAxis = new Vec3(0, 1, 0);

        hingeBuilder
                .between(box1, box2)
                .inSpace(EConstraintSpace.WorldSpace)
                .atPoints(hingePoint, hingePoint)
                .withHingeAxes(hingeAxis, hingeAxis);

        constraintManager.queueCreation(hingeBuilder);

        source.sendSuccess(() -> Component.literal(
                String.format("Successfully spawned a pair of boxes connected by a hinge. Box1: %s, Box2: %s",
                        box1.getPhysicsId().toString().substring(0, 8),
                        box2.getPhysicsId().toString().substring(0, 8))
        ), true);

        return 1;
    }
}