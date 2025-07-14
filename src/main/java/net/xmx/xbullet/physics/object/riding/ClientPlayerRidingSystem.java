package net.xmx.xbullet.physics.object.riding;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.operator.Op;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

@OnlyIn(Dist.CLIENT)
public class ClientPlayerRidingSystem {

    private static boolean isRidingPhysicsObject = false;
    private static final Quat localLookRotation = new Quat();
    @Nullable
    private static RidingProxyEntity currentProxy = null;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;

        if (player == null) {
            if (isRidingPhysicsObject) stopRiding();
            return;
        }

        if (player.getVehicle() instanceof RidingProxyEntity proxy) {
            if (!isRidingPhysicsObject) {
                startRiding(player, proxy);
            }
            currentProxy = proxy;
        } else {
            if (isRidingPhysicsObject) {
                stopRiding();
            }
        }
    }

    private static void startRiding(LocalPlayer player, RidingProxyEntity proxy) {
        isRidingPhysicsObject = true;
        currentProxy = proxy;

        proxy.getInterpolatedTransform().ifPresent(transform -> {
            Quat physicsObjectRotation = transform.getRotation();
            Quat playerWorldRotation = Quat.sEulerAngles(
                    (float) Math.toRadians(player.getXRot()),
                    (float) Math.toRadians(player.getYRot()),
                    0
            );

            localLookRotation.set(Op.star(physicsObjectRotation.conjugated(), playerWorldRotation));
        });
    }

    private static void stopRiding() {
        isRidingPhysicsObject = false;
        currentProxy = null;
    }

    public static void updateLocalLook(double dx, double dy) {
        if (!isRidingPhysicsObject) return;

        float rotX = (float) (dy * 0.003);
        float rotY = (float) (dx * 0.003);

        Quat yawQuat = new Quat(new Vec3(0, 1, 0), -rotY);
        localLookRotation.set(Op.star(localLookRotation, yawQuat));

        Quat pitchQuat = new Quat(new Vec3(1, 0, 0), -rotX);
        localLookRotation.set(Op.star(pitchQuat, localLookRotation));

        localLookRotation.set(localLookRotation.normalized());
    }

    public static boolean isRiding() {
        return isRidingPhysicsObject;
    }

    public static Optional<Quat> getTargetWorldRotation() {
        if (!isRiding() || currentProxy == null) {
            return Optional.empty();
        }

        return currentProxy.getInterpolatedTransform().map(transform ->
                Op.star(transform.getRotation(), localLookRotation)
        );
    }

    public static Optional<Vec3> getPhysicsObjectUpVector() {
        if (!isRiding() || currentProxy == null) {
            return Optional.empty();
        }

        return currentProxy.getInterpolatedTransform().map(transform ->
                transform.getRotation().rotateAxisY()
        );
    }
}