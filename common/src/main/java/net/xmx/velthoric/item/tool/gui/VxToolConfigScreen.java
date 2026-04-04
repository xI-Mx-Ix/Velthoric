/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.tool.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.xmx.velthoric.item.tool.VxToolMode;
import net.xmx.velthoric.item.tool.config.VxToolConfig;
import net.xmx.velthoric.item.tool.config.VxToolProperty;
import net.xmx.velthoric.item.tool.packet.VxToolConfigPacket;
import net.xmx.velthoric.network.VxNetworking;

import java.util.HashMap;
import java.util.Map;

/**
 * The configuration screen for the currently held tool.
 * <p>
 * This screen dynamically generates UI widgets based on the property types defined
 * in the tool's configuration. It handles input validation for numeric types and
 * synchronizes changes to the server upon closing.
 *
 * @author xI-Mx-Ix
 */
public class VxToolConfigScreen extends Screen {

    private final VxToolMode mode;
    private final Item item;
    private final VxToolConfig config;

    /**
     * Stores the string representation of values to be sent to the server.
     */
    private final Map<String, String> edits = new HashMap<>();

    public VxToolConfigScreen(Item item, VxToolMode mode) {
        super(Component.literal("Tool Configuration"));
        this.item = item;
        this.mode = mode;
        // Retrieve the configuration structure associated with the client player
        this.config = mode.getConfig(Minecraft.getInstance().player.getUUID());
        // Load persistent values from local storage
        this.config.load(BuiltInRegistries.ITEM.getKey(item).getPath());
    }

    @Override
    protected void init() {
        int y = 40;
        int centerX = this.width / 2;

        for (Map.Entry<String, VxToolProperty<?>> entry : config.getProperties().entrySet()) {
            String key = entry.getKey();
            VxToolProperty<?> prop = entry.getValue();
            Class<?> type = prop.getType();

            // Display the property name as a non-interactive button (acting as a label)
            this.addRenderableWidget(Button.builder(Component.literal(key), b -> {
            }).bounds(centerX - 150, y, 100, 20).build()).active = false;

            // Generate specific widgets based on the property type
            if (type == Boolean.class) {
                boolean currentVal = (Boolean) prop.getValue();

                // Toggle button for boolean values
                Button boolBtn = Button.builder(Component.literal(String.valueOf(currentVal)), b -> {
                    boolean newVal = !Boolean.parseBoolean(b.getMessage().getString());
                    b.setMessage(Component.literal(String.valueOf(newVal)));
                    edits.put(key, String.valueOf(newVal));
                }).bounds(centerX - 40, y, 100, 20).build();

                this.addRenderableWidget(boolBtn);
                edits.put(key, String.valueOf(currentVal)); // Initialize edit map

            } else {
                // EditBox for Numbers and Strings
                EditBox box = new EditBox(this.font, centerX - 40, y, 100, 20, Component.literal(key));
                box.setMaxLength(256);
                box.setValue(String.valueOf(prop.getValue()));

                // Apply input filtering based on numeric types
                if (type == Integer.class || type == Long.class) {
                    // Allow optional negative sign and digits only
                    box.setFilter(s -> s.isEmpty() || s.matches("-?\\d*"));
                } else if (type == Float.class || type == Double.class) {
                    // Allow optional negative sign, digits, and a single decimal point
                    box.setFilter(s -> s.isEmpty() || s.matches("-?\\d*(\\.\\d*)?"));
                }
                // String types accept any input, so no filter is applied

                box.setResponder(val -> edits.put(key, val));
                this.addRenderableWidget(box);
                edits.put(key, String.valueOf(prop.getValue())); // Initialize edit map
            }

            y += 25;
        }

        // Reset to Defaults button (Left)
        this.addRenderableWidget(Button.builder(Component.literal("Reset to Defaults"), b -> {
            this.config.resetToDefaults();
            // Clear edits map and refresh the screen widgets
            this.edits.clear();
            this.init(this.minecraft, this.width, this.height);
        }).bounds(centerX - 105, this.height - 30, 100, 20).build());

        // Save & Close button (Right)
        this.addRenderableWidget(Button.builder(Component.literal("Save & Close"), b -> {
            this.onClose();
        }).bounds(centerX + 5, this.height - 30, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void removed() {
        // Apply current UI edits to the local configuration object
        this.config.applyEdits(this.edits);
        // Save the configuration to client's local storage
        this.config.save(BuiltInRegistries.ITEM.getKey(item).getPath());
        // Transmit the accumulated edits to the server when the screen closes
        VxNetworking.sendToServer(new VxToolConfigPacket(Item.getId(item), edits));
    }
}