/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.config.subsystems;

import net.xmx.velthoric.config.VxConfigSpec;
import net.xmx.velthoric.config.VxConfigValue;

/**
 * Configuration container for the Core Physics Simulation.
 *
 * @author xI-Mx-Ix
 */
public class VxPhysicsConfig {

    public final VxConfigValue<Integer> maxBodies;
    public final VxConfigValue<Integer> maxBodyPairs;
    public final VxConfigValue<Integer> maxContactConstraints;
    public final VxConfigValue<Integer> numPositionIterations;
    public final VxConfigValue<Integer> numVelocityIterations;
    public final VxConfigValue<Double> speculativeContactDistance;
    public final VxConfigValue<Double> baumgarteFactor;
    public final VxConfigValue<Double> penetrationSlop;
    public final VxConfigValue<Double> timeBeforeSleep;
    public final VxConfigValue<Double> pointVelocitySleepThreshold;
    public final VxConfigValue<Double> gravityY;
    public final VxConfigValue<Integer> tempAllocatorSize;

    public VxPhysicsConfig(VxConfigSpec.Builder builder) {
        this.maxBodies = builder.defineInRange("max_bodies", 65536, 1000, 1000000, "Maximum number of rigid bodies in the simulation.");
        this.maxBodyPairs = builder.defineInRange("max_body_pairs", 65536, 1000, 1000000, "Maximum number of body pairs for collision cache.");
        this.maxContactConstraints = builder.defineInRange("max_contact_constraints", 65536, 1000, 1000000, "Buffer size for contact constraints.");
        
        this.numPositionIterations = builder.defineInRange("position_iterations", 10, 1, 100, "Number of solver iterations for position (collision resolution). Higher = more stable, slower.");
        this.numVelocityIterations = builder.defineInRange("velocity_iterations", 15, 1, 100, "Number of solver iterations for velocity. Higher = more accurate bounce/friction.");
        
        this.speculativeContactDistance = builder.define("speculative_contact_distance", 0.02d, "Distance to detect contacts before they actually touch (prevents tunneling).");
        this.baumgarteFactor = builder.defineInRange("baumgarte_factor", 0.2d, 0.01d, 1.0d, "Stabilization factor for constraints (0.1 - 0.5 recommended).");
        this.penetrationSlop = builder.define("penetration_slop", 0.001d, "Allowed penetration depth before solver pushes bodies apart.");
        
        this.timeBeforeSleep = builder.define("time_before_sleep", 1.0d, "Time in seconds a body must be motionless before going to sleep.");
        this.pointVelocitySleepThreshold = builder.define("sleep_velocity_threshold", 0.005d, "Velocity below which a body is considered motionless.");
        
        this.gravityY = builder.define("gravity_y", -9.81d, "Global gravity force on the Y axis.");
        
        this.tempAllocatorSize = builder.define("temp_allocator_size_bytes", 64 * 1024 * 1024, "Size of the Jolt temporary allocator in bytes (Default 64MB).");
    }
}