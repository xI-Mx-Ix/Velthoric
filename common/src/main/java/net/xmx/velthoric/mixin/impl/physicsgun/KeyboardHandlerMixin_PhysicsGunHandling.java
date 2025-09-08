package net.xmx.velthoric.mixin.impl.physicsgun;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.xmx.velthoric.init.registry.ItemRegistry;
import net.xmx.velthoric.item.physicsgun.manager.PhysicsGunClientManager;
import net.xmx.velthoric.item.physicsgun.packet.PhysicsGunActionPacket;
import net.xmx.velthoric.network.NetworkHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin_PhysicsGunHandling {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void onKeyPress(long pWindowPointer, int pKey, int pScanCode, int pAction, int pModifiers, CallbackInfo ci) {
        if (this.minecraft.screen != null) {
            return;
        }

        var player = this.minecraft.player;
        if (player == null) return;

        boolean isHoldingGun = player.getMainHandItem().is(ItemRegistry.PHYSICS_GUN.get()) || player.getOffhandItem().is(ItemRegistry.PHYSICS_GUN.get());
        if (!isHoldingGun) return;

        var clientManager = PhysicsGunClientManager.getInstance();

        if (clientManager.isGrabbing(player) && pKey == GLFW.GLFW_KEY_E) {
            if (pAction == GLFW.GLFW_PRESS) {
                if (!clientManager.isRotationMode()) {
                    clientManager.setRotationMode(true);
                    NetworkHandler.sendToServer(new PhysicsGunActionPacket(PhysicsGunActionPacket.ActionType.START_ROTATION_MODE));
                }
            } else if (pAction == GLFW.GLFW_RELEASE) {
                if (clientManager.isRotationMode()) {
                    clientManager.setRotationMode(false);
                    NetworkHandler.sendToServer(new PhysicsGunActionPacket(PhysicsGunActionPacket.ActionType.STOP_ROTATION_MODE));
                }
            }

            ci.cancel();
        }
    }
}