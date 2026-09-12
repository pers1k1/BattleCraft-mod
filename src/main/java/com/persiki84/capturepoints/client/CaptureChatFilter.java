package com.persiki84.capturepoints.client;

import com.persiki84.capturepoints.CapturePointsMod;
import com.persiki84.shared.ArgNumbers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CapturePointsMod.MOD_ID, value = Dist.CLIENT)
public final class CaptureChatFilter {
    private static final String RETURN_KEY = "capturepoints.capture.return_to_zone";
    private static final String CANCELLED_PREFIX = "capturepoints.capture.cancelled";
    private static final int MAX_DEPTH = 4;

    private CaptureChatFilter() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onSystemMessage(ClientChatReceivedEvent.System event) {
        TranslatableContents contents = find(event.getMessage(), 0);
        if (contents == null) return;

        if (contents.getKey().startsWith(CANCELLED_PREFIX)) {
            CaptureReturnHud.clear();
            return;
        }

        event.setCanceled(true);
        int seconds = seconds(contents);
        if (seconds > 0) CaptureReturnHud.show(seconds);
    }

    private static int seconds(TranslatableContents contents) {
        Object[] args = contents.getArgs();
        return args.length == 0 ? 0 : ArgNumbers.asInt(args[0], 0);
    }

    private static TranslatableContents find(Component message, int depth) {
        if (depth > MAX_DEPTH) return null;

        if (message.getContents() instanceof TranslatableContents contents && watched(contents.getKey())) {
            return contents;
        }
        for (Component sibling : message.getSiblings()) {
            TranslatableContents found = find(sibling, depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    private static boolean watched(String key) {
        return RETURN_KEY.equals(key) || key.startsWith(CANCELLED_PREFIX);
    }
}
