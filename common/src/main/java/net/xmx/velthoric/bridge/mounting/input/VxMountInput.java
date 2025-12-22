/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.bridge.mounting.input;

import net.minecraft.network.FriendlyByteBuf;
import java.util.Objects;

/**
 * Represents the normalized input state of a mounted entity.
 * Uses float values for analog axes (throttle, steering) and a bitmask
 * for boolean actions (handbrake, shifting, etc.).
 *
 * @author xI-Mx-Ix
 */
public class VxMountInput {

    /**
     * A default neutral input state with no movement or actions.
     */
    public static final VxMountInput NEUTRAL = new VxMountInput(0.0f, 0.0f, 0);

    // --- Action Flags ---
    /**
     * Flag indicating the handbrake (or jump) action.
     */
    public static final int FLAG_HANDBRAKE = 1;
    /**
     * Flag indicating a request to shift up a gear.
     */
    public static final int FLAG_SHIFT_UP = 1 << 1;
    /**
     * Flag indicating a request to shift down a gear.
     */
    public static final int FLAG_SHIFT_DOWN = 1 << 2;
    /**
     * Flag indicating a special action 1 (e.g., Horn).
     */
    public static final int FLAG_SPECIAL_1 = 1 << 3;

    // Axis values range from -1.0 to 1.0
    private final float forwardAmount; // +1.0 = Full Forward, -1.0 = Full Backward
    private final float rightAmount;   // +1.0 = Full Right, -1.0 = Full Left
    private final int actionFlags;     // Bitmask for boolean actions

    /**
     * Constructs a new input state.
     *
     * @param forwardAmount The forward throttle amount (-1.0 to 1.0).
     * @param rightAmount   The steering amount (-1.0 to 1.0).
     * @param actionFlags   The bitmask of active action flags.
     */
    public VxMountInput(float forwardAmount, float rightAmount, int actionFlags) {
        this.forwardAmount = forwardAmount;
        this.rightAmount = rightAmount;
        this.actionFlags = actionFlags;
    }

    /**
     * decodes the input state from a network buffer.
     *
     * @param buf The buffer to read from.
     */
    public VxMountInput(FriendlyByteBuf buf) {
        this.forwardAmount = buf.readFloat();
        this.rightAmount = buf.readFloat();
        this.actionFlags = buf.readVarInt();
    }

    /**
     * Encodes the input state into a network buffer.
     *
     * @param buf The buffer to write to.
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(this.forwardAmount);
        buf.writeFloat(this.rightAmount);
        buf.writeVarInt(this.actionFlags);
    }

    /**
     * Gets the forward/backward input amount.
     * Positive values indicate forward, negative values indicate backward.
     *
     * @return The forward amount, typically between -1.0 and 1.0.
     */
    public float getForwardAmount() {
        return forwardAmount;
    }

    /**
     * Gets the left/right steering input amount.
     * Positive values indicate right, negative values indicate left.
     *
     * @return The steering amount, typically between -1.0 and 1.0.
     */
    public float getRightAmount() {
        return rightAmount;
    }

    /**
     * Checks if a specific action flag is active in this input state.
     *
     * @param flag The flag constant to check.
     * @return True if the flag bit is set, false otherwise.
     */
    public boolean hasAction(int flag) {
        return (actionFlags & flag) != 0;
    }

    /**
     * Gets the raw bitmask of action flags.
     *
     * @return The action flags integer.
     */
    public int getActionFlags() {
        return actionFlags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VxMountInput that = (VxMountInput) o;
        return Float.compare(that.forwardAmount, forwardAmount) == 0 &&
                Float.compare(that.rightAmount, rightAmount) == 0 &&
                actionFlags == that.actionFlags;
    }

    @Override
    public int hashCode() {
        return Objects.hash(forwardAmount, rightAmount, actionFlags);
    }
}