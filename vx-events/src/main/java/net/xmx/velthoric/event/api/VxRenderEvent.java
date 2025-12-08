/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.event.api;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;

/**
 * Central registry for client-side rendering events.
 *
 * @author xI-Mx-Ix
 */
public class VxRenderEvent {

    /**
     * Fired when the 2D HUD (GUI) is rendered.
     * This allows rendering overlays like speedometers or other custom UI elements.
     */
    public static class ClientRenderHudEvent {
        public static final Event<Listener> EVENT = EventFactory.createLoop();

        private final GuiGraphics guiGraphics;
        private final float partialTick;

        public ClientRenderHudEvent(GuiGraphics guiGraphics, float partialTick) {
            this.guiGraphics = guiGraphics;
            this.partialTick = partialTick;
        }

        public GuiGraphics getGuiGraphics() {
            return guiGraphics;
        }

        public float getPartialTick() {
            return partialTick;
        }

        @FunctionalInterface
        public interface Listener {
            void onRenderHud(ClientRenderHudEvent event);
        }
    }

    /**
     * Fired at different stages of the level rendering process on the client (3D World).
     */
    public static class ClientRenderLevelStageEvent {
        public static final Event<Listener> EVENT = EventFactory.createLoop();

        private final Stage stage;
        private final LevelRenderer levelRenderer;
        private final PoseStack poseStack;
        private final float partialTick;
        private final Matrix4f projectionMatrix;
        private final LightTexture lightTexture;

        public ClientRenderLevelStageEvent(Stage stage, LevelRenderer levelRenderer, PoseStack poseStack, float partialTick, LightTexture lightTexture, Matrix4f projectionMatrix) {
            this.stage = stage;
            this.levelRenderer = levelRenderer;
            this.poseStack = poseStack;
            this.partialTick = partialTick;
            this.lightTexture = lightTexture;
            this.projectionMatrix = projectionMatrix;
        }

        public Stage getStage() {
            return stage;
        }

        public LevelRenderer getLevelRenderer() {
            return levelRenderer;
        }

        public PoseStack getPoseStack() {
            return poseStack;
        }

        public float getPartialTick() {
            return partialTick;
        }

        public Matrix4f getProjectionMatrix() {
            return projectionMatrix;
        }

        public LightTexture getLightTexture() {
            return lightTexture;
        }

        public enum Stage {
            AFTER_SKY,
            AFTER_ENTITIES,
            AFTER_BLOCK_ENTITIES,
            AFTER_PARTICLES,
            AFTER_WEATHER,
            LEVEL_LAST
        }

        @FunctionalInterface
        public interface Listener {
            void onRenderLevelStage(ClientRenderLevelStageEvent event);
        }
    }
}