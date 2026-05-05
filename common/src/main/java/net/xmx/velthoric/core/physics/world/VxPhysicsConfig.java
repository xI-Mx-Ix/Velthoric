/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.physics.world;

/**
 * Configuration parameters for the physics simulation.
 * Contains settings for memory limits, collision tolerances,
 * and solver iterations which affect the stability and performance
 * of the Jolt physics engine.
 *
 * @author xI-Mx-Ix
 */
public class VxPhysicsConfig {
    /**
     * The maximum number of physics bodies supported in a single world.
     */
    public int maxBodies = 65536;

    /**
     * The maximum number of simultaneous overlapping body pairs supported.
     */
    public int maxBodyPairs = 65536;

    /**
     * The maximum number of contact constraints (points of collision) supported.
     */
    public int maxContactConstraints = 65536;

    /**
     * The number of position correction iterations per step.
     */
    public int numPositionIterations = 10;

    /**
     * The number of velocity solver iterations per step.
     */
    public int numVelocityIterations = 15;

    /**
     * The distance at which the solver starts considering contacts for continuous collision detection.
     */
    public float speculativeContactDistance = 0.02f;

    /**
     * The Baumgarte stabilization factor for resolving position errors.
     */
    public float baumgarteFactor = 0.2f;

    /**
     * The allowed amount of penetration between bodies before the solver applies correction.
     */
    public float penetrationSlop = 0.02f;

    /**
     * The duration a body must remain nearly stationary before it is put to sleep.
     */
    public float timeBeforeSleep = 1.0f;

    /**
     * The linear velocity threshold below which a body is considered stationary for sleeping.
     */
    public float pointVelocitySleepThreshold = 0.005f;

    /**
     * The default gravity acceleration applied to all dynamic bodies in the world.
     */
    public float gravityY = -9.81f;

    /**
     * The size (in bytes) of the pre-allocated temporary memory pool for the physics solver.
     * Default is 64MB.
     */
    public int tempAllocatorSize = 64 * 1024 * 1024;

    public VxPhysicsConfig maxBodies(int maxBodies) {
        this.maxBodies = maxBodies;
        return this;
    }

    public VxPhysicsConfig maxBodyPairs(int maxBodyPairs) {
        this.maxBodyPairs = maxBodyPairs;
        return this;
    }

    public VxPhysicsConfig maxContactConstraints(int maxContactConstraints) {
        this.maxContactConstraints = maxContactConstraints;
        return this;
    }

    public VxPhysicsConfig numPositionIterations(int numPositionIterations) {
        this.numPositionIterations = numPositionIterations;
        return this;
    }

    public VxPhysicsConfig numVelocityIterations(int numVelocityIterations) {
        this.numVelocityIterations = numVelocityIterations;
        return this;
    }

    public VxPhysicsConfig speculativeContactDistance(float speculativeContactDistance) {
        this.speculativeContactDistance = speculativeContactDistance;
        return this;
    }

    public VxPhysicsConfig baumgarteFactor(float baumgarteFactor) {
        this.baumgarteFactor = baumgarteFactor;
        return this;
    }

    public VxPhysicsConfig penetrationSlop(float penetrationSlop) {
        this.penetrationSlop = penetrationSlop;
        return this;
    }

    public VxPhysicsConfig timeBeforeSleep(float timeBeforeSleep) {
        this.timeBeforeSleep = timeBeforeSleep;
        return this;
    }

    public VxPhysicsConfig pointVelocitySleepThreshold(float pointVelocitySleepThreshold) {
        this.pointVelocitySleepThreshold = pointVelocitySleepThreshold;
        return this;
    }

    public VxPhysicsConfig gravityY(float gravityY) {
        this.gravityY = gravityY;
        return this;
    }

    public VxPhysicsConfig tempAllocatorSize(int tempAllocatorSize) {
        this.tempAllocatorSize = tempAllocatorSize;
        return this;
    }
}