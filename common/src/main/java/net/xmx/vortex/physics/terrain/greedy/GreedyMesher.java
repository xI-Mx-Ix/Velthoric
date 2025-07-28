package net.xmx.vortex.physics.terrain.greedy;

import net.xmx.vortex.physics.terrain.model.VxGreedyBox;
import java.util.ArrayList;
import java.util.List;

public final class GreedyMesher {

    private GreedyMesher() {}

    public static List<VxGreedyBox> mesh(boolean[][][] voxels, int width, int height, int depth) {
        List<VxGreedyBox> boxes = new ArrayList<>();
        boolean[][][] visited = new boolean[width][height][depth];

        for (int y = 0; y < height; ++y) {
            for (int z = 0; z < depth; ++z) {
                for (int x = 0; x < width; ++x) {
                    if (!voxels[x][y][z] || visited[x][y][z]) {
                        continue;
                    }

                    int w = 1;
                    while (x + w < width && !visited[x + w][y][z] && voxels[x + w][y][z]) {
                        w++;
                    }

                    int h = 1;
                    while (y + h < height) {
                        boolean canExtend = true;
                        for (int k = 0; k < w; ++k) {
                            if (visited[x + k][y + h][z] || !voxels[x + k][y + h][z]) {
                                canExtend = false;
                                break;
                            }
                        }
                        if (canExtend) {
                            h++;
                        } else {
                            break;
                        }
                    }

                    int d = 1;
                    while (z + d < depth) {
                        boolean canExtend = true;
                        for (int kx = 0; kx < w; ++kx) {
                            for (int ky = 0; ky < h; ++ky) {
                                if (visited[x + kx][y + ky][z + d] || !voxels[x + kx][y + ky][z + d]) {
                                    canExtend = false;
                                    break;
                                }
                            }
                            if (!canExtend) {
                                break;
                            }
                        }
                        if (canExtend) {
                            d++;
                        } else {
                            break;
                        }
                    }

                    boxes.add(new VxGreedyBox(x, y, z, x + w, y + h, z + d));
                    for (int ix = x; ix < x + w; ++ix) {
                        for (int iy = y; iy < y + h; ++iy) {
                            for (int iz = z; iz < z + d; ++iz) {
                                visited[ix][iy][iz] = true;
                            }
                        }
                    }
                }
            }
        }
        return boxes;
    }
}