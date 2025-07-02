package net.xmx.xbullet.command.xbullet;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
// KORREKTUR HIER: Import hinzufügen
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.xmx.xbullet.physics.object.global.physicsobject.EObjectType;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManager;
import net.xmx.xbullet.physics.object.global.physicsobject.registry.GlobalPhysicsObjectRegistry;
import net.xmx.xbullet.physics.object.rigidphysicsobject.builder.RigidPhysicsObjectBuilder;
import net.xmx.xbullet.physics.object.softphysicsobject.builder.SoftPhysicsObjectBuilder;
import net.xmx.xbullet.physics.world.PhysicsWorld;

public class SpawnObjectCommandExecutor {

    public static int executeRigid(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel serverLevel = source.getLevel();

        ResourceLocation objectTypeId = ResourceLocationArgument.getId(context, "objectType");
        String objectTypeIdentifier = objectTypeId.toString();
        Vec3 minecraftPos = Vec3Argument.getVec3(context, "position");

        PhysicsObjectManager manager = PhysicsWorld.getObjectManager(serverLevel.dimension());
        if (!manager.isInitialized()) {
            source.sendFailure(Component.literal("Physics system for this dimension is not initialized."));
            return 0;
        }

        GlobalPhysicsObjectRegistry.RegistrationData regData = GlobalPhysicsObjectRegistry.getRegistrationData(objectTypeIdentifier);
        if (regData == null) {
            source.sendFailure(Component.literal("Unknown physics object type: " + objectTypeIdentifier));
            return 0;
        }

        if (regData.objectType() != EObjectType.RIGID_BODY) {
            source.sendFailure(Component.literal("Type " + objectTypeIdentifier + " is not a rigid body. Use '/xbullet spawn soft ...' instead."));
            return 0;
        }

        IPhysicsObject spawnedObject = new RigidPhysicsObjectBuilder()
                .level(serverLevel)
                .type(objectTypeIdentifier)
                .position(minecraftPos.x, minecraftPos.y, minecraftPos.z)
                .spawn(manager);

        if (spawnedObject != null) {
            source.sendSuccess(() -> Component.literal("Successfully spawned " + objectTypeIdentifier + " (ID: " + spawnedObject.getPhysicsId() + ")"), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("Failed to spawn " + objectTypeIdentifier + ". See logs for details."));
            return 0;
        }
    }

    public static int executeSoftRope(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel serverLevel = source.getLevel();
        // KORREKTUR HIER: Wert als ResourceLocation abrufen und in String umwandeln
        ResourceLocation objectTypeId = ResourceLocationArgument.getId(context, "objectType");
        String objectTypeIdentifier = objectTypeId.toString();
        Vec3 minecraftPos = Vec3Argument.getVec3(context, "position");
        float length = context.getArgument("length", Float.class);
        int segments = context.getArgument("segments", Integer.class);
        float radius = context.getArgument("radius", Float.class);

        PhysicsObjectManager manager = PhysicsWorld.getObjectManager(serverLevel.dimension());
        if (!manager.isInitialized()) {
            source.sendFailure(Component.literal("Physics system for this dimension is not initialized."));
            return 0;
        }

        GlobalPhysicsObjectRegistry.RegistrationData regData = GlobalPhysicsObjectRegistry.getRegistrationData(objectTypeIdentifier);
        if (regData == null) {
            source.sendFailure(Component.literal("Unknown physics object type: " + objectTypeIdentifier));
            return 0;
        }

        if (regData.objectType() != EObjectType.SOFT_BODY) {
            source.sendFailure(Component.literal("Type " + objectTypeIdentifier + " is not a soft body. Use '/xbullet spawn rigid ...' instead."));
            return 0;
        }

        CompoundTag customNbt = new CompoundTag();
        customNbt.putFloat("ropeLength", length);
        customNbt.putInt("numSegments", segments);
        customNbt.putFloat("ropeRadius", radius);

        IPhysicsObject spawnedObject = new SoftPhysicsObjectBuilder()
                .level(serverLevel)
                .type(objectTypeIdentifier)
                .position(minecraftPos.x, minecraftPos.y, minecraftPos.z)
                .nbt(customNbt)
                .spawn(manager);

        if (spawnedObject != null) {
            source.sendSuccess(() -> Component.literal("Successfully spawned " + objectTypeIdentifier + " (ID: " + spawnedObject.getPhysicsId() + ")"), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("Failed to spawn " + objectTypeIdentifier + ". See logs for details."));
            return 0;
        }
    }
}