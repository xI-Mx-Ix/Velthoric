/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.constraint;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EConstraintSubType;
import com.github.stephengold.joltjni.enumerate.EStreamType;
import com.github.stephengold.joltjni.std.StringStream;
import net.xmx.velthoric.core.constraint.manager.VxConstraintManager;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Represents a physics constraint that connects two bodies.
 * This class stores identification data for the constraint and its connected bodies,
 * as well as the serialized settings required to recreate the constraint in the physics world.
 *
 * @author xI-Mx-Ix
 */
public class VxConstraint {

    /**
     * The unique identifier for this specific constraint instance.
     * Used for lookups, network synchronization, and persistence keys.
     */
    private final UUID constraintId;

    /**
     * The UUID of the first physics body involved in this constraint.
     * This acts as the primary anchor for the constraint connection.
     */
    private final UUID body1Id;

    /**
     * The UUID of the second physics body involved in this constraint.
     * <p>
     * Can be set to {@link VxConstraintManager#WORLD_BODY_ID}
     * to indicate a connection to the static world geometry (e.g., pinning an object to the ground).
     */
    private final UUID body2Id;

    /**
     * The serialized configuration data for this constraint.
     * <p>
     * Contains the binary representation of the Jolt {@code TwoBodyConstraintSettings},
     * including pivot points, axes, limits, and motor settings.
     * This array is used to reconstruct the native constraint object when loading.
     */
    private byte[] settingsData;

    /**
     * The specific type classification of this constraint (e.g., Hinge, Slider, Fixed).
     * Used to determine the correct settings class during deserialization.
     */
    private final EConstraintSubType subType;

    /**
     * A reference to the active, native Jolt physics constraint object.
     * <p>
     * This field is {@code transient} as it represents a runtime handle that cannot be serialized.
     * It is {@code null} if the constraint has not yet been activated in the physics world
     * (e.g., waiting for dependent bodies to load).
     */
    @Nullable
    private transient TwoBodyConstraint joltConstraint;

    /**
     * Flag indicating whether this constraint should be serialized to disk.
     * <p>
     * If {@code true}, the constraint is saved when the associated chunk is unloaded or saved.
     * If {@code false}, the constraint is considered temporary (e.g., for visual effects or
     * short-lived interactions) and will be discarded upon unload.
     * Defaults to {@code true}.
     */
    private boolean persistent = true;

    /**
     * Constructs a new constraint instance from provided settings.
     * The settings are serialized immediately for storage.
     *
     * @param constraintId The unique ID for this constraint.
     * @param body1Id      The UUID of the first body.
     * @param body2Id      The UUID of the second body.
     * @param settings     The Jolt constraint settings.
     */
    public VxConstraint(UUID constraintId, UUID body1Id, UUID body2Id, TwoBodyConstraintSettings settings) {
        this.constraintId = constraintId;
        this.body1Id = body1Id;
        this.body2Id = body2Id;
        this.subType = getSubTypeFromSettings(settings);
        this.settingsData = serializeSettings(settings);
    }

    /**
     * Constructs a constraint instance from stored data.
     *
     * @param constraintId The unique ID for this constraint.
     * @param body1Id      The UUID of the first body.
     * @param body2Id      The UUID of the second body.
     * @param settingsData The serialized settings data.
     * @param subType      The subtype of the constraint.
     */
    public VxConstraint(UUID constraintId, UUID body1Id, UUID body2Id, byte[] settingsData, EConstraintSubType subType) {
        this.constraintId = constraintId;
        this.body1Id = body1Id;
        this.body2Id = body2Id;
        this.settingsData = settingsData;
        this.subType = subType;
    }

    /**
     * Returns the unique identifier of the constraint.
     * @return The constraint UUID.
     */
    public UUID getConstraintId() {
        return constraintId;
    }

    /**
     * Returns the unique identifier of the first body.
     * @return The first body's UUID.
     */
    public UUID getBody1Id() {
        return body1Id;
    }

