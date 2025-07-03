package net.xmx.xbullet.command.xbullet;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.xmx.xbullet.command.xbullet.packet.RequestClientPhysicsObjectCountPacket;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.network.NetworkHandler;
import net.xmx.xbullet.physics.object.global.physicsobject.EObjectType;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.ObjectManager;
import net.xmx.xbullet.physics.world.PhysicsWorld;

class CountPhysicsObjectCommandExecutor {

    static int execute(CommandContext<CommandSourceStack> context) {
        String side = context.getArgument("side", String.class).toLowerCase();
        CommandSourceStack source = context.getSource();

        if ("server".equals(side)) {
            ServerLevel serverLevel = source.getLevel();
            if (serverLevel == null) {
                source.sendFailure(Component.literal("Cannot count server physics objects: ServerLevel is null."));
                return 0;
            }

            ObjectManager manager = PhysicsWorld.getObjectManager(serverLevel.dimension());
            if (!manager.isInitialized()) {
                source.sendFailure(Component.literal("Physics system for this dimension is not initialized."));
                return 0;
            }

            long rigidCount = manager.getManagedObjects().values().stream().filter(o -> o.getPhysicsObjectType() == EObjectType.RIGID_BODY).count();
            long softCount = manager.getManagedObjects().values().stream().filter(o -> o.getPhysicsObjectType() == EObjectType.SOFT_BODY).count();

            int pendingCount = manager.getDataSystem().getPendingLoadCount();

            String message = String.format("Server in dimension %s has:\n- %d Rigid Bodies\n- %d Soft Bodies\n-> Total: %d managed objects.\n%s",
                    serverLevel.dimension().location(),
                    rigidCount,
                    softCount,
                    rigidCount + softCount,
                    pendingCount > 0 ? String.format("Additionally, %d objects are currently being loaded.", pendingCount) : ""
            );

            source.sendSuccess(() -> Component.literal(message), true);
            return (int) (rigidCount + softCount + pendingCount);

        } else if ("client".equals(side)) {
            if (!(source.getEntity() instanceof ServerPlayer player)) {
                source.sendFailure(Component.literal("This command can only be executed by a player to count client objects."));
                return 0;
            }

            try {
                NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new RequestClientPhysicsObjectCountPacket());
                source.sendSuccess(() -> Component.literal(String.format("Requested physics object count from client '%s'...", player.getName().getString())), false);
            } catch (Exception e) {
                source.sendFailure(Component.literal("Failed to send request to client. See server logs."));
                XBullet.LOGGER.error("Failed to send RequestClientPhysicsObjectCountPacket to player {}.", player.getName().getString(), e);
                return 0;
            }
            return 1;

        } else {
            source.sendFailure(Component.literal("Invalid side specified: " + side + ". Use 'server' or 'client'."));
            return 0;
        }
    }
}