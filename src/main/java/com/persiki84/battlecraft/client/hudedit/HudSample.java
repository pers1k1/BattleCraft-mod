package com.persiki84.battlecraft.client.hudedit;

import com.persiki84.battlecraft.client.ClientOverlayRenderer;
import com.persiki84.battlecraft.client.custom.HudBox;
import com.persiki84.battlecraft.client.custom.HudLayout;
import com.persiki84.battlecraft.client.custom.HudSlot;
import com.persiki84.battlecraft.client.hud.AmmoHud;
import com.persiki84.battlecraft.client.hud.GogglesHud;
import com.persiki84.battlecraft.client.hud.BottomHud;
import com.persiki84.battlecraft.client.hud.EffectsHud;
import com.persiki84.battlecraft.client.hud.MessageHud;
import com.persiki84.battlecraft.client.hud.ScanHud;
import com.persiki84.battlecraft.client.hud.ToastHud;
import com.persiki84.battlecraft.client.hud.VoiceHud;
import com.persiki84.battlecraft.client.island.IslandHud;
import com.persiki84.capturepoints.client.CaptureHudOverlay;
import com.persiki84.capturepoints.client.ObjectiveHud;
import com.persiki84.knockdown.client.KnockdownHud;
import com.persiki84.minimap.client.MinimapOverlay;
import com.persiki84.sellmod.client.CurrencyHud;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.zones.client.ZoneHud;
import net.minecraft.client.gui.GuiGraphics;

public final class HudSample {
    private HudSample() {}

    public static void draw(GuiGraphics graphics, HudSlot slot, HudBox box, float alpha) {
        if (slot == HudSlot.MINIMAP) {
            float span = MinimapOverlay.renderSize();
            HudLayout.sample(slot, span, span);
            return;
        }

        HudLayout.push(graphics, box);
        try {
            paint(graphics, slot, box, alpha);
        } finally {
            HudLayout.pop(graphics, box);
        }
    }

    private static void paint(GuiGraphics graphics, HudSlot slot, HudBox box, float alpha) {
        switch (slot) {
            case STATUS -> BottomHud.previewStatus(graphics, box, alpha);
            case HOTBAR -> BottomHud.previewTray(graphics, box, alpha);
            case ITEM_NAME -> BottomHud.previewItemName(graphics, box.x(), box.y(), box.width(), alpha);
            case MESSAGES -> MessageHud.preview(graphics, box.x(), box.y(), box.width(), box.height(), alpha);
            case ZONE_PROMPT -> ZoneHud.preview(graphics, box.x(), box.y(), box.width(), box.height(), alpha);
            case CAPTURE -> CaptureHudOverlay.preview(graphics, box.x(), box.y(), box.width(), box.height(), alpha);
            case OBJECTIVE -> ObjectiveHud.preview(graphics, box.centerX(), box.y(), alpha, UiFrame.delta());
            case AMMO -> AmmoHud.preview(graphics, box, alpha);
            case VOICE -> VoiceHud.preview(graphics, box, alpha);
            case GOGGLES -> GogglesHud.preview(graphics, box, alpha);
            case CURRENCY -> CurrencyHud.preview(graphics, box, alpha);
            case EFFECTS -> EffectsHud.preview(graphics, box, alpha);
            case TOASTS -> ToastHud.preview(graphics, box, alpha);
            case ISLAND -> IslandHud.preview(graphics, box, alpha);
            case SCAN -> ScanHud.preview(graphics, box, alpha);
            case KNOCKDOWN -> KnockdownHud.preview(graphics, box, alpha);
            case WARNING -> ClientOverlayRenderer.previewWarning(graphics, box, alpha);
            case LOBBY -> ClientOverlayRenderer.previewLobby(graphics, box, alpha);
            case VOTE -> ClientOverlayRenderer.previewVote(graphics, box, alpha);
            default -> { }
        }
    }
}
