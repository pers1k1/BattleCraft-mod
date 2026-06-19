package com.persiki84.knockdown.client;

import com.persiki84.knockdown.KnockDownMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = KnockDownMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class KeyInit {

    public static final KeyMapping REVIVE_KEY = new KeyMapping(
            "key.knockdown.revive",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.knockdown"
    );

    public static final KeyMapping SURRENDER_KEY = new KeyMapping(
            "key.knockdown.surrender",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.knockdown"
    );

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(REVIVE_KEY);
        event.register(SURRENDER_KEY);
    }
}
