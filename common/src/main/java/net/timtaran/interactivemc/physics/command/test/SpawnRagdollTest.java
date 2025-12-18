/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.command.test;

import com.github.stephengold.joltjni.RVec3;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.timtaran.interactivemc.physics.physics.ragdoll.VxRagdollManager;
import net.timtaran.interactivemc.physics.physics.world.VxPhysicsWorld;

/**
 * A test command to spawn a humanoid ragdoll for a specified living entity.
 *
 * @author xI-Mx-Ix
 */
public final class SpawnRagdollTest implements IVxTestCommand {

    @Override
    public String getName() {
        return "spawnRagdoll";
    }

    @Override
    public void registerArguments(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(Commands.argument("entity", EntityArgument.entity())
                .then(Commands.argument("position", Vec3Argument.vec3())
                        .executes(this::execute)));
    }

    /**
     * Executes the command to spawn a ragdoll.
     *
     * @param context The command context, containing the source and arguments.
     * @return An integer indicating success (1) or failure (0).
     * @throws CommandSyntaxException if the entity argument is invalid.
     */
    private int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Entity entity = EntityArgument.getEntity(context, "entity");
        Vec3 spawnPos = Vec3Argument.getVec3(context, "position");

        // Ensure the target is a living entity, as ragdolls are based on animated models.
        if (!(entity instanceof LivingEntity livingEntity)) {
            source.sendFailure(Component.literal("The target must be a living entity (e.g., a player, zombie, etc.)."));
            return 0;
        }

        // Get the physics world for the entity's current dimension.
        VxPhysicsWorld world = VxPhysicsWorld.get(livingEntity.level().dimension());
        if (world == null) {
            source.sendFailure(Component.literal("The physics world is not running in this dimension."));
            return 0;
        }

        // Access the ragdoll manager and request the creation of a humanoid ragdoll.
        VxRagdollManager ragdollManager = world.getRagdollManager();
        if (ragdollManager != null) {
            // Convert Minecraft's Vec3 to Jolt's RVec3 for the physics engine.
            RVec3 joltSpawnPos = new RVec3((float) spawnPos.x, (float) spawnPos.y, (float) spawnPos.z);
            ragdollManager.createHumanoidRagdoll(livingEntity, joltSpawnPos);
            source.sendSuccess(() -> Component.literal("Spawning a ragdoll for " + livingEntity.getName().getString()), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("Could not access the ragdoll manager."));
            return 0;
        }
    }
}