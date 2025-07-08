package net.xmx.xbullet.physics.object.riding.event;

import com.github.stephengold.joltjni.RMat44;
import com.github.stephengold.joltjni.Mat44;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.xmx.xbullet.math.PhysicsOperations;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.network.NetworkHandler;
import net.xmx.xbullet.physics.object.physicsobject.client.ClientPhysicsObjectData;
import net.xmx.xbullet.physics.object.physicsobject.client.ClientPhysicsObjectManager;
import net.xmx.xbullet.physics.object.riding.ClientRidingCache;
import net.xmx.xbullet.physics.object.riding.packet.DismountRequestPacket;
import org.joml.Vector3d;

public class ClientRidingEvents {

    private static boolean wasSneaking = false;

    private static final Vector3d originalPlayerPos = new Vector3d();
    private static float originalPlayerYRot, originalPlayerXRot, originalPlayerYBodyRot, originalPlayerYHeadRot;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        boolean isSneaking = player.isShiftKeyDown();

        if (isSneaking && !wasSneaking && player.isVehicle() && ClientRidingCache.isRiding(player.getUUID())) {
            NetworkHandler.CHANNEL.sendToServer(new DismountRequestPacket());
        }
        wasSneaking = isSneaking;
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        ClientRidingCache.RidingInfo ridingInfo = ClientRidingCache.getRidingInfo(player.getUUID());
        if (ridingInfo == null) return;

        ClientPhysicsObjectData parentObjectData = ClientPhysicsObjectManager.getInstance().getObjectData(ridingInfo.physicsObjectId());
        if (parentObjectData == null || parentObjectData.getRigidData() == null) {
            return;
        }

        originalPlayerPos.set(player.getX(), player.getY(), player.getZ());
        originalPlayerYRot = player.getYRot();
        originalPlayerXRot = player.getXRot();
        originalPlayerYBodyRot = player.yBodyRot;
        originalPlayerYHeadRot = player.yHeadRot;

        PhysicsTransform parentTransform = parentObjectData.getRigidData().getRenderTransform(event.getPartialTick());
        PhysicsTransform finalWorldTransform = calculateWorldTransform(parentTransform, ridingInfo.relativeSeatTransform());

        var pos = finalWorldTransform.getTranslation();
        var rot = finalWorldTransform.getRotation();

        float bodyYaw = (float) Math.toDegrees(PhysicsOperations.quatToEulerAngles(rot).getY());

        player.setPos(pos.xx(), pos.yy() - player.getEyeHeight(), pos.zz());
        player.setYRot(bodyYaw);
        player.yBodyRot = bodyYaw;
        player.yHeadRot = bodyYaw;

    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        Player player = event.getEntity();
        ClientRidingCache.RidingInfo ridingInfo = ClientRidingCache.getRidingInfo(player.getUUID());
        if (ridingInfo == null) return;

        player.setPos(originalPlayerPos.x, originalPlayerPos.y, originalPlayerPos.z);
        player.setYRot(originalPlayerYRot);
        player.setXRot(originalPlayerXRot);
        player.yBodyRot = originalPlayerYBodyRot;
        player.yHeadRot = originalPlayerYHeadRot;
    }

    private static PhysicsTransform calculateWorldTransform(PhysicsTransform parentTransform, PhysicsTransform relativeTransform) {

        RMat44 parentMatrix = RMat44.sRotationTranslation(parentTransform.getRotation(), parentTransform.getTranslation());
        Mat44 relativeMatrix = Mat44.sRotationTranslation(relativeTransform.getRotation(), relativeTransform.getTranslation().toVec3());
        RMat44 finalMatrix = parentMatrix.multiply(relativeMatrix);
        PhysicsTransform result = new PhysicsTransform(finalMatrix.getTranslation(), finalMatrix.getQuaternion());
        finalMatrix.close();
        relativeMatrix.close();
        parentMatrix.close();
        return result;
    }
}