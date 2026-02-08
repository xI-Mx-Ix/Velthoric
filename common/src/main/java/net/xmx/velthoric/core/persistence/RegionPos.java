/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.persistence;

/**
 * Represents the coordinate of a region file in the world.
 * Each region covers an area of 32x32 chunks.
 *
 * @param x The region x-coordinate (chunkX >> 5).
 * @param z The region z-coordinate (chunkZ >> 5).
 * @author xI-Mx-Ix
 */
public record RegionPos(int x, int z) {
}