package com.persiki84.battlecraft.client.menu.custom;

import com.persiki84.battlecraft.client.DiscordRpcManager;
import com.persiki84.battlecraft.client.custom.Customization;
import com.persiki84.battlecraft.client.custom.FontLook;
import com.persiki84.battlecraft.client.custom.GlassKey;
import com.persiki84.battlecraft.client.custom.HudLayout;
import com.persiki84.battlecraft.client.custom.HudPlacement;
import com.persiki84.battlecraft.client.custom.HudSlot;
import com.persiki84.battlecraft.client.custom.InterfaceDial;
import com.persiki84.battlecraft.client.custom.InterfaceFlag;
import com.persiki84.battlecraft.client.custom.PaletteKey;
import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.battlecraft.client.hud.PointsView;
import com.persiki84.battlecraft.client.voice.VoiceOptions;
import com.persiki84.battlecraft.client.voice.VoiceRows;
import com.persiki84.battlecraft.compat.walkie.Walkie;
import com.persiki84.battlecraft.client.voice.RadioTalk;
import com.persiki84.shared.client.font.FontShape;
import com.persiki84.shared.client.menu.KeyRow;
import com.persiki84.shared.client.menu.ToggleRow;
import com.persiki84.shared.client.ui.UiMotionSet;
import com.persiki84.shared.client.ui.UiSoundScheme;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

final class InterfaceSections {
    static final float DIAL_STEP = 0.05f;
    private static final float MARKER_LEAST = 0.5f;
    private static final float MARKER_MOST = 1.5f;
    private static final float NOISE_LEAST = 0.0f;
    private static final float NOISE_MOST = 2.0f;
    private static final Component UNBOUND = Component.translatable("battlecraft.key.unbound");

    private InterfaceSections() {}

    static List<CustomSection> build(RowKit kit) {
        List<CustomSection> built = new ArrayList<>();
        built.add(general(kit));
        built.add(desktop(kit));
        built.add(damage(kit));
        built.add(IslandSection.build(kit));
        if (VoiceOptions.available()) built.add(voice(kit));
        if (Walkie.available()) built.add(radio(kit));
        built.add(elements(kit));
        return built;
    }

    private static CustomSection general(RowKit kit) {
        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(Picks.font(kit));
        rows.addAll(GlassSections.knobs(kit, GlassKey.Group.TEXT));
        rows.add(Picks.sound(kit));
        rows.add(Picks.motion(kit));
        rows.add(kit.percent("battlecraft.custom.sound_volume", HudConfig::soundVolume, HudConfig::soundVolume,
                0.0f, 1.0f, DIAL_STEP));
        rows.add(kit.toggle("battlecraft.custom.discord", HudConfig::discordRpc, value -> {
            HudConfig.discordRpc(value);
            DiscordRpcManager.getInstance().refresh();
        }));
        rows.add(kit.toggle("battlecraft.custom.heartbeat", Customization::heartbeat, value -> {
            Customization.heartbeat(value);
            Customization.save();
        }));
        rows.add(kit.percent("battlecraft.custom.marker_scale", HudConfig::markerScale, HudConfig::markerScale,
                MARKER_LEAST, MARKER_MOST, DIAL_STEP));
        rows.add(kit.key("battlecraft.custom.points_key", () -> PointsView.keyName(UNBOUND), PointsView::key));
        return CustomSection.of("ui.general", CustomTab.INTERFACE, "battlecraft.custom.group.general", false, rows,
                InterfaceSections::resetGeneral, PreviewFocus.GENERAL, List.of());
    }

    private static void resetGeneral() {
        FontLook.choose(FontShape.FALLBACK);
        Customization.sound(UiSoundScheme.FALLBACK);
        Customization.motion(UiMotionSet.IGNITE);
        Customization.heartbeat(false);
        InterfaceFlag.DISCORD.reset();
        InterfaceDial.SOUND_VOLUME.reset();
        InterfaceDial.MARKER_SCALE.reset();
        GlassSections.resetGroup(GlassKey.Group.TEXT);
    }

    private static CustomSection desktop(RowKit kit) {
        List<AbstractWidget> rows = List.of(
                kit.toggle("battlecraft.custom.desktop", HudConfig::menuDesktop, HudConfig::menuDesktop),
                kit.toggle("battlecraft.custom.wallpaper_dots", HudConfig::wallpaperDots, HudConfig::wallpaperDots),
                kit.toggle("battlecraft.custom.wallpaper_themed", HudConfig::wallpaperThemed,
                        HudConfig::wallpaperThemed));
        return CustomSection.of("ui.desktop", CustomTab.INTERFACE, "battlecraft.custom.group.desktop", true, rows,
                () -> resetFlags(InterfaceFlag.MENU_DESKTOP, InterfaceFlag.WALLPAPER_DOTS,
                        InterfaceFlag.WALLPAPER_THEMED), PreviewFocus.DESKTOP, List.of());
    }

