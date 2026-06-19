package com.persiki84.airdrop.client;

import com.persiki84.airdrop.AirDropMod;
import com.persiki84.airdrop.client.model.AirDropModel;
import com.persiki84.airdrop.client.renderer.AirDropRenderer;
import com.persiki84.airdrop.entity.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AirDropMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(AirDropModel.LAYER_LOCATION, AirDropModel::createBodyLayer);
    }
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.AIRDROP.get(), AirDropRenderer::new);
    }
}
