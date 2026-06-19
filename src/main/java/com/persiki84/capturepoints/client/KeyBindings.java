package com.persiki84.capturepoints.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.persiki84.capturepoints.CapturePointsMod;
import com.persiki84.capturepoints.network.CaptureStartPacket;
import com.persiki84.capturepoints.network.PacketHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = CapturePointsMod.MOD_ID, value = Dist.CLIENT)
public class KeyBindings {
    public static final String CATEGORY = "key.categories." + CapturePointsMod.MOD_ID;

    public static final KeyMapping CAPTURE_KEY = new KeyMapping(
            "key." + CapturePointsMod.MOD_ID + ".capture",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY
    );

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (CAPTURE_KEY.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.level != null) {
                BlockPos playerPos = mc.player.blockPosition();
                PacketHandler.INSTANCE.sendToServer(new CaptureStartPacket(playerPos));
            }
        }
    }
}
