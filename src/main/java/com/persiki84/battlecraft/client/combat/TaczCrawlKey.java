package com.persiki84.battlecraft.client.combat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "battlecraft", value = Dist.CLIENT)
public final class TaczCrawlKey {
    private static final String CRAWL = "key.tacz.crawl";
    private static final int CHECK_INTERVAL = 20;

    private static int ticks;

    private TaczCrawlKey() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++ticks % CHECK_INTERVAL != 0) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options == null || minecraft.options.keyMappings == null) return;

        unbind(minecraft);
    }

    private static void unbind(Minecraft minecraft) {
        for (KeyMapping mapping : minecraft.options.keyMappings) {
            if (!mapping.getName().startsWith(CRAWL) || mapping.isUnbound()) continue;

            mapping.setKey(InputConstants.UNKNOWN);
            KeyMapping.resetMapping();
            minecraft.options.save();
            return;
        }
    }
}
