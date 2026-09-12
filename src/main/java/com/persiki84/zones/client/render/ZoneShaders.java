package com.persiki84.zones.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.persiki84.zones.ZonesMod;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;

@Mod.EventBusSubscriber(modid = ZonesMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ZoneShaders {
    public static final VertexFormat ZONE_FORMAT = DefaultVertexFormat.POSITION_TEX;

    private static ShaderInstance volumeShader;
    private static ShaderInstance ringShader;

    private ZoneShaders() {}

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ZonesMod.MOD_ID, "zone_volume"), ZONE_FORMAT),
                shader -> volumeShader = shader);
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ZonesMod.MOD_ID, "zone_ring"), ZONE_FORMAT),
                shader -> ringShader = shader);
    }

    public static ShaderInstance volume() {
        return volumeShader;
    }

    public static ShaderInstance ring() {
        return ringShader;
    }

    public static boolean ready() {
        return volumeShader != null && ringShader != null;
    }
}
