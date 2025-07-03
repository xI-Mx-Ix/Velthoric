package net.xmx.xbullet.physics.object.fluid;

import com.github.stephengold.joltjni.AaBox;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.readonly.ConstShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class BuoyancyPointCalculator {

    private static final float GRID_SIZE = 0.5f;

    public static CompletableFuture<List<Vec3>> calculateAsync(ConstShape shape) {

        return CompletableFuture.supplyAsync(() -> {
            if (shape == null || !shape.hasAssignedNativeObject()) {
                return Collections.emptyList();
            }

            try (AaBox localBounds = shape.getLocalBounds()) {
                Vec3 center = localBounds.getCenter();
                Vec3 extent = localBounds.getExtent();

                if (extent.getX() == 0 && extent.getY() == 0 && extent.getZ() == 0) {
                    return Collections.emptyList();
                }

                List<Vec3> points = new ArrayList<>();

                int stepsX = (int) Math.ceil(extent.getX() / GRID_SIZE);
                int stepsY = (int) Math.ceil(extent.getY() / GRID_SIZE);
                int stepsZ = (int) Math.ceil(extent.getZ() / GRID_SIZE);

                for (int i = -stepsX; i <= stepsX; i++) {
                    for (int j = -stepsY; j <= stepsY; j++) {
                        for (int k = -stepsZ; k <= stepsZ; k++) {
                            float x = center.getX() + i * GRID_SIZE;
                            float y = center.getY() + j * GRID_SIZE;
                            float z = center.getZ() + k * GRID_SIZE;

                            if (localBounds.contains(new Vec3(x, y, z))) {
                                points.add(new Vec3(x, y, z));
                            }
                        }
                    }
                }
                return points;
            }
        });
    }
}