    // WHY: у индикатора урона нет своей вкладки: выключатели, размер, время и оба цвета стоят
    // WHY: одной группой здесь, а цвета не дублируются во вкладке «Цвета»
    private static CustomSection damage(RowKit kit) {
        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(kit.toggle("battlecraft.custom.damage_numbers", HudConfig::damageNumbers, HudConfig::damageNumbers));
        rows.add(kit.toggle("battlecraft.custom.damage_stack", HudConfig::damageStack, HudConfig::damageStack));
        rows.add(kit.percent("battlecraft.custom.damage_scale", HudConfig::damageScale, HudConfig::damageScale,
                HudConfig.DAMAGE_SCALE_LEAST, HudConfig.DAMAGE_SCALE_MOST, DIAL_STEP));
        rows.add(kit.percent("battlecraft.custom.damage_time", HudConfig::damageTime, HudConfig::damageTime,
                HudConfig.DAMAGE_TIME_LEAST, HudConfig.DAMAGE_TIME_MOST, DIAL_STEP));
        List<PaletteKey> colors = new ArrayList<>();
        for (PaletteKey key : PaletteKey.values()) {
            if (!key.group().onInterfaceTab()) continue;
            colors.add(key);
            rows.add(kit.color(key));
        }
        return CustomSection.of("ui.damage", CustomTab.INTERFACE, "battlecraft.custom.group.damage", true, rows,
                () -> resetDamage(colors), PreviewFocus.DAMAGE, colors);
    }

    private static void resetDamage(List<PaletteKey> colors) {
        resetFlags(InterfaceFlag.DAMAGE_NUMBERS, InterfaceFlag.DAMAGE_STACK);
        InterfaceDial.DAMAGE_SCALE.reset();
        InterfaceDial.DAMAGE_TIME.reset();
        ColorSections.clear(colors);
    }

    // WHY: без мода строк нет вовсе: пустой раздел с мёртвыми списками устройств хуже отсутствия раздела
    private static CustomSection voice(RowKit kit) {
        List<AbstractWidget> rows = new ArrayList<>();
        for (AbstractWidget row : VoiceRows.build(0, 0, kit.width(), RowKit.ROW_HEIGHT, 0)) {
            rows.add(row);
            if (row instanceof KeyRow key) kit.talk(key);
        }
        return CustomSection.of("ui.voice", CustomTab.INTERFACE, "battlecraft.custom.group.voice", true, rows,
                null, PreviewFocus.VOICE, List.of());
    }

    // WHY: без мода рации строк нет вовсе: ручки помех и карточек эфира нечем проверить,
    // WHY: а выключатель того, чего в сборке нет, читается как поломка
    private static CustomSection radio(RowKit kit) {
        List<AbstractWidget> rows = List.of(
                kit.toggle("battlecraft.custom.radio_hud", HudConfig::radioHud, HudConfig::radioHud),
                kit.toggle("battlecraft.custom.voice_trace", HudConfig::voiceTrace, HudConfig::voiceTrace),
                kit.toggle("battlecraft.custom.radio_static", HudConfig::radioStatic, HudConfig::radioStatic),
                kit.toggle("battlecraft.custom.radio_chirp", HudConfig::radioChirp, HudConfig::radioChirp),
                kit.key("battlecraft.custom.radio_key", () -> RadioTalk.keyName(UNBOUND), RadioTalk::key),
                kit.percent("battlecraft.custom.radio_noise", HudConfig::radioNoise, HudConfig::radioNoise,
                        NOISE_LEAST, NOISE_MOST, DIAL_STEP));
        return CustomSection.of("ui.radio", CustomTab.INTERFACE, "battlecraft.custom.group.radio", true, rows,
                InterfaceSections::resetRadio, PreviewFocus.RADIO, List.of());
    }

    private static void resetRadio() {
        resetFlags(InterfaceFlag.RADIO_HUD, InterfaceFlag.VOICE_TRACE, InterfaceFlag.RADIO_STATIC,
                InterfaceFlag.RADIO_CHIRP);
        InterfaceDial.RADIO_NOISE.reset();
    }

    private static CustomSection elements(RowKit kit) {
        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(kit.toggle("battlecraft.custom.hud_follows_screen", HudConfig::hudFollowsScreen,
                HudConfig::hudFollowsScreen));
        rows.add(kit.heading("battlecraft.custom.hud.in_chat"));
        for (HudSlot slot : HudSlot.values()) {
            rows.add(slotRow(kit, slot));
        }
        return CustomSection.of("ui.elements", CustomTab.INTERFACE, "battlecraft.custom.group.elements", true, rows,
                InterfaceSections::resetElements, PreviewFocus.HUD, List.of());
    }

    private static void resetElements() {
        Customization.resetHud();
        InterfaceFlag.HUD_FOLLOWS_SCREEN.reset();
    }

    private static ToggleRow slotRow(RowKit kit, HudSlot slot) {
        HudPlacement placement = HudLayout.of(slot);
        ToggleRow row = new ToggleRow(0, 0, kit.width(), RowKit.ROW_HEIGHT,
                Component.translatable(slot.translationKey()), placement::visible, value -> {
                    placement.visible(value);
                    Customization.save();
                });
        row.hint("battlecraft.custom.hud.slot" + RowKit.HINT);
        kit.slot(row, slot);
        return row;
    }

    static void resetFlags(InterfaceFlag... flags) {
        for (InterfaceFlag flag : flags) {
            flag.reset();
        }
    }
}
