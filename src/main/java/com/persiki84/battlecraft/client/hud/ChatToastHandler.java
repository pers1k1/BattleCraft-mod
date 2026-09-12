package com.persiki84.battlecraft.client.hud;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.shared.ArgNumbers;
import com.persiki84.shared.client.menu.GlassScreen;
import com.persiki84.shared.client.menu.MenuFeedback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.TextColor;
import com.persiki84.shared.client.menu.MenuCommands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class ChatToastHandler {
    private static final long AIRDROP_EXTRA_MS = 10000L;
    private static final long LIVE_HOLD_MS = 2800L;

    private static final Map<String, Integer> LIVE_ARGS = Map.of(
            "capturepoints.capture.defend_warning", 2
    );

    private static final Map<String, Integer> COUNTDOWN_ARGS = Map.of(
            "capturepoints.capture.on_cooldown", 1,
            "battlecraft.error.surrender_too_early", 0,
            "battlecraft.error.vote_cooldown", 0,
            "quarrymod.event.cooldown_remaining", 0,
            "airdrop.despawn.warning", 0,
            "airdrop.interact.opening", 0,
            "immortality.respawn.given", 0,
            "immortality.give.target", 0
    );

    private static final Set<String> SHOWN_ON_HUD = Set.of(
            "battlecraft.match.ended",
            "battlecraft.match.ended.winner",
            "battlecraft.lobby.countdown",
            "battlecraft.lobby.waiting_for",
            "battlecraft.vote.started",
            "capturepoints.capture.started",
            "capturepoints.capture.completed_team",
            "capturepoints.income.received",
            "capturepoints.reward.paid",
            "capturepoints.reward.paid_share",
            "killreward.kill_rewarded"
    );

    private static final Set<String> MOD_NAMESPACES = Set.of(
            "battlecraft", "airdrop", "capturepoints", "combattimer", "dmgndctr", "immortality",
            "itemmodifiers", "killreward", "knockdown", "minimap", "quarrymod", "sellmod", "zones"
    );

    private ChatToastHandler() {}

    @SubscribeEvent
    public static void onSystemMessage(ClientChatReceivedEvent.System event) {
        if (!HudConfig.chatToasts()) return;

        Component message = event.getMessage();
        if (MenuCommands.quiet()) {
            event.setCanceled(true);
            if (!isListing(message)) answer(message);
            return;
        }

        Component own = findOwn(message, 0, false);
        if (own == null) own = findOwn(message, 0, true);
        if (own == null || isListing(message)) return;

        event.setCanceled(true);
        pushOwn(message, own);
    }

    private static void answer(Component message) {
        if (Minecraft.getInstance().screen instanceof GlassScreen) {
            MenuFeedback.show(message, failure(message));
            return;
        }
        ToastHud.push(message, 0L);
    }

    private static boolean failure(Component message) {
        TextColor color = message.getStyle().getColor();
        if (color != null && color.equals(TextColor.fromLegacyFormat(ChatFormatting.RED))) return true;

        Component own = findOwn(message, 0, false);
        if (own == null) return false;
        return ((TranslatableContents) own.getContents()).getKey().contains(".error.");
    }

    private static void pushOwn(Component message, Component own) {
        TranslatableContents contents = (TranslatableContents) own.getContents();
        String key = contents.getKey();
        if (SHOWN_ON_HUD.contains(key)) return;
        if (Minecraft.getInstance().screen instanceof GlassScreen) {
            MenuFeedback.show(message, failure(message));
            return;
        }

        Style style = own.getStyle().applyTo(message.getStyle());

        Integer liveArg = LIVE_ARGS.get(key);
        if (liveArg != null && pushTicking(contents, style, liveArg, true)) {
            return;
        }

        Integer countdownArg = COUNTDOWN_ARGS.get(key);
        if (countdownArg != null && pushTicking(contents, style, countdownArg, false)) {
            return;
        }

        ToastHud.push(message, key.startsWith("airdrop.") ? AIRDROP_EXTRA_MS : 0L);
    }

    private static boolean isListing(Component message) {
        return message.getString().indexOf('\n') >= 0;
    }

    private static boolean pushTicking(TranslatableContents contents, Style style, int index, boolean live) {
        Object[] args = contents.getArgs();
        if (index >= args.length) return false;

        int seconds = ArgNumbers.asInt(args[index], 0);
        if (seconds <= 0) return false;

        String key = contents.getKey();
        String toastKey = toastKey(key, args, index);
        long endsAt = System.currentTimeMillis() + seconds * 1000L;

        Supplier<Component> text = () -> {
            Object[] ticking = args.clone();
            ticking[index] = Math.max(0, (int) Math.ceil((endsAt - System.currentTimeMillis()) / 1000.0));
            MutableComponent value = Component.translatable(key, ticking);
            return style == Style.EMPTY ? value : value.setStyle(style);
        };

        if (live) {
            ToastHud.pushLive(toastKey, text, endsAt, LIVE_HOLD_MS, true);
        } else {
            ToastHud.pushCountdown(toastKey, text, endsAt);
        }
        return true;
    }

    private static String toastKey(String key, Object[] args, int index) {
        StringBuilder builder = new StringBuilder(key);
        for (int i = 0; i < args.length; i++) {
            if (i == index) continue;
            builder.append('|').append(args[i] instanceof Component text ? text.getString() : String.valueOf(args[i]));
        }
        return builder.toString();
    }

    private static Component findOwn(Component component, int depth, boolean allowPrefix) {
        if (depth > 4) return null;

        if (component.getContents() instanceof TranslatableContents contents
                && isOwnKey(contents.getKey())
                && (allowPrefix || !contents.getKey().endsWith(".prefix"))) {
            return component;
        }
        for (Component sibling : component.getSiblings()) {
            Component found = findOwn(sibling, depth + 1, allowPrefix);
            if (found != null) return found;
        }
        return null;
    }

    private static boolean isOwnKey(String key) {
        int dot = key.indexOf('.');
        return dot > 0 && MOD_NAMESPACES.contains(key.substring(0, dot));
    }
}
