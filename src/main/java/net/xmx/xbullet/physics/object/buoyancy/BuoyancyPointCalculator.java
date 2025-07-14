package net.xmx.xbullet.physics.object.buoyancy;

import com.github.stephengold.joltjni.AaBox;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.readonly.ConstShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class BuoyancyPointCalculator {

    private static final float GRID_SPACING = 0.4f;

    public static CompletableFuture<List<Vec3>> calculateAsync(ConstShape shape) {
        return CompletableFuture.supplyAsync(() -> {
            if (shape == null || !shape.hasAssignedNativeObject()) {
                return Collections.emptyList();
            }

            try (AaBox localBounds = shape.getLocalBounds()) {
                Vec3 center = localBounds.getCenter();
                Vec3 extent = localBounds.getExtent();

                if (extent.isNearZero()) {
                    return Collections.emptyList();
                }

                List<Vec3> points = new ArrayList<>();

                int stepsX = Math.max(0, (int) (extent.getX() / GRID_SPACING));
                int stepsY = Math.max(0, (int) (extent.getY() / GRID_SPACING));
                int stepsZ = Math.max(0, (int) (extent.getZ() / GRID_SPACING));

                for (int i = -stepsX; i <= stepsX; i++) {
                    for (int j = -stepsY; j <= stepsY; j++) {
                        for (int k = -stepsZ; k <= stepsZ; k++) {

                            float x = center.getX() + i * GRID_SPACING;
                            float y = center.getY() + j * GRID_SPACING;
                            float z = center.getZ() + k * GRID_SPACING;

                            Vec3 point = new Vec3(x, y, z);

                            if (localBounds.contains(point)) {
                                points.add(point);
                            }
                        }
                    }
                }

                if (points.isEmpty()) {
                    points.add(center);
                }

                return points;
            }
        });
    }
}