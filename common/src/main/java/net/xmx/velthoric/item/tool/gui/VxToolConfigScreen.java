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
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.xmx.velthoric.item.tool.VxToolMode;
import net.xmx.velthoric.item.tool.config.VxToolConfig;
import net.xmx.velthoric.item.tool.config.VxToolProperty;
import net.xmx.velthoric.item.tool.packet.VxToolConfigPacket;
import net.xmx.velthoric.network.VxPacketHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * The configuration screen for the currently held tool.
 *
 * @author xI-Mx-Ix
 */
public class VxToolConfigScreen extends Screen {

    private final VxToolMode mode;
    private final Item item;
    private final VxToolConfig config;
    
    // Temporary storage for edits before sending
    private final Map<String, String> edits = new HashMap<>();

    public VxToolConfigScreen(Item item, VxToolMode mode) {
        super(Component.literal("Tool Configuration"));
        this.item = item;
        this.mode = mode;
        // We use the client player's UUID to get the default/current structure
        this.config = mode.getConfig(Minecraft.getInstance().player.getUUID());
    }

    @Override
    protected void init() {
        int y = 40;
        int centerX = this.width / 2;

        for (Map.Entry<String, VxToolProperty<?>> entry : config.getProperties().entrySet()) {
            String key = entry.getKey();
            VxToolProperty<?> prop = entry.getValue();

            // Label
            this.addRenderableWidget(Button.builder(Component.literal(key), b -> {})
                    .bounds(centerX - 150, y, 100, 20).build()).active = false;

            if (prop.getType() == Boolean.class) {
                boolean currentVal = (Boolean) prop.getValue();
                Button boolBtn = Button.builder(Component.literal(String.valueOf(currentVal)), b -> {
                    boolean newVal = !Boolean.parseBoolean(b.getMessage().getString());
                    b.setMessage(Component.literal(String.valueOf(newVal)));
                    edits.put(key, String.valueOf(newVal));
                }).bounds(centerX - 40, y, 100, 20).build();
                this.addRenderableWidget(boolBtn);
                edits.put(key, String.valueOf(currentVal)); // Init
            } else {
                EditBox box = new EditBox(this.font, centerX - 40, y, 100, 20, Component.literal(key));
                box.setValue(String.valueOf(prop.getValue()));
                box.setResponder(val -> edits.put(key, val));
                this.addRenderableWidget(box);
                edits.put(key, String.valueOf(prop.getValue())); // Init
            }

            y += 25;
        }

        // Save & Close
        this.addRenderableWidget(Button.builder(Component.literal("Save & Close"), b -> {
            this.onClose();
        }).bounds(centerX - 50, this.height - 30, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void removed() {
        // Send packet on close
        VxPacketHandler.sendToServer(new VxToolConfigPacket(Item.getId(item), edits));
    }
}