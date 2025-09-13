/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.raycasting;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;

public class VxClipContext extends ClipContext {

    private final boolean includePhysics;
    private final boolean includeSeats;
    private final Entity entity;

    public VxClipContext(Vec3 from, Vec3 to, Block block, Fluid fluid, Entity entity, boolean includePhysics) {
        this(from, to, block, fluid, entity, includePhysics, false);
    }

    public VxClipContext(Vec3 from, Vec3 to, Block block, Fluid fluid, Entity entity, boolean includePhysics, boolean includeSeats) {
        super(from, to, block, fluid, entity);
        this.entity = entity;
        this.includePhysics = includePhysics;
        this.includeSeats = includeSeats;
    }

    public boolean isIncludePhysics() {
        return this.includePhysics;
    }

    public boolean isIncludeSeats() {
        return this.includeSeats;
    }

    public Entity getEntity() {
        return this.entity;
    }
}
