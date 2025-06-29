package net.xmx.xbullet.debug.drawer;

import com.github.stephengold.joltjni.readonly.RVec3Arg;

public class DebugLine {
    public final double fromX, fromY, fromZ;
    public final double toX, toY, toZ;
    public final int color;

    public DebugLine(RVec3Arg from, RVec3Arg to, int color) {
        this.fromX = from.xx();
        this.fromY = from.yy();
        this.fromZ = from.zz();
        this.toX = to.xx();
        this.toY = to.yy();
        this.toZ = to.zz();
        this.color = color;
    }

    public DebugLine(double fromX, double fromY, double fromZ, double toX, double toY, double toZ, int color) {
        this.fromX = fromX;
        this.fromY = fromY;
        this.fromZ = fromZ;
        this.toX = toX;
        this.toY = toY;
        this.toZ = toZ;
        this.color = color;
    }
}