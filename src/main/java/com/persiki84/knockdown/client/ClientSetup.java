package com.persiki84.knockdown.client;

import com.persiki84.knockdown.KnockDownMod;
import com.persiki84.knockdown.cap.KnockdownProvider;
import com.persiki84.knockdown.network.NetworkHandler;
import com.persiki84.knockdown.network.PacketReviveAction;
import com.persiki84.knockdown.network.PacketSelfRevive;
import com.persiki84.knockdown.network.PacketSurrenderAction;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = KnockDownMod.MODID, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (KeyInit.REVIVE_KEY.isDown()) {
            mc.player.getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(cap -> {
                if (cap.isKnocked()) {
                    NetworkHandler.CHANNEL.sendToServer(new PacketSelfRevive(true));
                } else {
                    NetworkHandler.CHANNEL.sendToServer(new PacketReviveAction(true));
                }
            });
        }

        if (KeyInit.SURRENDER_KEY.isDown()) {
            mc.player.getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(cap -> {
                if (cap.isKnocked()) {
                    NetworkHandler.CHANNEL.sendToServer(new PacketSurrenderAction(true));
                }
            });
        } else {
            mc.player.getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(cap -> {
                if (cap.isKnocked() && cap.getSurrenderProgress() > 0) {
                    NetworkHandler.CHANNEL.sendToServer(new PacketSurrenderAction(false));
                }
            });
        }
    }
}
