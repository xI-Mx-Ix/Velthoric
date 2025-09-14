/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.util.ship.sound;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.ship.plot.ShipPlotInfo;
import net.xmx.velthoric.ship.plot.VxClientPlotManager;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.UUID;

public class VxSoundOnShip extends AbstractTickableSoundInstance {

    private final UUID shipId;
    private final Vec3 localPos;
    private final Vector3d velocity = new Vector3d();

    public VxSoundOnShip(SoundEvent soundEvent, SoundSource soundSource, float volume, float pitch, RandomSource random, Vec3 localPos, UUID shipId) {
        super(soundEvent, soundSource, random);
        this.volume = volume;
        this.pitch = pitch;
        this.shipId = shipId;
        this.localPos = localPos;
        this.looping = false;
        this.delay = 0;
        this.attenuation = Attenuation.LINEAR;
        this.relative = false;
        tick(); // Initial tick to set position
    }

    @Override
    public void tick() {
        VxClientObjectManager objectManager = VxClientObjectManager.getInstance();
        var index = objectManager.getStore().getIndexForId(shipId);
        if (index == null) {
            this.stop();
            return;
        }

        ShipPlotInfo plotInfo = VxClientPlotManager.getInstance().getShipInfoForShip(shipId);
        if (plotInfo == null) {
            this.stop();
            return;
        }

        RVec3 shipPos = new RVec3();
        Quat shipRot = new Quat();
        float partialTick = Minecraft.getInstance().getFrameTime();
        objectManager.getInterpolator().interpolateFrame(objectManager.getStore(), index, partialTick, shipPos, shipRot);

        BlockPos plotOrigin = plotInfo.plotCenter().getWorldPosition();

        Vector3d worldPosVec = new Vector3d(localPos.x - plotOrigin.getX(), localPos.y - plotOrigin.getY(), localPos.z - plotOrigin.getZ());
        new Quaterniond(shipRot.getX(), shipRot.getY(), shipRot.getZ(), shipRot.getW()).transform(worldPosVec);
        worldPosVec.add(shipPos.x(), shipPos.y(), shipPos.z());

        this.x = worldPosVec.x;
        this.y = worldPosVec.y;
        this.z = worldPosVec.z;

        float shipVelX = objectManager.getStore().state1_velX[index];
        float shipVelY = objectManager.getStore().state1_velY[index];
        float shipVelZ = objectManager.getStore().state1_velZ[index];
        this.velocity.set(shipVelX, shipVelY, shipVelZ);
    }

    public Vector3d getVelocity() {
        return this.velocity;
    }
}