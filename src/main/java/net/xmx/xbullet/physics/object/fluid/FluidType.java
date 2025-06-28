package net.xmx.xbullet.physics.object.fluid;

import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

import java.util.Arrays;
import java.util.Optional;

public enum FluidType {
    WATER(997.0f, FluidTags.WATER),
    LAVA(3100.0f, FluidTags.LAVA);

    private final float density;
    private final TagKey<Fluid> tag;

    FluidType(float density, TagKey<Fluid> tag) {
        this.density = density;
        this.tag = tag;
    }

    public float getDensity() {
        return density;
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