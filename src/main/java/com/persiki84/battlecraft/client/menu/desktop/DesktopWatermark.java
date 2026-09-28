package com.persiki84.battlecraft.client.menu.desktop;

import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;

// WHY: версия лежит на обоях, как водяной знак бета-сборки macOS: две тихие строки в правом нижнем
// WHY: углу на уровне Dock. На узком экране, где строки дошли бы до Dock, знака нет вовсе:
// WHY: наезд на Dock читался бы ошибкой вёрстки, а не подписью
final class DesktopWatermark {
    static final Component NAME = Component.translatable("battlecraft.menu.version");

    private static final String MOD_ID = "battlecraft";
    private static final float MARGIN = 7.0f;
    private static final float NAME_SCALE = 0.62f;
    private static final float BUILD_SCALE = 0.55f;
    private static final float LINE_GAP = 2.0f;
    private static final float NAME_ALPHA = 0.8f;
    private static final float BUILD_ALPHA = 0.55f;
    private static final float DOCK_CLEARANCE = 10.0f;

    private final Component build = build();
    private Language measuredLanguage;
    private double measuredScale;
    private float width;

    static Component build() {
        String version = ModList.get().getModContainerById(MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("");
        return Component.translatable("battlecraft.desktop.build", version);
    }

    void render(GuiGraphics graphics, Font font, float screenWidth, float screenHeight, float dockRight,
                float alpha) {
        float right = screenWidth - MARGIN;
        if (right - width(font) < dockRight + DOCK_CLEARANCE) return;

        float buildY = screenHeight - MARGIN - font.lineHeight * BUILD_SCALE;
        float nameY = buildY - LINE_GAP - font.lineHeight * NAME_SCALE;
        int ink = UiAccent.textFaint();
        UiRender.textRight(graphics, font, NAME, right, nameY, NAME_SCALE, UiTheme.alpha(ink, NAME_ALPHA * alpha),
                false);
        UiRender.textRight(graphics, font, build, right, buildY, BUILD_SCALE,
                UiTheme.alpha(ink, BUILD_ALPHA * alpha), false);
    }

    // WHY: ширина нужна только для проверки места рядом с Dock и меряется на смене языка или
    // WHY: масштаба интерфейса, а не в каждом кадре
    private float width(Font font) {
        Language language = Language.getInstance();
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        if (language != measuredLanguage || scale != measuredScale) {
            measuredLanguage = language;
            measuredScale = scale;
            width = Math.max(UiRender.width(font, NAME) * NAME_SCALE, UiRender.width(font, build) * BUILD_SCALE);
        }
        return width;
    }
}
