package net.xmx.xbullet.model.objmodel;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public class OBJRenderTypes extends RenderType {

    private OBJRenderTypes(String s, VertexFormat v, VertexFormat.Mode m, int i, boolean b, boolean b2, Runnable r, Runnable r2) {
        super(s, v, m, i, b, b2, r, r2);
    }

    public static RenderType objSolid(ResourceLocation textureLocation) {
        RenderType.CompositeState state = RenderType.CompositeState.builder()

                .setShaderState(RENDERTYPE_ENTITY_SOLID_SHADER)

                .setTextureState(new RenderStateShard.TextureStateShard(textureLocation, false, false))

                .setTransparencyState(NO_TRANSPARENCY)

                .setLightmapState(LIGHTMAP)

                .setOverlayState(OVERLAY)

                .setCullState(CULL)

                .createCompositeState(true);

        return create("obj_solid", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES, 256, true, false, state);
    }

    public static RenderType objTranslucent(ResourceLocation textureLocation) {
        RenderType.CompositeState state = RenderType.CompositeState.builder()

                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)

                .setTextureState(new RenderStateShard.TextureStateShard(textureLocation, false, false))

                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)

                .setLightmapState(LIGHTMAP)

                .setOverlayState(OVERLAY)

                .setCullState(NO_CULL)

                .createCompositeState(true);

        return create("obj_translucent", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES, 256, true, true, state);
    }
}