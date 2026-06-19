package com.persiki84.dmgndctr.client;

import com.persiki84.dmgndctr.network.DamagePacket;
import net.minecraft.client.Minecraft;

public class ClientPacketHandler {
    public static void handle(DamagePacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.particleEngine.add(new TextParticle(mc.level, msg.x, msg.y, msg.z, msg.damage, msg.isCrit));
        }
    }
}
