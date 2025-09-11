/*
This file is part of Velthoric.
Licensed under LGPL 3.0.
*/
package net.xmx.velthoric.event.api;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;

public class VxRenderEvent {

    /**
     * Fired at different stages of the level rendering process on the client.
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
            LEVEL_LAST // Corresponds to the end of the renderLevel method
        }

        @FunctionalInterface
        public interface Listener {
            void onRenderLevelStage(ClientRenderLevelStageEvent event);
        }
    }
}