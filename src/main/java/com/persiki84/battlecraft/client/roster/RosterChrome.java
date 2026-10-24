package com.persiki84.battlecraft.client.roster;

import com.persiki84.battlecraft.mixin.PlayerTabOverlayAccessor;
import com.persiki84.shared.client.ui.UiRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

// WHY: шапку и подвал списка присылает сервер (ClientboundTabListPacket), и ванильный список держит
// WHY: их у себя закрытыми полями. Разбор на строки идёт через атласы шрифта, поэтому держится до
// WHY: смены текста, ширины или плотности пикселей, а не считается каждый кадр
final class RosterChrome {
    static final float SCALE = 0.85f;
    private static final float LINE_GAP = 2.0f;

    private final Part header = new Part();
    private final Part footer = new Part();

    public void measure(GuiGraphics graphics, Font font, float width) {
        if (!(Minecraft.getInstance().gui.getTabList() instanceof PlayerTabOverlayAccessor tab)) return;
        header.measure(graphics, font, tab.battlecraft$header(), width);
        footer.measure(graphics, font, tab.battlecraft$footer(), width);
    }

    public float headerHeight(Font font) {
        return header.height(font);
    }

    public float footerHeight(Font font) {
        return footer.height(font);
    }

    public float widest() {
        return Math.max(header.widest, footer.widest);
    }

    public void drawHeader(GuiGraphics graphics, Font font, float centerX, float y, int color) {
        header.draw(graphics, font, centerX, y, color);
    }

    public void drawFooter(GuiGraphics graphics, Font font, float centerX, float y, int color) {
        footer.draw(graphics, font, centerX, y, color);
    }

    private static final class Part {
        private final List<FormattedCharSequence> lines = new ArrayList<>();
        private Component measured;
        private float measuredWidth = -1.0f;
        private float measuredPixels = -1.0f;
        private float widest;

        private void measure(GuiGraphics graphics, Font font, Component value, float width) {
            float pixels = UiRender.pixels(graphics);
            if (value == measured && width == measuredWidth && pixels == measuredPixels) return;

            measured = value;
            measuredWidth = width;
            measuredPixels = pixels;
            lines.clear();
            widest = 0.0f;
            if (value == null || value.getString().isBlank()) return;
            lines.addAll(UiRender.split(graphics, font, value, SCALE, (int) (width / SCALE)));
            for (FormattedCharSequence line : lines) {
                widest = Math.max(widest, UiRender.measureLine(graphics, font, line, SCALE));
            }
        }

        private float height(Font font) {
            if (lines.isEmpty()) return 0.0f;
            return lines.size() * (font.lineHeight * SCALE + LINE_GAP) - LINE_GAP;
        }

        private void draw(GuiGraphics graphics, Font font, float centerX, float y, int color) {
            float step = font.lineHeight * SCALE + LINE_GAP;
            for (FormattedCharSequence line : lines) {
                float span = UiRender.measureLine(graphics, font, line, SCALE);
                UiRender.textLine(graphics, font, line, centerX - span / 2.0f, y, SCALE, color, false);
                y += step;
            }
        }
    }
}
