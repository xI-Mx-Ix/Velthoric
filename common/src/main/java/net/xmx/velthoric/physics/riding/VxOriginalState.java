/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.riding;

import net.minecraft.world.entity.Entity;

public class VxOriginalState {
    double xo, yo, zo;
    double x, y, z;
    double xOld, yOld, zOld;

    public void setFrom(Entity entity) {
        this.xo = entity.xo;
        this.yo = entity.yo;
        this.zo = entity.zo;
        this.x = entity.getX();
        this.y = entity.getY();
        this.z = entity.getZ();
        this.xOld = entity.xOld;
        this.yOld = entity.yOld;
        this.zOld = entity.zOld;
    }

    public void applyTo(Entity entity) {
        entity.xo = this.xo;
        entity.yo = this.yo;
        entity.zo = this.zo;
        entity.setPos(this.x, this.y, this.z);
        entity.xOld = this.xOld;
        entity.yOld = this.yOld;
        entity.zOld = this.zOld;
    }
}