/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.riding.VxRideable;
import net.xmx.velthoric.physics.riding.manager.VxRidingManager;
import net.xmx.velthoric.physics.riding.seat.VxSeat;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;

public class SitCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sit")
                .executes(context -> execute(context.getSource()))
        );
    }

    private static int execute(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        if (player.isPassenger()) {
            source.sendFailure(Component.literal("You are already riding something."));
            return 0;
        }

        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(player.level().dimension());
        if (physicsWorld == null) {
            source.sendFailure(Component.literal("Physics world is not available in this dimension."));
            return 0;
        }
        VxRidingManager ridingManager = physicsWorld.getRidingManager();

        double minDistanceSq = Double.MAX_VALUE;
        VxRideable closestRideable = null;
        VxSeat closestSeat = null;

        Vec3 playerPos = player.position();

        for (Map.Entry<UUID, Map<String, VxSeat>> entry : ridingManager.getAllSeatsByObject().entrySet()) {
            UUID objectId = entry.getKey();
            Map<String, VxSeat> seats = entry.getValue();

            VxBody body = physicsWorld.getObjectManager().getObject(objectId);
            if (!(body instanceof VxRideable rideable)) {
                continue;
            }

            VxTransform transform = rideable.getTransform();
            Vector3f bodyWorldPos = transform.getTranslation(new Vector3f());
            Quaternionf bodyWorldRot = transform.getRotation(new Quaternionf());

            for (VxSeat seat : seats.values()) {

                if (ridingManager.isSeatOccupied(objectId, seat)) {
                    continue;
                }

                Vector3f seatOffset = new Vector3f(seat.getRiderOffset());
                bodyWorldRot.transform(seatOffset);
                seatOffset.add(bodyWorldPos);

                Vec3 seatWorldPos = new Vec3(seatOffset.x, seatOffset.y, seatOffset.z);

                double distanceSq = playerPos.distanceToSqr(seatWorldPos);
                if (distanceSq < minDistanceSq) {
                    minDistanceSq = distanceSq;
                    closestRideable = rideable;
                    closestSeat = seat;
                }
            }
        }

        if (closestRideable != null && closestSeat != null) {
            ridingManager.startRiding(player, closestRideable, closestSeat);
            source.sendSuccess(() -> Component.literal("Sitting down on the nearest seat."), false);
            return 1;
        } else {
            source.sendFailure(Component.literal("No free seats found in this dimension."));
            return 0;
        }
    }
}