package net.xmx.vortex.physics.terrain.model;

public record VxGreedyBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public int width() {
        return maxX - minX;
    }

    public int height() {
        return maxY - minY;
    }

    public int depth() {
        return maxZ - minZ;
    }
}