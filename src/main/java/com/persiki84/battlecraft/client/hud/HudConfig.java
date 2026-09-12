package com.persiki84.battlecraft.client.hud;

import net.minecraftforge.common.ForgeConfigSpec;

public final class HudConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    private static ForgeConfigSpec.BooleanValue HOTBAR_AUTO_HIDE;
    private static ForgeConfigSpec.IntValue HOTBAR_HIDE_DELAY;
    private static ForgeConfigSpec.BooleanValue HOTBAR_COLLAPSE_EMPTY;
    private static ForgeConfigSpec.BooleanValue HOTBAR_SLOT_NUMBERS;
    private static ForgeConfigSpec.BooleanValue GLASS_REFRACTION;
    private static ForgeConfigSpec.DoubleValue SOUND_VOLUME;
    private static ForgeConfigSpec.BooleanValue HOVER_SOUND;
    private static ForgeConfigSpec.BooleanValue HUD_ACCENT_TEXT;
    private static ForgeConfigSpec.BooleanValue STATUS_BARS;
    private static ForgeConfigSpec.BooleanValue EFFECT_CHIPS;
    private static ForgeConfigSpec.BooleanValue BOSS_BARS;
    private static ForgeConfigSpec.BooleanValue CHAT_TOASTS;
    private static ForgeConfigSpec.BooleanValue CHAT_MOTION;
    private static ForgeConfigSpec.BooleanValue HEARTBEAT_SYNC;
    private static ForgeConfigSpec.BooleanValue HEARTBEAT_FATIGUE;
    private static ForgeConfigSpec.BooleanValue ITEM_FACTS;
    private static ForgeConfigSpec.IntValue PLAIN_SCREENS;
    private static ForgeConfigSpec.BooleanValue MENU_INTRO;
    private static ForgeConfigSpec.BooleanValue LOADING_SCREENS;
    private static ForgeConfigSpec.BooleanValue BOOT_SCREEN;
    private static ForgeConfigSpec.BooleanValue BROWSE_SCREENS;
    private static ForgeConfigSpec.BooleanValue WORLD_PANEL;
    private static ForgeConfigSpec.BooleanValue REVEAL_PROBE;
    private static ForgeConfigSpec.BooleanValue SCREEN_PROBE;
    private static ForgeConfigSpec.BooleanValue ISLAND;
    private static ForgeConfigSpec.BooleanValue ISLAND_MEDIA;
    private static ForgeConfigSpec.BooleanValue ISLAND_AVATAR;
    private static ForgeConfigSpec.BooleanValue ISLAND_NICK;
    private static ForgeConfigSpec.BooleanValue ISLAND_FPS;
    private static ForgeConfigSpec.BooleanValue ISLAND_PING;
    private static ForgeConfigSpec.BooleanValue ISLAND_COVER;
    private static ForgeConfigSpec.BooleanValue ISLAND_BAR;
    private static ForgeConfigSpec.BooleanValue ISLAND_TITLE;
    private static ForgeConfigSpec.BooleanValue ISLAND_ARTIST;
    private static ForgeConfigSpec.BooleanValue ISLAND_TIME;
    private static ForgeConfigSpec.BooleanValue ISLAND_VISUALIZER;
    private static ForgeConfigSpec.BooleanValue ISLAND_COVER_TINT;
    private static ForgeConfigSpec.DoubleValue VISUALIZER_GAIN;
    private static ForgeConfigSpec.DoubleValue VISUALIZER_SPEED;
    private static ForgeConfigSpec.DoubleValue VISUALIZER_LIGHT;
    private static ForgeConfigSpec.DoubleValue VISUALIZER_COLOR;
    private static ForgeConfigSpec.DoubleValue VISUALIZER_ATTACK;
    private static ForgeConfigSpec.DoubleValue ISLAND_FLIP_SPEED;
    private static ForgeConfigSpec.BooleanValue DISCORD_RPC;
    private static ForgeConfigSpec.BooleanValue ACCENT_FROM_LAUNCHER;
    private static ForgeConfigSpec.ConfigValue<String> ACCENT_COLOR;

    static {
        BUILDER.push("Интерфейс BattleCraft");

        defineHotbar();
        defineFeel();
        defineVanillaReplacements();
        defineScreens();
        defineIslandGroup();
        defineProfile();

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private static void defineHotbar() {
        HOTBAR_AUTO_HIDE = defineHotbarAutoHide();
        HOTBAR_HIDE_DELAY = defineHotbarHideDelay();
        HOTBAR_COLLAPSE_EMPTY = defineHotbarCollapseEmpty();
        HOTBAR_SLOT_NUMBERS = defineHotbarSlotNumbers();
    }

    private static void defineFeel() {
        GLASS_REFRACTION = defineGlassRefraction();
        SOUND_VOLUME = defineSoundVolume();
        HOVER_SOUND = defineHoverSound();
        HUD_ACCENT_TEXT = defineHudAccentText();
    }

    private static void defineVanillaReplacements() {
        STATUS_BARS = defineStatusBars();
        EFFECT_CHIPS = defineEffectChips();
        BOSS_BARS = defineBossBars();
        CHAT_TOASTS = defineChatToasts();
        CHAT_MOTION = defineChatMotion();
        HEARTBEAT_SYNC = defineHeartbeatSync();
        HEARTBEAT_FATIGUE = defineHeartbeatFatigue();
        ITEM_FACTS = defineItemFacts();
    }

    private static void defineScreens() {
        PLAIN_SCREENS = definePlainScreens();
        MENU_INTRO = defineMenuIntro();
        LOADING_SCREENS = defineLoadingScreens();
        BOOT_SCREEN = defineBootScreen();
        BROWSE_SCREENS = defineBrowseScreens();
        WORLD_PANEL = defineWorldPanel();
        REVEAL_PROBE = defineRevealProbe();
        SCREEN_PROBE = defineScreenProbe();
    }

    private static void defineIslandGroup() {
        ISLAND = defineIsland();
        ISLAND_MEDIA = defineIslandMedia();
        ISLAND_AVATAR = defineIslandPart("islandAvatar", "Аватар игрока в острове");
        ISLAND_NICK = defineIslandPart("islandNick", "Никнейм в острове");
        ISLAND_FPS = defineIslandPart("islandFps", "Частота кадров в острове");
        ISLAND_PING = defineIslandPart("islandPing", "Пинг в острове");
        ISLAND_COVER = defineIslandPart("islandCover", "Обложка играющего трека вместо аватара");
        ISLAND_BAR = defineIslandPart("islandBar", "Полоса прогресса трека");
        ISLAND_TITLE = defineIslandPart("islandTitle", "Название играющего трека");
        ISLAND_ARTIST = defineIslandPart("islandArtist", "Исполнитель играющего трека");
        ISLAND_TIME = defineIslandPart("islandTime", "Таймер трека");
        ISLAND_VISUALIZER = defineIslandPart("islandVisualizer", "Визуализатор звука в острове");
        ISLAND_COVER_TINT = defineIslandPart("islandCoverTint", "Полоски визуализатора цветом обложки трека");
        VISUALIZER_GAIN = defineVisualizerDial("visualizerGain", "Чувствительность визуализатора, 1 это обычная");
        VISUALIZER_SPEED = defineVisualizerDial("visualizerSpeed", "Резкость отклика полосок, 1 это обычная");
        VISUALIZER_LIGHT = defineVisualizerDial("visualizerLight", "Яркость цветов, снятых с обложки, 1 это обычная");
        VISUALIZER_COLOR = defineVisualizerDial("visualizerColor", "Насыщенность цветов, снятых с обложки, 1 это обычная");
        VISUALIZER_ATTACK = defineVisualizerDial("visualizerAttack", "Скорость подъёма полосок, 1 это обычная");
        ISLAND_FLIP_SPEED = defineVisualizerDial("islandFlipSpeed", "Скорость разворота обложки при смене трека, 1 это обычная");
    }

    private static ForgeConfigSpec.DoubleValue defineVisualizerDial(String key, String note) {
        return BUILDER.comment(note).defineInRange(key, 1.0, 0.25, 2.0);
    }

    private static void defineProfile() {
        DISCORD_RPC = defineDiscordRpc();
        ACCENT_FROM_LAUNCHER = defineAccentFromLauncher();
        ACCENT_COLOR = defineAccentColor();
    }

    private static ForgeConfigSpec.BooleanValue defineScreenProbe() {
        return BUILDER.comment("Диагностика: показывать состояние перекраски виджетов при закрытии экрана")
                .define("screenProbe", false);
    }

    private static ForgeConfigSpec.BooleanValue defineDiscordRpc() {
        return BUILDER.comment("Показывать игру в статусе Discord")
                .define("discordRpc", true);
    }

    private static ForgeConfigSpec.BooleanValue defineAccentFromLauncher() {
        return BUILDER.comment("Брать акцентный цвет из темы лаунчера (launcher_theme/theme.json)")
                .define("accentFromLauncher", true);
    }

    private static ForgeConfigSpec.ConfigValue<String> defineAccentColor() {
        return BUILDER.comment("Свой акцентный цвет в виде #RRGGBB, пустая строка отдаёт выбор лаунчеру")
                .define("accentColor", "");
    }

    private static ForgeConfigSpec.BooleanValue defineIsland() {
        return BUILDER.comment("Остров сверху экрана с аватаром, ником, частотой кадров и пингом")
                .define("island", true);
    }

    private static ForgeConfigSpec.BooleanValue defineIslandMedia() {
        return BUILDER.comment("Показывать в острове музыку из браузеров и плееров Windows")
                .define("islandMedia", true);
    }

    private static ForgeConfigSpec.BooleanValue defineHudAccentText() {
        return BUILDER.comment("Текст худа цветом акцента")
                .define("hudAccentText", true);
    }

    private static ForgeConfigSpec.BooleanValue defineIslandPart(String key, String comment) {
        return BUILDER.comment(comment).define(key, true);
    }

    private static ForgeConfigSpec.BooleanValue defineRevealProbe() {
        return BUILDER.comment("Диагностика: писать в лог ход анимации экранов")
                .define("revealProbe", false);
    }

    private static ForgeConfigSpec.BooleanValue defineMenuIntro() {
        return BUILDER.comment("Проявление интерфейса при входе в экран и выгорание при выходе")
                .define("menuIntro", true);
    }

    private static ForgeConfigSpec.BooleanValue defineLoadingScreens() {
        return BUILDER.comment("Свои экраны загрузки вместо ванильных: вход на сервер, загрузка мира, чтение данных")
                .define("loadingScreens", true);
    }

    private static ForgeConfigSpec.BooleanValue defineBootScreen() {
        return BUILDER.comment("Свой экран запуска игры поверх заставки загрузчика")
                .define("bootScreen", true);
    }

    private static ForgeConfigSpec.BooleanValue defineBrowseScreens() {
        return BUILDER.comment("Свои экраны выбора мира и сервера вместо ванильных")
                .define("browseScreens", true);
    }

    private static ForgeConfigSpec.BooleanValue defineWorldPanel() {
        return BUILDER.comment("Экраны мода висят в мире там, куда смотрел игрок, а не поверх кадра")
                .define("worldPanel", true);
    }

    private static ForgeConfigSpec.BooleanValue defineChatMotion() {
        return BUILDER.comment("Выезд поля ввода, архива и новых строк чата")
                .define("chatMotion", true);
    }

    private static ForgeConfigSpec.IntValue definePlainScreens() {
        return BUILDER.comment("Диагностика экранов: сумма флагов 1 (без пост-слоя), 2 (без вуали), 4 (ванильный инвентарь)")
                .defineInRange("plainScreens", 0, 0, 7);
    }

    private static ForgeConfigSpec.BooleanValue defineItemFacts() {
        return BUILDER.comment("Показывать характеристики предметов в подсказках")
                .define("itemFacts", true);
    }

    private static ForgeConfigSpec.BooleanValue defineGlassRefraction() {
        return BUILDER.comment("Преломление и размытие фона под панелями интерфейса")
                .define("glassRefraction", true);
    }

    private static ForgeConfigSpec.DoubleValue defineSoundVolume() {
        return BUILDER.comment("Громкость звуков интерфейса, 0 отключает их")
                .defineInRange("uiSoundVolume", 1.0, 0.0, 2.0);
    }

    private static ForgeConfigSpec.BooleanValue defineHoverSound() {
        return BUILDER.comment("Звук при наведении на кнопку")
                .define("uiHoverSound", true);
    }

    private static ForgeConfigSpec.BooleanValue defineHotbarAutoHide() {
        return BUILDER.comment("Скрывать хотбар, пока игрок его не использует")
                .define("hotbarAutoHide", true);
    }

    private static ForgeConfigSpec.IntValue defineHotbarHideDelay() {
        return BUILDER.comment("Задержка перед скрытием хотбара (мс)")
                .defineInRange("hotbarHideDelay", 1500, 300, 60000);
    }

    private static ForgeConfigSpec.BooleanValue defineHotbarCollapseEmpty() {
        return BUILDER.comment("Схлопывать пустые слоты хотбара")
                .define("hotbarCollapseEmpty", true);
    }

    private static ForgeConfigSpec.BooleanValue defineHotbarSlotNumbers() {
        return BUILDER.comment("Показывать номер клавиши на слотах хотбара")
                .define("hotbarSlotNumbers", true);
    }

    private static ForgeConfigSpec.BooleanValue defineStatusBars() {
        return BUILDER.comment("Заменять сердечки, броню, голод, кислород и опыт своими полосами")
                .define("customStatusBars", true);
    }

    private static ForgeConfigSpec.BooleanValue defineEffectChips() {
        return BUILDER.comment("Заменять иконки эффектов")
                .define("customEffects", true);
    }

    private static ForgeConfigSpec.BooleanValue defineBossBars() {
        return BUILDER.comment("Заменять полосы боссов")
                .define("customBossBars", true);
    }

    private static ForgeConfigSpec.BooleanValue defineChatToasts() {
        return BUILDER.comment("Показывать сообщения модов уведомлениями в интерфейсе вместо чата")
                .define("chatToasts", true);
    }

    private static ForgeConfigSpec.BooleanValue defineHeartbeatSync() {
        return BUILDER.comment("Подчинять сердцебиение EnhancedVisuals ритму линии ЭКГ")
                .define("heartbeatSync", true);
    }

    private static ForgeConfigSpec.BooleanValue defineHeartbeatFatigue() {
        return BUILDER.comment("Сердцебиение EnhancedVisuals от усталости, а не только от ран")
                .define("heartbeatFatigue", true);
    }

    private HudConfig() {}

    public static boolean hotbarAutoHide() {
        return read(HOTBAR_AUTO_HIDE, true);
    }

    public static int hotbarHideDelay() {
        return SPEC.isLoaded() ? HOTBAR_HIDE_DELAY.get() : 1500;
    }

    public static boolean collapseEmptySlots() {
        return read(HOTBAR_COLLAPSE_EMPTY, true);
    }

    public static boolean slotNumbers() {
        return read(HOTBAR_SLOT_NUMBERS, true);
    }

    public static boolean glassRefraction() {
        return read(GLASS_REFRACTION, true);
    }

    public static boolean discordRpc() {
        return read(DISCORD_RPC, true);
    }

    public static void discordRpc(boolean value) {
        if (SPEC.isLoaded()) DISCORD_RPC.set(value);
    }

    public static float soundVolume() {
        return SPEC.isLoaded() ? SOUND_VOLUME.get().floatValue() : 1.0f;
    }

    public static void soundVolume(float value) {
        if (SPEC.isLoaded()) SOUND_VOLUME.set((double) Math.max(0.0f, Math.min(2.0f, value)));
    }

    public static boolean hoverSound() {
        return read(HOVER_SOUND, true);
    }

    public static boolean statusBars() {
        return read(STATUS_BARS, true);
    }

    public static boolean effectChips() {
        return read(EFFECT_CHIPS, true);
    }

    public static boolean bossBars() {
        return read(BOSS_BARS, true);
    }

    public static boolean menuIntro() {
        return read(MENU_INTRO, true);
    }

    public static boolean loadingScreens() {
        return read(LOADING_SCREENS, true);
    }

    public static boolean bootScreen() {
        return read(BOOT_SCREEN, true);
    }

    public static boolean browseScreens() {
        return read(BROWSE_SCREENS, true);
    }

    public static boolean worldPanel() {
        return read(WORLD_PANEL, true);
    }

    public static void island(boolean value) {
        if (SPEC.isLoaded()) ISLAND.set(value);
    }

    public static void islandMedia(boolean value) {
        if (SPEC.isLoaded()) ISLAND_MEDIA.set(value);
    }

    public static boolean island() {
        return read(ISLAND, true);
    }

    public static boolean islandMedia() {
        return read(ISLAND_MEDIA, true);
    }


    public static void islandAvatar(boolean value) {
        if (SPEC.isLoaded()) ISLAND_AVATAR.set(value);
    }

    public static boolean islandAvatar() {
        return read(ISLAND_AVATAR, true);
    }

    public static void islandNick(boolean value) {
        if (SPEC.isLoaded()) ISLAND_NICK.set(value);
    }

    public static boolean islandNick() {
        return read(ISLAND_NICK, true);
    }

    public static void islandFps(boolean value) {
        if (SPEC.isLoaded()) ISLAND_FPS.set(value);
    }

    public static boolean islandFps() {
        return read(ISLAND_FPS, true);
    }

    public static void islandPing(boolean value) {
        if (SPEC.isLoaded()) ISLAND_PING.set(value);
    }

    public static boolean islandPing() {
        return read(ISLAND_PING, true);
    }

    public static void islandCover(boolean value) {
        if (SPEC.isLoaded()) ISLAND_COVER.set(value);
    }

    public static boolean islandCover() {
        return read(ISLAND_COVER, true);
    }

    public static void islandBar(boolean value) {
        if (SPEC.isLoaded()) ISLAND_BAR.set(value);
    }

    public static boolean islandBar() {
        return read(ISLAND_BAR, true);
    }

    public static void islandTitle(boolean value) {
        if (SPEC.isLoaded()) ISLAND_TITLE.set(value);
    }

    public static boolean islandTitle() {
        return read(ISLAND_TITLE, true);
    }

    public static void islandArtist(boolean value) {
        if (SPEC.isLoaded()) ISLAND_ARTIST.set(value);
    }

    public static boolean islandArtist() {
        return read(ISLAND_ARTIST, true);
    }

    public static void islandTime(boolean value) {
        if (SPEC.isLoaded()) ISLAND_TIME.set(value);
    }

    public static boolean islandTime() {
        return read(ISLAND_TIME, true);
    }

    public static void islandVisualizer(boolean value) {
        if (SPEC.isLoaded()) ISLAND_VISUALIZER.set(value);
    }

    public static float visualizerGain() {
        return SPEC.isLoaded() ? VISUALIZER_GAIN.get().floatValue() : 1.0f;
    }

    public static void visualizerGain(float value) {
        if (SPEC.isLoaded()) VISUALIZER_GAIN.set((double) value);
    }

    public static float visualizerSpeed() {
        return SPEC.isLoaded() ? VISUALIZER_SPEED.get().floatValue() : 1.0f;
    }

    public static void visualizerSpeed(float value) {
        if (SPEC.isLoaded()) VISUALIZER_SPEED.set((double) value);
    }

    public static float visualizerLight() {
        return SPEC.isLoaded() ? VISUALIZER_LIGHT.get().floatValue() : 1.0f;
    }

    public static void visualizerLight(float value) {
        if (SPEC.isLoaded()) VISUALIZER_LIGHT.set((double) value);
    }

    public static float visualizerColor() {
        return SPEC.isLoaded() ? VISUALIZER_COLOR.get().floatValue() : 1.0f;
    }

    public static void visualizerColor(float value) {
        if (SPEC.isLoaded()) VISUALIZER_COLOR.set((double) value);
    }

    public static float visualizerAttack() {
        return SPEC.isLoaded() ? VISUALIZER_ATTACK.get().floatValue() : 1.0f;
    }

    public static void visualizerAttack(float value) {
        if (SPEC.isLoaded()) VISUALIZER_ATTACK.set((double) value);
    }

    public static float islandFlipSpeed() {
        return SPEC.isLoaded() ? ISLAND_FLIP_SPEED.get().floatValue() : 1.0f;
    }

    public static void islandFlipSpeed(float value) {
        if (SPEC.isLoaded()) ISLAND_FLIP_SPEED.set((double) value);
    }

    public static boolean islandVisualizer() {
        return read(ISLAND_VISUALIZER, true);
    }

    public static void islandCoverTint(boolean value) {
        if (SPEC.isLoaded()) ISLAND_COVER_TINT.set(value);
    }

    public static boolean islandCoverTint() {
        return read(ISLAND_COVER_TINT, true);
    }

    public static void hudAccentText(boolean value) {
        if (SPEC.isLoaded()) HUD_ACCENT_TEXT.set(value);
    }

    public static boolean hudAccentText() {
        return read(HUD_ACCENT_TEXT, true);
    }

    public static boolean screenProbe() {
        return read(SCREEN_PROBE, false);
    }

    public static boolean revealProbe() {
        return read(REVEAL_PROBE, false);
    }

    public static boolean accentFromLauncher() {
        return read(ACCENT_FROM_LAUNCHER, true);
    }

    public static String accentColor() {
        return SPEC.isLoaded() ? ACCENT_COLOR.get() : "";
    }

    public static int plainScreens() {
        return SPEC.isLoaded() ? PLAIN_SCREENS.get() : 0;
    }

    public static boolean itemFacts() {
        return read(ITEM_FACTS, true);
    }

    public static boolean chatToasts() {
        return read(CHAT_TOASTS, true);
    }

    public static boolean chatMotion() {
        return read(CHAT_MOTION, true);
    }

    public static boolean heartbeatSync() {
        return read(HEARTBEAT_SYNC, true);
    }

    public static boolean heartbeatFatigue() {
        return read(HEARTBEAT_FATIGUE, true);
    }

    private static boolean read(ForgeConfigSpec.BooleanValue value, boolean fallback) {
        return SPEC.isLoaded() ? value.get() : fallback;
    }
}
