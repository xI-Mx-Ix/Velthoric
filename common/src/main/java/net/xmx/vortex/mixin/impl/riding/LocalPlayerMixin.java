package net.xmx.vortex.mixin.impl.riding;

import net.minecraft.client.player.LocalPlayer;
import net.xmx.vortex.physics.object.riding.PlayerRidingAttachment;
import net.xmx.vortex.physics.object.riding.util.PlayerRidingAware;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin extends Player implements PlayerRidingAware {

    public LocalPlayerMixin(Level level, BlockPos pos, float yRot, GameProfile profile) {
        super(level, pos, yRot, profile);
    }

    @Unique
    private final PlayerRidingAttachment vortex$ridingAttachment = new PlayerRidingAttachment();

    @Override
    public PlayerRidingAttachment getPlayerRidingAttachment() {
        return vortex$ridingAttachment;
    }
}