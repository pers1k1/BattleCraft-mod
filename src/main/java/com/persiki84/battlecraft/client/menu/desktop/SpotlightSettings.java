package com.persiki84.battlecraft.client.menu.desktop;

import com.persiki84.shared.client.ui.UiIcon;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.ChatOptionsScreen;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.LanguageSelectScreen;
import net.minecraft.client.gui.screens.MouseSettingsScreen;
import net.minecraft.client.gui.screens.OnlineOptionsScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.SkinCustomizationScreen;
import net.minecraft.client.gui.screens.SoundOptionsScreen;
import net.minecraft.client.gui.screens.VideoSettingsScreen;
import net.minecraft.client.gui.screens.controls.ControlsScreen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.client.gui.screens.telemetry.TelemetryInfoScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.PlayerModelPart;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

// WHY: ключи и порядок строк сняты с экранов настроек 1.20.1: у OptionInstance нет открытого
// WHY: ключа подписи, а без ключа не найти ни английское название, ни саму строку на экране.
// WHY: Ресурспаков здесь нет намеренно: RestrictedClient гасит их кнопку в настройках
final class SpotlightSettings {
    private static final Component KIND_SETTINGS = Component.translatable("battlecraft.desktop.search.settings");
    private static final Component KIND_KEY = Component.translatable("battlecraft.desktop.search.key");
    private static final String CHAT_DELAY = "battlecraft.desktop.search.chat_delay";

    private record Row(String key, String title, Function<Options, OptionInstance<?>> option) {
    }

    private enum Page {
        GENERAL(null, UiIcon.Kind.GEAR, OptionsScreen::new),
        VIDEO("options.video", UiIcon.Kind.SUN, VideoSettingsScreen::new),
        SOUND("options.sounds", UiIcon.Kind.SPEAKER, SoundOptionsScreen::new),
        CONTROLS("options.controls", UiIcon.Kind.TOGGLES, ControlsScreen::new),
        MOUSE("options.mouse_settings", UiIcon.Kind.TOGGLES, MouseSettingsScreen::new),
        KEYS("controls.keybinds", UiIcon.Kind.TOGGLES, KeyBindsScreen::new),
        CHAT("options.chat.title", UiIcon.Kind.NOTE, ChatOptionsScreen::new),
        SKIN("options.skinCustomisation", UiIcon.Kind.PERSON, SkinCustomizationScreen::new),
        ONLINE("options.online", UiIcon.Kind.GLOBE, SpotlightSettings::onlineScreen),
        ACCESSIBILITY("options.accessibility.title", UiIcon.Kind.PERSON, AccessibilityOptionsScreen::new),
        LANGUAGE("options.language", UiIcon.Kind.GLOBE, SpotlightSettings::languageScreen),
        TELEMETRY("options.telemetry", UiIcon.Kind.GEAR, TelemetryInfoScreen::new),
        CREDITS("options.credits_and_attribution", UiIcon.Kind.GEAR, SpotlightSettings::creditsScreen);

        private final String title;
        private final UiIcon.Kind glyph;
        private final BiFunction<Screen, Options, Screen> screen;

        Page(String title, UiIcon.Kind glyph, BiFunction<Screen, Options, Screen> screen) {
            this.title = title;
            this.glyph = glyph;
            this.screen = screen;
        }

        Screen open(Screen parent) {
            return screen.apply(parent, Minecraft.getInstance().options);
        }
    }

    private static final List<Row> GENERAL = List.of(row("options.fov", Options::fov));

    private static final List<Row> VIDEO = List.of(
            row("options.fullscreen.resolution", null),
            row("options.biomeBlendRadius", Options::biomeBlendRadius),
            row("options.graphics", Options::graphicsMode),
            row("options.renderDistance", Options::renderDistance),
            row("options.prioritizeChunkUpdates", Options::prioritizeChunkUpdates),
            row("options.simulationDistance", Options::simulationDistance),
            row("options.ao", Options::ambientOcclusion),
            row("options.framerateLimit", Options::framerateLimit),
            row("options.vsync", Options::enableVsync),
            row("options.viewBobbing", Options::bobView),
            row("options.guiScale", Options::guiScale),
            row("options.attackIndicator", Options::attackIndicator),
            row("options.gamma", Options::gamma),
            row("options.renderClouds", Options::cloudStatus),
            row("options.fullscreen", Options::fullscreen),
            row("options.particles", Options::particles),
            row("options.mipmapLevels", Options::mipmapLevels),
            row("options.entityShadows", Options::entityShadows),
            row("options.screenEffectScale", Options::screenEffectScale),
            row("options.entityDistanceScaling", Options::entityDistanceScaling),
            row("options.fovEffectScale", Options::fovEffectScale),
            row("options.autosaveIndicator", Options::showAutosaveIndicator),
            row("options.glintSpeed", Options::glintSpeed),
            row("options.glintStrength", Options::glintStrength));

    private static final List<Row> SOUND_TAIL = List.of(
            row("options.audioDevice", Options::soundDevice),
            row("options.showSubtitles", Options::showSubtitles),
            row("options.directionalAudio", Options::directionalAudio));

    private static final List<Row> CONTROLS = List.of(
            row("key.sneak", Options::toggleCrouch),
            row("key.sprint", Options::toggleSprint),
            row("options.autoJump", Options::autoJump),
            row("options.operatorItemsTab", Options::operatorItemsTab));

    private static final List<Row> MOUSE = List.of(
            row("options.sensitivity", Options::sensitivity),
            row("options.invertMouse", Options::invertYMouse),
            row("options.mouseWheelSensitivity", Options::mouseWheelSensitivity),
            row("options.discrete_mouse_scroll", Options::discreteMouseScroll),
            row("options.touchscreen", Options::touchscreen),
            row("options.rawMouseInput", Options::rawMouseInput));

