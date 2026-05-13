/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.physics.ignore;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a persistent collision filter between two physics bodies.
 * <p>
 * This object is a simple data container used by the persistence system
 * to track which bodies should ignore each other.
 *
 * @author xI-Mx-Ix
 */
public final class VxBodyPairIgnore {

    /**
     * The UUID of the first body in the pair.
     * <p>
     * Note: The internal ID ordering is normalized during construction to ensure
     * that (BodyA, BodyB) is treated identically to (BodyB, BodyA).
     */
    private final UUID body1Id;

    /**
     * The UUID of the second body in the pair.
     */
    private final UUID body2Id;

    /**
     * Constructs a new collision ignore pair between two unique identifiers.
     * <p>
     * The constructor automatically sorts the IDs lexicographically to maintain
     * canonical ordering, allowing for consistent hashing and equality checks.
     *
     * @param body1Id Unique identifier of the first participant.
     * @param body2Id Unique identifier of the second participant.
     */
    public VxBodyPairIgnore(UUID body1Id, UUID body2Id) {
        // Ensure consistent ordering for easier comparison (lexicographical)
        if (body1Id.compareTo(body2Id) < 0) {
            this.body1Id = body1Id;
            this.body2Id = body2Id;
        } else {
            this.body1Id = body2Id;
            this.body2Id = body1Id;
        }
    }

    /**
     * @return The canonical identifier of the first body in this pair.
     */
    public UUID getBody1Id() {
        return body1Id;
    }

    /**
     * @return The canonical identifier of the second body in this pair.
     */
    public UUID getBody2Id() {
        return body2Id;
    }

    /**
     * Compares this ignore pair to another object for equality.
     * <p>
     * Since the IDs are normalized at construction, this method effectively
     * detects identity regardless of the original parameter order.
     *
     * @param o The object to compare against.
     * @return {@code true} if both pairs involve the same two UUIDs.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VxBodyPairIgnore that = (VxBodyPairIgnore) o;
        return Objects.equals(body1Id, that.body1Id) && Objects.equals(body2Id, that.body2Id);
    }

    /**
     * Generates a hash code based on the normalized ID pair.
     *
     * @return A stable hash code for use in collections.
     */
    @Override
    public int hashCode() {
        return Objects.hash(body1Id, body2Id);
    }

    /**
     * @return A human-readable representation of the ignore pair.
     */
    @Override
    public String toString() {
        return "VxBodyPairIgnore{" +
                "body1=" + body1Id +
                ", body2=" + body2Id +
                '}';
    }
}