package com.persiki84.battlecraft.client.menu.custom;

import com.persiki84.battlecraft.client.custom.Customization;
import com.persiki84.battlecraft.client.custom.InterfaceDial;
import com.persiki84.battlecraft.client.custom.InterfaceFlag;
import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.battlecraft.client.island.AvatarPicture;
import com.persiki84.battlecraft.client.island.AvatarSource;
import com.persiki84.shared.client.menu.ActionRow;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class IslandSection {
    private static final float DIAL_LEAST = 0.25f;
    private static final float DIAL_MOST = 2.0f;
    // WHY: у ручек визуализатора единица это то, как он настроен изнутри (так их описывает конфиг),
    // WHY: а у сдвига лирики ноль: этими значениями ручки встают на сбросе группы
    private static final float DIAL_NORMAL = 1.0f;
    private static final float LYRICS_LEAST = -3.0f;
    private static final float LYRICS_MOST = 3.0f;
    private static final float LYRICS_STEP = 0.1f;
    private static final InterfaceFlag[] FLAGS = {
            InterfaceFlag.ISLAND, InterfaceFlag.ISLAND_MEDIA, InterfaceFlag.ISLAND_AVATAR, InterfaceFlag.ISLAND_NICK,
            InterfaceFlag.ISLAND_FPS, InterfaceFlag.ISLAND_PING, InterfaceFlag.ISLAND_COVER,
            InterfaceFlag.ISLAND_TITLE, InterfaceFlag.ISLAND_ARTIST, InterfaceFlag.ISLAND_LYRICS,
            InterfaceFlag.ISLAND_LYRICS_BRIDGE, InterfaceFlag.ISLAND_BAR, InterfaceFlag.ISLAND_TIME,
            InterfaceFlag.ISLAND_VISUALIZER, InterfaceFlag.ISLAND_COVER_TINT};

    private IslandSection() {}

    static CustomSection build(RowKit kit) {
        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(part(kit, "island", HudConfig::island, HudConfig::island));
        rows.add(part(kit, "island_media", HudConfig::islandMedia, HudConfig::islandMedia));
        rows.add(part(kit, "island.avatar", HudConfig::islandAvatar, HudConfig::islandAvatar));
        rows.add(Picks.avatar(kit));
        rows.add(pictureRow(kit));
        addParts(kit, rows);
        addDials(kit, rows);
        return CustomSection.of("ui.island", CustomTab.INTERFACE, "battlecraft.custom.group.island", true, rows,
                IslandSection::reset, PreviewFocus.ISLAND, List.of());
    }

    private static void addParts(RowKit kit, List<AbstractWidget> rows) {
        rows.add(part(kit, "island.nick", HudConfig::islandNick, HudConfig::islandNick));
        rows.add(part(kit, "island.fps", HudConfig::islandFps, HudConfig::islandFps));
        rows.add(part(kit, "island.ping", HudConfig::islandPing, HudConfig::islandPing));
        rows.add(part(kit, "island.cover", HudConfig::islandCover, HudConfig::islandCover));
        rows.add(part(kit, "island.title", HudConfig::islandTitle, HudConfig::islandTitle));
        rows.add(part(kit, "island.artist", HudConfig::islandArtist, HudConfig::islandArtist));
        rows.add(part(kit, "island.lyrics", HudConfig::islandLyrics, HudConfig::islandLyrics));
        rows.add(part(kit, "island.lyrics_bridge", HudConfig::islandLyricsBridge, HudConfig::islandLyricsBridge));
        rows.add(part(kit, "island.bar", HudConfig::islandBar, HudConfig::islandBar));
        rows.add(part(kit, "island.time", HudConfig::islandTime, HudConfig::islandTime));
        rows.add(part(kit, "island.visualizer", HudConfig::islandVisualizer, HudConfig::islandVisualizer));
        rows.add(part(kit, "island.cover_tint", HudConfig::islandCoverTint, HudConfig::islandCoverTint));
    }

    private static void addDials(RowKit kit, List<AbstractWidget> rows) {
        rows.add(dial(kit, "visualizer_gain", HudConfig::visualizerGain, HudConfig::visualizerGain));
        rows.add(dial(kit, "visualizer_speed", HudConfig::visualizerSpeed, HudConfig::visualizerSpeed));
        rows.add(dial(kit, "visualizer_light", HudConfig::visualizerLight, HudConfig::visualizerLight));
        rows.add(dial(kit, "visualizer_color", HudConfig::visualizerColor, HudConfig::visualizerColor));
        rows.add(dial(kit, "visualizer_attack", HudConfig::visualizerAttack, HudConfig::visualizerAttack));
        rows.add(dial(kit, "island_flip", HudConfig::islandFlipSpeed, HudConfig::islandFlipSpeed));
        rows.add(kit.plain("battlecraft.custom.island_lyrics_offset", HudConfig::islandLyricsOffset,
                HudConfig::islandLyricsOffset, LYRICS_LEAST, LYRICS_MOST, LYRICS_STEP));
    }

    private static AbstractWidget part(RowKit kit, String id, BooleanSupplier value, Consumer<Boolean> apply) {
        return kit.toggle("battlecraft.custom." + id, value, apply);
    }

    private static AbstractWidget dial(RowKit kit, String id, java.util.function.Supplier<Float> value,
                                       Consumer<Float> apply) {
        return kit.percent("battlecraft.custom." + id, value, apply, DIAL_LEAST, DIAL_MOST,
                InterfaceSections.DIAL_STEP);
    }

    private static ActionRow pictureRow(RowKit kit) {
        return new ActionRow(0, 0, kit.width(), RowKit.ROW_HEIGHT,
                Component.translatable("battlecraft.custom.island.avatar_picture"),
                () -> Component.translatable(AvatarPicture.present()
                        ? "battlecraft.custom.island.avatar_picture.change"
                        : "battlecraft.custom.island.avatar_picture.choose"),
                () -> kit.host().pickAvatarPicture());
    }

    private static void reset() {
        InterfaceSections.resetFlags(FLAGS);
        Customization.avatar(AvatarSource.FALLBACK);
        InterfaceDial.VISUALIZER_GAIN.reset();
        InterfaceDial.VISUALIZER_SPEED.reset();
        HudConfig.visualizerLight(DIAL_NORMAL);
        HudConfig.visualizerColor(DIAL_NORMAL);
        HudConfig.visualizerAttack(DIAL_NORMAL);
        HudConfig.islandFlipSpeed(DIAL_NORMAL);
        HudConfig.islandLyricsOffset(0.0f);
    }
}
