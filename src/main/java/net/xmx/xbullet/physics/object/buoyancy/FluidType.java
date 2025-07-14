package net.xmx.xbullet.physics.object.buoyancy;

import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

import java.util.Arrays;
import java.util.Optional;

public enum FluidType {
    WATER(997.0f, 0.001f, FluidTags.WATER),
    LAVA(3100.0f, 100.0f, FluidTags.LAVA);

    private final float density;
    private final float viscosity;
    private final TagKey<Fluid> tag;

    FluidType(float density, float viscosity, TagKey<Fluid> tag) {
        this.density = density;
        this.viscosity = viscosity;
        this.tag = tag;
    }

    public float getDensity() {
        return density;
    }

    public float getViscosity() {
        return viscosity;
    }

    public TagKey<Fluid> getTag() {
        return tag;
    }

    public static Optional<FluidType> fromFluidState(FluidState state) {
        if (state.isEmpty()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(type -> state.is(type.getTag()))
                .findFirst();
    }
}