    private static final List<Row> CHAT = List.of(
            row("options.chat.visibility", Options::chatVisibility),
            row("options.chat.color", Options::chatColors),
            row("options.chat.links", Options::chatLinks),
            row("options.chat.links.prompt", Options::chatLinksPrompt),
            row("options.chat.opacity", Options::chatOpacity),
            row("options.accessibility.text_background_opacity", Options::textBackgroundOpacity),
            row("options.chat.scale", Options::chatScale),
            row("options.chat.line_spacing", Options::chatLineSpacing),
            new Row("options.chat.delay_instant", CHAT_DELAY, Options::chatDelay),
            row("options.chat.width", Options::chatWidth),
            row("options.chat.height.focused", Options::chatHeightFocused),
            row("options.chat.height.unfocused", Options::chatHeightUnfocused),
            row("options.narrator", Options::narrator),
            row("options.autoSuggestCommands", Options::autoSuggestions),
            row("options.hideMatchedNames", Options::hideMatchedNames),
            row("options.reducedDebugInfo", Options::reducedDebugInfo),
            row("options.onlyShowSecureChat", Options::onlyShowSecureChat));

    private static final List<Row> ONLINE = List.of(
            row("options.realmsNotifications", Options::realmsNotifications),
            row("options.allowServerListing", Options::allowServerListing));

    private static final List<Row> ACCESSIBILITY = List.of(
            row("options.accessibility.high_contrast", Options::highContrast),
            row("options.accessibility.text_background", Options::backgroundForChatOnly),
            row("options.notifications.display_time", Options::notificationDisplayTime),
            row("options.darknessEffectScale", Options::darknessEffectScale),
            row("options.damageTiltStrength", Options::damageTiltStrength),
            row("options.hideLightningFlashes", Options::hideLightningFlash),
            row("options.darkMojangStudiosBackgroundColor", Options::darkMojangStudiosBackground),
            row("options.accessibility.panorama_speed", Options::panoramaSpeed));

    private static final List<Row> LANGUAGE = List.of(row("options.forceUnicodeFont", Options::forceUnicodeFont));

    private SpotlightSettings() {}

    private static Screen onlineScreen(Screen parent, Options options) {
        return OnlineOptionsScreen.createOnlineOptionsScreen(Minecraft.getInstance(), parent, options);
    }

    private static Screen languageScreen(Screen parent, Options options) {
        return new LanguageSelectScreen(parent, options, Minecraft.getInstance().getLanguageManager());
    }

    private static Screen creditsScreen(Screen parent, Options options) {
        return new CreditsAndAttributionScreen(parent);
    }

    private static Row row(String key, Function<Options, OptionInstance<?>> option) {
        return new Row(key, key, option);
    }

    static List<SpotlightSource.Result> all() {
        List<SpotlightSource.Result> made = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Page page : Page.values()) {
            if (page.title != null && seen.add(page.title)) made.add(section(page));
            for (Row row : rowsOf(page)) {
                if (seen.add(row.key())) made.add(option(page, row));
            }
        }
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            made.add(keyResult(mapping));
        }
        return made;
    }

    private static List<Row> rowsOf(Page page) {
        return switch (page) {
            case GENERAL -> GENERAL;
            case VIDEO -> VIDEO;
            case SOUND -> soundRows();
            case CONTROLS -> CONTROLS;
            case MOUSE -> MOUSE;
            case CHAT -> CHAT;
            case SKIN -> skinRows();
            case ONLINE -> ONLINE;
            case ACCESSIBILITY -> ACCESSIBILITY;
            case LANGUAGE -> LANGUAGE;
            default -> List.of();
        };
    }

    private static List<Row> soundRows() {
        List<Row> rows = new ArrayList<>();
        for (SoundSource source : SoundSource.values()) {
            rows.add(row("soundCategory." + source.getName(), options -> options.getSoundSourceOptionInstance(source)));
        }
        rows.addAll(SOUND_TAIL);
        return rows;
    }

    private static List<Row> skinRows() {
        List<Row> rows = new ArrayList<>();
        for (PlayerModelPart part : PlayerModelPart.values()) {
            String key = SpotlightRank.keyOf(part.getName());
            if (key != null) rows.add(row(key, null));
        }
        rows.add(row("options.mainHand", Options::mainHand));
        return rows;
    }

    private static SpotlightSource.Result section(Page page) {
        return new SpotlightSource.Result(plain(page.title), KIND_SETTINGS, page.glyph,
                owner -> Minecraft.getInstance().setScreen(page.open(owner)), page.title);
    }

    private static SpotlightSource.Result option(Page page, Row row) {
        SpotlightTarget target = SpotlightTarget.option(row.key(), row.option());
        return new SpotlightSource.Result(plain(row.title()), KIND_SETTINGS, page.glyph,
                owner -> SpotlightJump.open(page.open(owner), target), row.title());
    }

    private static SpotlightSource.Result keyResult(KeyMapping mapping) {
        SpotlightTarget target = SpotlightTarget.key(mapping);
        return new SpotlightSource.Result(Component.translatable(mapping.getName()), KIND_KEY, UiIcon.Kind.TOGGLES,
                owner -> SpotlightJump.open(Page.KEYS.open(owner), target), mapping.getName());
    }

    // WHY: подписи кнопок перехода в ванили кончаются многоточием («Музыка и звуки...»), а в
    // WHY: строке поиска оно читается как обрезанный текст
    private static Component plain(String key) {
        return Component.literal(SpotlightRank.withoutEllipsis(Component.translatable(key).getString()));
    }
}
