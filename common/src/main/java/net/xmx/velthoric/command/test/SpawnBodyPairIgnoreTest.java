/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.command.test;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.builtin.VxRegisteredBodies;
import net.xmx.velthoric.builtin.box.BoxColor;
import net.xmx.velthoric.builtin.box.BoxRigidBody;
import net.xmx.velthoric.core.body.server.VxServerBodyManager;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;

/**
 * A test command that demonstrates and verifies the body pair ignore functionality.
 * <p>
 * This command spawns three boxes in a vertical stack:
 * <ul>
 *     <li><b>Bottom Box:</b> A regular rigid box.</li>
 *     <li><b>Middle Box:</b> Spawned directly on top of the bottom box, but set to ignore collisions with it.</li>
 *     <li><b>Top Box:</b> A blue box spawned on top of the middle box, which collides normally with both.</li>
 * </ul>
 * This allows verifying that the ignore manager correctly filters out collisions between specific
 * pairs while leaving other interactions intact.
 * </p>
 */
public final class SpawnBodyPairIgnoreTest implements IVxTestCommand {

    /**
     * Returns the unique name of the test command.
     *
     * @return The string "spawnBodyPairIgnore".
     */
    @Override
    public String getName() {
        return "spawnBodyPairIgnore";
    }

    /**
     * Registers the command execution logic to the Brigadier builder.
     *
     * @param builder The literal argument builder for the command.
     */
    @Override
    public void registerArguments(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.executes(this::execute);
    }

    /**
     * Executes the box spawning and collision filtering test.
     * <p>
     * Logic flow:
     * 1. Retrieves the player's position and the dimension's physics world.
     * 2. Spawns two boxes (bottom and middle).
     * 3. Registers the IDs of these two boxes in the {@link net.xmx.velthoric.jni.BodyPairIgnoreHandler}
     *    via {@link VxPhysicsWorld#getBodyPairIgnoreHandler()}.
     * 4. Spawns a third (top) box to show that normal collisions still work.
     *
     * @param context The command execution context.
     * @return 1 on success, 0 on failure.
     */
    private int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel serverLevel = source.getLevel();

        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(serverLevel.dimension());
        if (physicsWorld == null) {
            source.sendFailure(Component.literal("Physics system for this dimension is not initialized."));
            return 0;
        }

        VxServerBodyManager manager = physicsWorld.getBodyManager();
        Vec3 spawnPos;
        try {
            spawnPos = source.getPlayerOrException().position();
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal("Must be executed by a player."));
            return 0;
        }
        float boxSize = 1.0f;
        com.github.stephengold.joltjni.Vec3 halfExtents = new com.github.stephengold.joltjni.Vec3(boxSize / 2, boxSize / 2, boxSize / 2);

        // Spawn bottom box (will be ignored with middle box)
        VxTransform transform1 = new VxTransform(
                new RVec3(spawnPos.x, spawnPos.y, spawnPos.z),
                Quat.sIdentity()
        );
        BoxRigidBody box1 = manager.createRigidBody(
                VxRegisteredBodies.BOX,
                transform1,
                box -> box.setHalfExtents(halfExtents)
        );

        if (box1 == null) {
            source.sendFailure(Component.literal("Failed to spawn bottom box."));
            return 0;
        }

        // Spawn middle box (will be ignored with bottom box)
        VxTransform transform2 = new VxTransform(
                new RVec3(spawnPos.x, spawnPos.y + boxSize, spawnPos.z),
                Quat.sIdentity()
        );
        BoxRigidBody box2 = manager.createRigidBody(
                VxRegisteredBodies.BOX,
                transform2,
                box -> box.setHalfExtents(halfExtents)
        );

        if (box2 == null) {
            source.sendFailure(Component.literal("Failed to spawn middle box."));
            return 0;
        }

        // Ignore collision between box1 and box2
        physicsWorld.getBodyPairIgnoreHandler().ignorePair(box1.getBodyId(), box2.getBodyId());

        // Spawn top box (normal collision with both boxes)
        VxTransform transform3 = new VxTransform(
                new RVec3(spawnPos.x, spawnPos.y + boxSize * 2, spawnPos.z),
                Quat.sIdentity()
        );
        BoxRigidBody box3 = manager.createRigidBody(
                VxRegisteredBodies.BOX,
                transform3,
                box -> {
                    box.setHalfExtents(halfExtents);
                    box.setColor(BoxColor.BLUE);
                }
        );

        if (box3 == null) {
            source.sendFailure(Component.literal("Failed to spawn top box."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                String.format("Successfully spawned 3 boxes. Bottom (%s) and middle (%s) ignore each other. Top (%s) collides normally.",
                        box1.getPhysicsId(), box2.getPhysicsId(), box3.getPhysicsId())
        ), true);

        return 1;
    }
}