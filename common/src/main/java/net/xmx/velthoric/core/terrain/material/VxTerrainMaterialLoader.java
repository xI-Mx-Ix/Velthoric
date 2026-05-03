/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.terrain.material;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.xmx.velthoric.init.VxMainClass;

import java.util.Map;

/**
 * Loads terrain materials from JSON data packs under "data/<namespace>/velthoric_materials/".
 *
 * @author xI-Mx-Ix
 */
public class VxTerrainMaterialLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /**
     * Creates a new instance of the VxTerrainMaterialLoader.
     */
    public VxTerrainMaterialLoader() {
        super(GSON, "velthoric_materials");
    }

    /**
     * Loads terrain materials from JSON data packs.
     */
    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
        VxTerrainMaterial.clear();
        
        int loaded = 0;
        for (Map.Entry<ResourceLocation, JsonElement> entry : object.entrySet()) {
            try {
                JsonObject json = entry.getValue().getAsJsonObject();
                if (json.has("values")) {
                    JsonObject values = json.getAsJsonObject("values");
                    for (Map.Entry<String, JsonElement> valueEntry : values.entrySet()) {
                        ResourceLocation blockId = ResourceLocation.parse(valueEntry.getKey());
                        JsonObject props = valueEntry.getValue().getAsJsonObject();
                        
                        float friction = props.has("friction") ? props.get("friction").getAsFloat() : 0.75f;
                        float restitution = props.has("restitution") ? props.get("restitution").getAsFloat() : 0.0f;
                        float weight = props.has("weight") ? props.get("weight").getAsFloat() : 100.0f;
                        
                        boolean isFragile = props.has("fragile") && props.get("fragile").getAsBoolean();
                        
                        Block transformTo = null;
                        if (props.has("transform_to")) {
                            ResourceLocation targetId = ResourceLocation.tryParse(props.get("transform_to").getAsString());
                            if (targetId != null) {
                                transformTo = BuiltInRegistries.BLOCK.get(targetId);
                                if (transformTo == Blocks.AIR) transformTo = null;
                            }
                        }
                        
                        boolean spawnsParticles = !props.has("particles") || props.get("particles").getAsBoolean();
                        float breakThreshold = props.has("break_threshold") ? props.get("break_threshold").getAsFloat() : 5000.0f;
                        boolean isInteractable = props.has("interactable") && props.get("interactable").getAsBoolean();

                        VxTerrainMaterial.register(blockId, friction, restitution, weight, isFragile, transformTo, spawnsParticles, breakThreshold, isInteractable);
                        loaded++;
                    }
                }
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Failed to parse Velthoric material file: {}", entry.getKey(), e);
            }
        }
        
        if (loaded > 0) {
            VxMainClass.LOGGER.info("Loaded {} custom terrain materials from data packs.", loaded);
        }
    }
}