/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.mounting.input;

import net.minecraft.network.FriendlyByteBuf;
import java.util.Objects;

/**
 * Represents the input state of a mounted entity.
 * This class is used to encode and decode the input state of a mounted entity.
 *
 * @author xI-Mx-Ix
 */
public class VxMountInput {

    public static final VxMountInput NEUTRAL = new VxMountInput(false, false, false, false, false, false);

    private final boolean forward;
    private final boolean backward;
    private final boolean left;
    private final boolean right;
    private final boolean up;
    private final boolean down;

    public VxMountInput(boolean forward, boolean backward, boolean left, boolean right, boolean up, boolean down) {
        this.forward = forward;
        this.backward = backward;
        this.left = left;
        this.right = right;
        this.up = up;
        this.down = down;
    }

    public VxMountInput(FriendlyByteBuf buf) {
        int packed = buf.readByte();
        this.forward = (packed & 1) != 0;
        this.backward = (packed & 2) != 0;
        this.left = (packed & 4) != 0;
        this.right = (packed & 8) != 0;
        this.up = (packed & 16) != 0;
        this.down = (packed & 32) != 0;
    }

    public void encode(FriendlyByteBuf buf) {
        int packed = 0;
        if (this.forward) packed |= 1;
        if (this.backward) packed |= 2;
        if (this.left) packed |= 4;
        if (this.right) packed |= 8;
        if (this.up) packed |= 16;
        if (this.down) packed |= 32;
        buf.writeByte(packed);
    }

    public boolean isForward()  {
        return forward;
    }

    public boolean isBackward() {
        return backward;
    }

    public boolean isLeft() {
        return left;
    }

    public boolean isRight() {
        return right;
    }

    public boolean isUp() {
        return up;
    }

    public boolean isDown() {
        return down;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VxMountInput rideInput = (VxMountInput) o;
        return forward == rideInput.forward &&
                backward == rideInput.backward &&
                left == rideInput.left &&
                right == rideInput.right &&
                up == rideInput.up &&
                down == rideInput.down;
    }

    @Override
    public int hashCode() {
        return Objects.hash(forward, backward, left, right, up, down);
    }
}