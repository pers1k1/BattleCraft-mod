package com.persiki84.knockdown.client;

import com.persiki84.knockdown.KnockDownMod;
import com.persiki84.knockdown.cap.KnockdownProvider;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = KnockDownMod.MODID, value = Dist.CLIENT)
public class ClientKnockHandler {

    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        mc.player.getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(cap -> {
            if (cap.isKnocked()) {
                if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT || event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    event.setCanceled(true);
                }
            }
        });
    }
}
