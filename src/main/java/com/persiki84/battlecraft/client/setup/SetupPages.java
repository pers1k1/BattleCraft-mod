package com.persiki84.battlecraft.client.setup;

import com.persiki84.battlecraft.client.DiscordRpcManager;
import com.persiki84.battlecraft.client.custom.CustomPreset;
import com.persiki84.battlecraft.client.custom.Customization;
import com.persiki84.battlecraft.client.custom.GlassKey;
import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.battlecraft.client.voice.VoiceOptions;
import com.persiki84.shared.client.font.FontShape;
import com.persiki84.shared.client.font.MsdfFontSets;
import com.persiki84.shared.client.menu.SliderRow;
import com.persiki84.shared.client.ui.UiCue;
import com.persiki84.shared.client.ui.UiQuality;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiSoundScheme;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class SetupPages {
    private static final int FONT_CARD_HEIGHT = 118;
    private static final int INK_CARD_HEIGHT = 96;
    private static final int SOUND_CARD_HEIGHT = 62;
    private static final int VOLUME_HEIGHT = 26;
    private static final int GLOW_HEIGHT = 26;
    private static final float VOLUME_LOUDEST = 1.0f;
    private static final float VOLUME_STEP = 0.05f;
    private static final float PERCENT = 100.0f;

    private SetupPages() {}

    public static SetupPage of(SetupStep step, SetupScreen screen) {
        return switch (step) {
            case VISUALS -> visuals(screen);
            case TINT -> tint(screen);
            case GLASS_THEME -> glassTheme(screen);
            case INK -> ink(screen);
            case FONT -> font(screen);
            case TEXT -> text(screen);
            case SOUND -> sound(screen);
            case VOICE -> voice(screen);
            case DISCORD -> discord(screen);
        };
    }

    private static SetupPage visuals(SetupScreen screen) {
        List<StackPage.Option> options = new ArrayList<>();
        for (UiQuality.Mode mode : UiQuality.Mode.values()) {
            options.add(new StackPage.Option(
                    Component.translatable("battlecraft.visuals." + mode.id()),
                    Component.translatable("battlecraft.visuals." + mode.id() + ".note"),
                    () -> {
                        Customization.mode(mode);
                        screen.answer(SetupStep.VISUALS);
                    }));
        }
        return new StackPage(SetupStep.VISUALS, options);
    }

    private static SetupPage tint(SetupScreen screen) {
        return new StackPage(SetupStep.TINT, List.of(
                preset(screen, CustomPreset.DEW_DIM, "battlecraft.setup.tint.dim"),
                preset(screen, CustomPreset.DEW, "battlecraft.setup.tint.plain")));
    }

    private static StackPage.Option preset(SetupScreen screen, CustomPreset chosen, String key) {
        return new StackPage.Option(Component.translatable(key), Component.translatable(key + ".note"), () -> {
            Customization.preset(chosen);
            screen.answer(SetupStep.TINT);
        });
    }

    private static SetupPage glassTheme(SetupScreen screen) {
        return new StackPage(SetupStep.GLASS_THEME, List.of(
                new StackPage.Option(Component.translatable("battlecraft.setup.glassTheme.follow"),
                        Component.translatable("battlecraft.setup.glassTheme.follow.note"),
                        Customization.themedGlass(), () -> answerGlass(screen, true)),
                new StackPage.Option(Component.translatable("battlecraft.setup.glassTheme.keep"),
                        Component.translatable("battlecraft.setup.glassTheme.keep.note"),
                        Customization.plainGlass(), () -> answerGlass(screen, false))));
    }

    private static void answerGlass(SetupScreen screen, boolean follow) {
        Customization.glassFollowsTheme(follow);
        screen.answer(SetupStep.GLASS_THEME);
    }

    private static SetupPage ink(SetupScreen screen) {
        return new CardPage(SetupStep.INK, List.of(
                inkCard(screen, "battlecraft.setup.ink.soft", false),
                inkCard(screen, "battlecraft.setup.ink.exact", true)), INK_CARD_HEIGHT);
    }

    private static CardPage.Card inkCard(SetupScreen screen, String key, boolean accent) {
        return new CardPage.Card(
                Component.translatable(key),
                Component.translatable(key + ".note"),
                (graphics, typeface, centerX, top, width) -> InkSample.paint(graphics, typeface, accent, centerX, top),
                () -> {
                    Customization.accentInk(accent);
                    screen.answer(SetupStep.INK);
                }, null);
    }

    private static SetupPage font(SetupScreen screen) {
        List<CardPage.Card> cards = new ArrayList<>();
        for (FontShape shape : FontShape.values()) {
            cards.add(new CardPage.Card(
                    Component.translatable(shape.translationKey()),
                    Component.translatable(shape.noteKey()),
                    (graphics, typeface, centerX, top, width) -> FontSample.paint(graphics, typeface, shape, centerX, top, width),
                    () -> {
                        Customization.font(shape);
                        settleGlow(shape);
                        screen.answer(SetupStep.FONT);
                    }, null));
        }
        return new CardPage(SetupStep.FONT, cards, FONT_CARD_HEIGHT).onRelease(SetupPages::dropSamples);
    }

    // WHY: резкий образец задуман без ореола, поэтому выбравшему его свечение гасится сразу,
    // WHY: а не остаётся заводским: следующий шаг показывает ползунок уже на нуле и даёт вернуть
    private static void settleGlow(FontShape shape) {
        if (shape == FontShape.SHARP) Customization.glass(GlassKey.GLOW_ALPHA, 0.0f);
    }

    private static void dropSamples() {
        for (FontShape shape : FontShape.values()) {
            MsdfFontSets.drop(shape);
        }
    }

    private static SetupPage text(SetupScreen screen) {
        List<CardPage.Card> cards = new ArrayList<>();
        for (TextWeight weight : TextWeight.values()) {
            cards.add(new CardPage.Card(
                    Component.translatable(weight.translationKey()),
                    Component.translatable(weight.noteKey()),
                    (graphics, typeface, centerX, top, width) ->
                            FontSample.paintWeighted(graphics, typeface, weight.amount(), centerX, top, width),
                    () -> {
                        Customization.glass(GlassKey.TEXT_WEIGHT, weight.amount());
                        screen.answer(SetupStep.TEXT);
                    }, null));
        }
        return new CardPage(SetupStep.TEXT, cards, FONT_CARD_HEIGHT).extra(glow());
    }

    // WHY: свечение живёт на той же странице, что и толщина: образцы на карточках набраны
    // WHY: боевым текстом, поэтому ореол видно прямо во время протяжки ползунка
    private static CardPage.Extra glow() {
        return new CardPage.Extra() {
            @Override
            public AbstractWidget build(int left, int top, int width) {
                return new SliderRow(left, top, width, GLOW_HEIGHT,
                        Component.translatable("battlecraft.custom.glass.glowAlpha"),
                        () -> Customization.glass(GlassKey.GLOW_ALPHA),
                        value -> Customization.glass(GlassKey.GLOW_ALPHA, value),
                        GlassKey.GLOW_ALPHA.minimum(), GlassKey.GLOW_ALPHA.maximum(),
                        GlassKey.GLOW_ALPHA.step())
                        .readout(0, PERCENT, "%");
            }

            @Override
            public int height() {
                return GLOW_HEIGHT;
            }
        };
    }

    private static SetupPage voice(SetupScreen screen) {
        return new VoicePage(SetupStep.VOICE, () -> {
            VoiceOptions.settled();
            screen.answer(SetupStep.VOICE);
        });
    }

    private static SetupPage sound(SetupScreen screen) {
        List<CardPage.Card> cards = new ArrayList<>();
        for (UiSoundScheme heard : UiSoundScheme.values()) {
            cards.add(new CardPage.Card(
                    Component.translatable(heard.translationKey()),
                    Component.translatable(heard.noteKey()),
                    null,
                    () -> {
                        Customization.sound(heard);
                        screen.answer(SetupStep.SOUND);
                    },
                    null, probes(heard)));
        }
        return new CardPage(SetupStep.SOUND, cards, SOUND_CARD_HEIGHT).extra(volume());
    }

    // WHY: громкость живёт на той же странице, что и выбор схемы: слышно её тем же щелчком
    // WHY: ползунка на делении, поэтому отдельный шаг мастера ей не нужен
    private static CardPage.Extra volume() {
        return new CardPage.Extra() {
            @Override
            public AbstractWidget build(int left, int top, int width) {
                return new SliderRow(left, top, width, VOLUME_HEIGHT,
                        Component.translatable("battlecraft.custom.sound_volume"),
                        HudConfig::soundVolume, HudConfig::soundVolume, 0.0f, VOLUME_LOUDEST, VOLUME_STEP)
                        .readout(0, PERCENT, "%");
            }

            @Override
            public int height() {
                return VOLUME_HEIGHT;
            }
        };
    }

    private static List<CardPage.Probe> probes(UiSoundScheme heard) {
        return List.of(
                new CardPage.Probe(Component.translatable("battlecraft.setup.sound.press"),
                        () -> UiSound.preview(heard, UiCue.PRESS), null),
                new CardPage.Probe(Component.translatable("battlecraft.setup.sound.hover"),
                        () -> UiSound.preview(heard, UiCue.HOVER),
                        () -> UiSound.preview(heard, UiCue.HOVER)));
    }

    private static SetupPage discord(SetupScreen screen) {
        return new StackPage(SetupStep.DISCORD, List.of(
                new StackPage.Option(Component.translatable("battlecraft.setup.discord.on"),
                        Component.translatable("battlecraft.setup.discord.on.note"),
                        () -> answerDiscord(screen, true)),
                new StackPage.Option(Component.translatable("battlecraft.setup.discord.off"),
                        Component.translatable("battlecraft.setup.discord.off.note"),
                        () -> answerDiscord(screen, false))));
    }

    private static void answerDiscord(SetupScreen screen, boolean shown) {
        HudConfig.discordRpc(shown);
        DiscordRpcManager.getInstance().refresh();
        screen.answer(SetupStep.DISCORD);
    }
}
