/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.tool.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.architectury.platform.Platform;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

/**
 * Utility class for persistent storage of tool configurations on the client side.
 * <p>
 * This class handles saving and loading tool properties to/from JSON files
 * located in the mod's configuration directory.
 *
 * @author xI-Mx-Ix
 */
public class VxToolPersistence {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_DIR = Platform.getConfigFolder().resolve("velthoric/tools").toFile();

    /**
     * Saves the given configuration to a JSON file.
     *
     * @param toolId The unique identifier for the tool (e.g., its registry name).
     * @param config The configuration to save.
     */
    public static void save(String toolId, VxToolConfig config) {
        if (!CONFIG_DIR.exists()) {
            CONFIG_DIR.mkdirs();
        }

        File file = new File(CONFIG_DIR, toolId + ".json");
        JsonObject json = new JsonObject();

        for (Map.Entry<String, VxToolProperty<?>> entry : config.getProperties().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue().getValue();

            if (value instanceof Number n) {
                json.addProperty(key, n);
            } else if (value instanceof Boolean b) {
                json.addProperty(key, b);
            } else if (value instanceof String s) {
                json.addProperty(key, s);
            }
        }

        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(json, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Loads the configuration from a JSON file, if it exists.
     *
     * @param toolId The unique identifier for the tool.
     * @param config The configuration object to populate with loaded values.
     */
    public static void load(String toolId, VxToolConfig config) {
        File file = new File(CONFIG_DIR, toolId + ".json");
        if (!file.exists()) return;

        try (FileReader reader = new FileReader(file)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) return;

            for (Map.Entry<String, VxToolProperty<?>> entry : config.getProperties().entrySet()) {
                String key = entry.getKey();
                if (json.has(key)) {
                    Class<?> type = entry.getValue().getType();
                    try {
                        if (type == Integer.class) {
                            config.setValue(key, json.get(key).getAsInt());
                        } else if (type == Long.class) {
                            config.setValue(key, json.get(key).getAsLong());
                        } else if (type == Float.class) {
                            config.setValue(key, json.get(key).getAsFloat());
                        } else if (type == Double.class) {
                            config.setValue(key, json.get(key).getAsDouble());
                        } else if (type == Boolean.class) {
                            config.setValue(key, json.get(key).getAsBoolean());
                        } else if (type == String.class) {
                            config.setValue(key, json.get(key).getAsString());
                        }
                    } catch (Exception e) {
                        // Skip malformed individual properties
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}