    /**
     * Returns the unique identifier of the second body.
     * @return The second body's UUID.
     */
    public UUID getBody2Id() {
        return body2Id;
    }

    /**
     * Returns the raw serialized data for the constraint's settings.
     * @return A byte array of the settings data.
     */
    public byte[] getSettingsData() {
        return settingsData;
    }

    /**
     * Returns the specific subtype of the constraint.
     * @return The EConstraintSubType enum value.
     */
    public EConstraintSubType getSubType() {
        return subType;
    }

    /**
     * Sets the persistence state of this constraint.
     *
     * @param persistent true to enable saving to disk, false to make the constraint temporary.
     */
    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    /**
     * Checks if this constraint is marked for persistence.
     *
     * @return true if the constraint should be saved to disk, false otherwise.
     */
    public boolean isPersistent() {
        return persistent;
    }

    /**
     * Returns the underlying Jolt physics constraint object if it has been created.
     * @return The Jolt constraint, or null if not activated.
     */
    @Nullable
    public TwoBodyConstraint getJoltConstraint() {
        return joltConstraint;
    }

    /**
     * Sets the underlying Jolt physics constraint object.
     * @param joltConstraint The activated Jolt constraint.
     */
    public void setJoltConstraint(@Nullable TwoBodyConstraint joltConstraint) {
        this.joltConstraint = joltConstraint;
    }

    /**
     * Updates the internal settings data by serializing the provided settings object.
     * This is typically used after Jolt modifies settings, e.g., converting from world to local space.
     *
     * @param newSettings The new settings to serialize and store.
     */
    public void updateSettingsData(TwoBodyConstraintSettings newSettings) {
        this.settingsData = serializeSettings(newSettings);
    }

    /**
     * Serializes constraint settings into a byte array using Jolt's object stream.
     *
     * @param settings The settings to serialize.
     * @return A byte array containing the serialized data.
     */
    private byte[] serializeSettings(TwoBodyConstraintSettings settings) {
        try (StringStream stringStream = new StringStream()) {
            if (ObjectStreamOut.sWriteObject(stringStream, EStreamType.Text, settings)) {
                return stringStream.str().getBytes(StandardCharsets.ISO_8859_1);
            }
        }
        throw new IllegalStateException("Failed to serialize constraint settings.");
    }

    /**
     * Determines the constraint subtype from a settings object instance.
     *
     * @param settings The constraint settings object.
     * @return The corresponding EConstraintSubType.
     * @throws IllegalArgumentException if the settings type is unknown or unsupported.
     */
    private EConstraintSubType getSubTypeFromSettings(TwoBodyConstraintSettings settings) {
        if (settings instanceof HingeConstraintSettings) return EConstraintSubType.Hinge;
        if (settings instanceof SixDofConstraintSettings) return EConstraintSubType.SixDof;
        if (settings instanceof ConeConstraintSettings) return EConstraintSubType.Cone;
        if (settings instanceof DistanceConstraintSettings) return EConstraintSubType.Distance;
        if (settings instanceof FixedConstraintSettings) return EConstraintSubType.Fixed;
        if (settings instanceof GearConstraintSettings) return EConstraintSubType.Gear;
        if (settings instanceof PathConstraintSettings) return EConstraintSubType.Path;
        if (settings instanceof PointConstraintSettings) return EConstraintSubType.Point;
        if (settings instanceof PulleyConstraintSettings) return EConstraintSubType.Pulley;
        if (settings instanceof RackAndPinionConstraintSettings) return EConstraintSubType.RackAndPinion;
        if (settings instanceof SliderConstraintSettings) return EConstraintSubType.Slider;
        if (settings instanceof SwingTwistConstraintSettings) return EConstraintSubType.SwingTwist;
        throw new IllegalArgumentException("Unknown or unsupported constraint settings type: " + settings.getClass().getName());
    }
}