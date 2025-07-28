package net.xmx.vortex.physics.object.riding;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.operator.Op;
import dev.architectury.event.events.client.ClientTickEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.xmx.vortex.physics.object.riding.util.PlayerRidingAware;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ClientPlayerRidingSystem {

    public static void registerEvents() {
        ClientTickEvent.CLIENT_PRE.register(ClientPlayerRidingSystem::onClientPreTick);
    }

    private static void onClientPreTick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) return;

        PlayerRidingAttachment attachment = getAttachment(player);
        if (attachment == null) return;

        if (player.getVehicle() instanceof RidingProxyEntity proxy) {
            if (!attachment.isRiding()) {
                startRiding(player, proxy, attachment);
            }
            attachment.setCurrentProxy(proxy);
        } else {
            if (attachment.isRiding()) {
                stopRiding(attachment);
            }
        }
    }

    @Nullable
    public static PlayerRidingAttachment getAttachment(Entity player) {
        if (player instanceof PlayerRidingAware) {
            return ((PlayerRidingAware) player).getPlayerRidingAttachment();
        }
        return null;
    }

    private static void startRiding(LocalPlayer player, RidingProxyEntity proxy, PlayerRidingAttachment attachment) {
        attachment.setRiding(true);
        attachment.setCurrentProxy(proxy);

        proxy.getInterpolatedTransform().ifPresent(transform -> {
            Quat physicsObjectRotation = transform.getRotation();
            Quat playerWorldRotation = Quat.sEulerAngles(
                    (float) Math.toRadians(player.getXRot()),
                    (float) Math.toRadians(player.getYRot()),
                    0
            );
            attachment.localLookRotation.set(Op.star(physicsObjectRotation.conjugated(), playerWorldRotation));
        });
    }

    private static void stopRiding(PlayerRidingAttachment attachment) {
        attachment.setRiding(false);
        attachment.setCurrentProxy(null);
    }
}