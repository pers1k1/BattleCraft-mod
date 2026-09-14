package com.persiki84.battlecraft.client.menu.custom;

import com.persiki84.battlecraft.client.custom.ConfigSection;
import com.persiki84.battlecraft.client.custom.CustomPreset;
import com.persiki84.battlecraft.client.custom.Customization;
import com.persiki84.battlecraft.client.custom.GlassKey;
import com.persiki84.battlecraft.client.custom.HudLayout;
import com.persiki84.battlecraft.client.custom.HudPlacement;
import com.persiki84.battlecraft.client.custom.HudSlot;
import com.persiki84.battlecraft.client.custom.PaletteKey;
import com.persiki84.battlecraft.client.custom.PresetLibrary;
import com.persiki84.battlecraft.client.custom.UserPreset;
import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.battlecraft.client.hud.PointsView;
import com.persiki84.battlecraft.client.voice.VoiceOptions;
import com.persiki84.battlecraft.client.voice.VoiceRows;
import com.persiki84.battlecraft.client.DiscordRpcManager;
import com.persiki84.shared.client.font.FontShape;
import com.persiki84.shared.client.ui.UiCue;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiSoundScheme;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.ColorRow;
import com.persiki84.shared.client.menu.HeadingRow;
import com.persiki84.shared.client.menu.KeyRow;
import com.persiki84.shared.client.menu.GlidingRow;
import com.persiki84.shared.client.menu.ManagerScreen;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.MenuField;
import com.persiki84.shared.client.menu.PaletteStack;
import com.persiki84.shared.client.menu.MenuRow;
import com.persiki84.shared.client.menu.PickRow;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiMotionSet;
import com.persiki84.shared.client.menu.SliderRow;
import com.persiki84.shared.client.menu.ToggleRow;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CustomizeScreen extends ManagerScreen {
    private static final float ENTER_SECONDS = 1.05f;
    private static final int TAB_GLASS = 0;
    private static final int TAB_COLORS = 1;
    private static final int TAB_INTERFACE = 2;
    private static final float DIAL_LEAST = 0.25f;
    private static final float DIAL_MOST = 2.0f;
    private static final float DIAL_STEP = 0.05f;
    private static final float MARKER_LEAST = 0.5f;
    private static final float MARKER_MOST = 1.5f;
    private static final int TAB_PRESETS = 3;
    private static final int TAB_MINE = 4;

    private static final int PREVIEW_GAP = 12;
    private static final int PANEL_WIDTH = 430;
    private static final float FIT_MARGIN = 10.0f;
    private static final int LIST_WIDTH = 138;
    private static final int COLUMN_GAP = 8;
    private static final int MINE_ROWS = MenuField.ROW_EQUIVALENT + ConfigSection.ALL.size() + 4;

    private final PreviewPane preview = new PreviewPane();
    private final PaletteStack windows = new PaletteStack();

    private final Map<Integer, List<AbstractWidget>> cached = new HashMap<>();
    private final Map<MenuRow, GlassKey> glassRowKeys = new HashMap<>();
    private final Set<ConfigSection> chosen = EnumSet.allOf(ConfigSection.class);

    private final List<KeyRow> bindingRows = new ArrayList<>();
    private KeyRow talkKey;
    private ToggleRow hudAccentRow;
    private MenuField nameField;
    private String presetName = "";
    private Path selectedFile;
    private int tab;
    private int rows;

    public CustomizeScreen() {
        super(Component.translatable("battlecraft.custom.title"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new CustomizeScreen());
    }

    @Override
    protected List<Component> tabs() {
        return List.of(
                Component.translatable("battlecraft.custom.tab.glass"),
                Component.translatable("battlecraft.custom.tab.colors"),
                Component.translatable("battlecraft.custom.tab.interface"),
                Component.translatable("battlecraft.custom.tab.presets"),
                Component.translatable("battlecraft.custom.tab.mine"));
    }

    @Override
    protected int activeTab() {
        return tab;
    }

    @Override
    protected void pickTab(int index) {
        tab = index;
        rowScroll = 0;
        listScroll = 0;
        windows.closeAll();
        if (tab == TAB_MINE) PresetLibrary.refresh();
    }

    @Override
    protected int desiredRows() {
        return rows;
    }


    @Override
    protected int contentLeft() {
        return (this.width - (PANEL_WIDTH + PREVIEW_GAP + PreviewPane.WIDTH)) / 2;
    }

    @Override
    protected int listWidth() {
        return tab == TAB_MINE ? LIST_WIDTH : 0;
    }

    @Override
    protected float contentScale() {
        float needed = PANEL_WIDTH + PREVIEW_GAP + PreviewPane.WIDTH + FIT_MARGIN * 2.0f;
        return needed <= this.width ? 1.0f : this.width / needed;
    }

    @Override
    protected void init() {
        cached.clear();
        glassRowKeys.clear();
        bindingRows.clear();
        hudAccentRow = null;
        super.init();
    }

    @Override
    protected void buildBody() {
        if (tab == TAB_MINE) {
            buildMine();
            return;
        }

        List<AbstractWidget> built = rowsOf(tab);
        for (Map.Entry<MenuRow, GlassKey> entry : glassRowKeys.entrySet()) {
            String reason = entry.getValue().reasonKey(Customization.liquid());
            if (reason == null) {
                entry.getKey().unblock();
            } else {
                entry.getKey().block(Component.translatable(reason));
            }
        }
        holdHudAccent();
        place(built);
        rows = shownRows;
    }

    // WHY: страницы собираются через общий кэш и для всеобщего поиска тоже: второй сборщик
    // WHY: наплодил бы строк-двойников, и правка ползунка уходила бы не в ту из них
    @Override
    protected List<AbstractWidget> rowsOf(int page) {
        if (page == TAB_MINE) return null;

        return cached.computeIfAbsent(page, built -> switch (built) {
            case TAB_COLORS -> colorRows();
            case TAB_INTERFACE -> interfaceRows();
            case TAB_PRESETS -> presetRows();
            default -> glassRows();
        });
    }

    private List<AbstractWidget> glassRows() {
        glassRowKeys.clear();
        List<AbstractWidget> built = new ArrayList<>();
        built.add(toggle("battlecraft.custom.liquid", Customization::liquid, value -> {
            Customization.liquid(value);
            Customization.save();
            rebuild();
        }).note(Component.translatable("battlecraft.custom.liquid.credit")));

        for (GlassKey.Group group : GlassKey.Group.values()) {
            if (group == GlassKey.Group.TEXT) continue;

            built.add(heading("battlecraft.custom.group." + group.id()));
            built.addAll(groupHead(group));
            built.addAll(groupSliders(group));
        }
        built.add(action("battlecraft.custom.reset.glass", "battlecraft.custom.reset", () -> {
            Customization.resetGlass();
            Customization.save();
            rebuild();
        }));
        return built;
    }

    private List<AbstractWidget> groupSliders(GlassKey.Group group) {
        List<AbstractWidget> built = new ArrayList<>();
        for (GlassKey key : GlassKey.values()) {
            if (key.group() == group) built.add(slider(key));
        }
        return built;
    }

    private List<AbstractWidget> groupHead(GlassKey.Group group) {
        return switch (group) {
            case MATERIAL -> List.<AbstractWidget>of(glassThemeRow(), colorRow(PaletteKey.GLASS));
            default -> List.<AbstractWidget>of();
        };
    }

    private ToggleRow glassThemeRow() {
        return toggle("battlecraft.custom.glass_theme", Customization::glassFollowsTheme, value -> {
            Customization.glassFollowsTheme(value);
            Customization.save();
            rebuild();
        });
    }

    private SliderRow slider(GlassKey key) {
        SliderRow row = new SliderRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable(key.translationKey()),
                () -> Customization.glass(key), value -> {
                    Customization.glass(key, value);
                    Customization.save();
                }, key.minimum(), key.maximum(), key.step());
        glassRowKeys.put(row, key);
        row.hint(key.hintKey());
        return row.readout(decimalsFor(key), 1.0f, "");
    }

    private static int decimalsFor(GlassKey key) {
        if (key.step() >= 1.0f) return 0;
        if (key.step() >= 0.05f) return 2;
        return 3;
    }

    private List<AbstractWidget> colorRows() {
        List<AbstractWidget> built = new ArrayList<>(inkRows());
        for (PaletteKey.Group group : PaletteKey.Group.values()) {
            if (group.onGlassTab()) continue;

            built.add(heading("battlecraft.custom.group." + group.id()));
            for (PaletteKey key : PaletteKey.values()) {
                if (key.group() == group) built.add(colorRow(key));
            }
        }
        built.add(action("battlecraft.custom.follow_theme", "battlecraft.custom.apply", () -> {
            Customization.followTheme();
            Customization.save();
            rebuild();
        }));
        built.add(action("battlecraft.custom.reset.colors", "battlecraft.custom.reset", () -> {
            Customization.resetColors();
            Customization.save();
            rebuild();
        }).alerting());
        return built;
    }

    private List<AbstractWidget> inkRows() {
        hudAccentRow = toggle("battlecraft.custom.hud_accent",
                HudConfig::hudAccentText, HudConfig::hudAccentText);
        return List.of(
                toggle("battlecraft.custom.follow_launcher", Customization::followLauncher, value -> {
                    Customization.followLauncher(value);
                    Customization.save();
                }),
                toggle("battlecraft.custom.accent_ink", Customization::accentInk, value -> {
                    Customization.accentInk(value);
                    Customization.save();
                    rebuild();
                }),
                hudAccentRow);
    }

    // WHY: под акцентом уже весь текст, и худ вместе с ним, поэтому отдельная ручка худа не решает
    // WHY: ничего: она гаснет и говорит почему, а не молчит включённой без действия
    private void holdHudAccent() {
        if (hudAccentRow == null) return;

        if (Customization.accentInk()) {
            hudAccentRow.block(Component.translatable("battlecraft.custom.blocked.accent_ink"));
        } else {
            hudAccentRow.unblock();
        }
    }

    private ColorRow colorRow(PaletteKey key) {
        ColorRow row = new ColorRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable(key.translationKey()),
                () -> Customization.color(key),
                () -> Customization.colorTouched(key),
                () -> openPicker(key));
        row.hint(key.hintKey());
        return row;
    }

    private void openPicker(PaletteKey key) {
        windows.toggle(this.width, this.height, key.id(), Component.translatable(key.translationKey()),
                Customization.color(key), argb -> {
                    Customization.color(key, argb);
                    Customization.save();
                }, () -> {
                    Customization.clearColor(key);
                    Customization.save();
                    rebuild();
                });
    }

    private List<AbstractWidget> interfaceRows() {
        bindingRows.clear();
        List<AbstractWidget> built = new ArrayList<>();
        built.add(fontRow());
        built.addAll(groupSliders(GlassKey.Group.TEXT));
        built.add(soundRow());
        built.add(motionRow());
        built.add(volumeRow());
        built.add(toggle("battlecraft.custom.discord", HudConfig::discordRpc, value -> {
            HudConfig.discordRpc(value);
            DiscordRpcManager.getInstance().refresh();
        }));
        built.add(toggle("battlecraft.custom.heartbeat", Customization::heartbeat, value -> {
            Customization.heartbeat(value);
            Customization.save();
        }));
        built.add(markerRow());
        built.add(pointsKeyRow());
        addIslandRows(built);
        addVoiceRows(built);
        built.add(heading("battlecraft.custom.group.elements"));
        built.add(heading("battlecraft.custom.hud.in_chat"));
        for (HudSlot slot : HudSlot.values()) {
            built.add(slotRow(slot));
        }
        built.add(action("battlecraft.custom.reset.hud", "battlecraft.custom.reset", () -> {
            Customization.resetHud();
            Customization.save();
            rebuild();
        }));
        return built;
    }

    // WHY: без мода строк нет вовсе: пустой раздел с мёртвыми списками устройств хуже отсутствия раздела
    private void addVoiceRows(List<AbstractWidget> built) {
        talkKey = null;
        if (!VoiceOptions.available()) return;

        built.add(heading("battlecraft.custom.group.voice"));
        for (AbstractWidget row : VoiceRows.build(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, 0)) {
            built.add(row);
            if (row instanceof KeyRow key) {
                talkKey = key;
                bindingRows.add(key);
            }
        }
    }

    // WHY: строки вкладки кэшируются, поэтому строка клавиши переживает уход на другую вкладку
    // WHY: и ловила бы там нажатия, которых не видно
    private KeyRow voiceKey() {
        return tab == TAB_INTERFACE ? talkKey : null;
    }

    private List<KeyRow> listeningRows() {
        return tab == TAB_INTERFACE ? bindingRows : List.of();
    }

    private KeyRow pointsKeyRow() {
        KeyRow row = new KeyRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("battlecraft.custom.points_key"),
                () -> PointsView.keyName(Component.translatable("battlecraft.key.unbound")),
                PointsView::key);
        row.hint("battlecraft.custom.points_key" + HINT_SUFFIX);
        bindingRows.add(row);
        return row;
    }

    private void addIslandRows(List<AbstractWidget> built) {
        built.add(toggle("battlecraft.custom.island", HudConfig::island, HudConfig::island));
        built.add(toggle("battlecraft.custom.island_media", HudConfig::islandMedia, HudConfig::islandMedia));
        built.add(heading("battlecraft.custom.group.island"));
        built.add(toggle("battlecraft.custom.island.avatar", HudConfig::islandAvatar, HudConfig::islandAvatar));
        built.add(toggle("battlecraft.custom.island.nick", HudConfig::islandNick, HudConfig::islandNick));
        built.add(toggle("battlecraft.custom.island.fps", HudConfig::islandFps, HudConfig::islandFps));
        built.add(toggle("battlecraft.custom.island.ping", HudConfig::islandPing, HudConfig::islandPing));
        built.add(toggle("battlecraft.custom.island.cover", HudConfig::islandCover, HudConfig::islandCover));
        built.add(toggle("battlecraft.custom.island.title", HudConfig::islandTitle, HudConfig::islandTitle));
        built.add(toggle("battlecraft.custom.island.artist", HudConfig::islandArtist, HudConfig::islandArtist));
        built.add(toggle("battlecraft.custom.island.bar", HudConfig::islandBar, HudConfig::islandBar));
        built.add(toggle("battlecraft.custom.island.time", HudConfig::islandTime, HudConfig::islandTime));
        built.add(toggle("battlecraft.custom.island.visualizer", HudConfig::islandVisualizer, HudConfig::islandVisualizer));
        built.add(toggle("battlecraft.custom.island.cover_tint", HudConfig::islandCoverTint, HudConfig::islandCoverTint));
        built.add(dial("battlecraft.custom.visualizer_gain", HudConfig::visualizerGain, HudConfig::visualizerGain));
        built.add(dial("battlecraft.custom.visualizer_speed", HudConfig::visualizerSpeed, HudConfig::visualizerSpeed));
        built.add(dial("battlecraft.custom.visualizer_light", HudConfig::visualizerLight, HudConfig::visualizerLight));
        built.add(dial("battlecraft.custom.visualizer_color", HudConfig::visualizerColor, HudConfig::visualizerColor));
        built.add(dial("battlecraft.custom.visualizer_attack", HudConfig::visualizerAttack, HudConfig::visualizerAttack));
        built.add(dial("battlecraft.custom.island_flip", HudConfig::islandFlipSpeed, HudConfig::islandFlipSpeed));
    }

    private SliderRow markerRow() {
        SliderRow row = new SliderRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("battlecraft.custom.marker_scale"),
                HudConfig::markerScale, HudConfig::markerScale, MARKER_LEAST, MARKER_MOST, DIAL_STEP);
        row.hint("battlecraft.custom.marker_scale" + HINT_SUFFIX);
        return row.readout(0, 100.0f, "%");
    }

    private PickRow fontRow() {
        List<Component> options = new ArrayList<>();
        for (FontShape shape : FontShape.values()) {
            options.add(Component.translatable(shape.translationKey()));
        }
        PickRow row = new PickRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("battlecraft.custom.font"), options,
                () -> Customization.font().ordinal(),
                picked -> {
                    Customization.font(FontShape.values()[picked]);
                    Customization.save();
                });
        row.hint("battlecraft.custom.font" + HINT_SUFFIX);
        return row;
    }

    private PickRow motionRow() {
        List<Component> options = new ArrayList<>();
        for (UiMotionSet set : UiMotionSet.values()) {
            options.add(Component.translatable(set.translationKey()));
        }
        PickRow row = new PickRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("battlecraft.custom.motion"), options,
                () -> Customization.motion().ordinal(),
                picked -> {
                    Customization.motion(UiMotionSet.values()[picked]);
                    Customization.save();
                });
        row.hint("battlecraft.custom.motion" + HINT_SUFFIX);
        return row;
    }

    private PickRow soundRow() {
        List<Component> options = new ArrayList<>();
        for (UiSoundScheme scheme : UiSoundScheme.values()) {
            options.add(Component.translatable(scheme.translationKey()));
        }
        PickRow row = new PickRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("battlecraft.custom.sound"), options,
                () -> Customization.sound().ordinal(),
                picked -> {
                    Customization.sound(UiSoundScheme.values()[picked]);
                    Customization.save();
                    UiSound.preview(Customization.sound(), UiCue.PRESS);
                });
        row.hint("battlecraft.custom.sound" + HINT_SUFFIX);
        return row;
    }

    // WHY: ручки визуализатора живут в процентах от обычного: единица это то, как он настроен
    // WHY: изнутри, и игрок двигает только множитель, а не сами полосы частот
    private SliderRow dial(String key, java.util.function.Supplier<Float> value,
                           java.util.function.Consumer<Float> apply) {
        SliderRow row = new SliderRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, Component.translatable(key),
                value, apply, DIAL_LEAST, DIAL_MOST, DIAL_STEP);
        row.hint(key + HINT_SUFFIX);
        return row.readout(0, 100.0f, "%");
    }

    // WHY: уровень слышно прямо на ползунке: его собственный щелчок на делении идёт тем же
    // WHY: голосом интерфейса, что и кнопки
    private SliderRow volumeRow() {
        SliderRow row = new SliderRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("battlecraft.custom.sound_volume"),
                HudConfig::soundVolume, HudConfig::soundVolume, 0.0f, 1.0f, 0.05f);
        row.hint("battlecraft.custom.sound_volume" + HINT_SUFFIX);
        return row.readout(0, 100.0f, "%");
    }

    private ToggleRow slotRow(HudSlot slot) {
        HudPlacement placement = HudLayout.of(slot);
        ToggleRow row = new ToggleRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable(slot.translationKey()),
                placement::visible, value -> {
                    placement.visible(value);
                    Customization.save();
                });
        row.hint("battlecraft.custom.hud.slot" + HINT_SUFFIX);
        return row;
    }

    private List<AbstractWidget> presetRows() {
        List<AbstractWidget> built = new ArrayList<>();
        built.add(new HeadingRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("battlecraft.custom.preset.current",
                        Component.translatable(Customization.preset().translationKey()))));
        for (CustomPreset preset : CustomPreset.values()) {
            built.add(presetRow(preset));
        }
        built.add(heading("battlecraft.custom.group.reset"));
        built.add(action("battlecraft.custom.reset.all", "battlecraft.custom.reset", () -> {
            Customization.resetAll();
            Customization.save();
            rebuild();
        }).alerting());
        return built;
    }

    private ActionRow presetRow(CustomPreset preset) {
        return new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable(preset.translationKey()),
                () -> Customization.preset() == preset
                        ? Component.translatable("battlecraft.custom.preset.chosen")
                        : Component.translatable(preset.hintKey()), () -> {
                    Customization.preset(preset);
                    Customization.save();
                    cached.remove(TAB_PRESETS);
                    cached.remove(TAB_GLASS);
                    rebuild();
                });
    }

    // WHY: поле имени выше строк, поэтому вкладка раскладывается вручную, как вкладка создания
    // WHY: в менеджере меток: place() кладёт только ряды одной высоты и своей прокруткой их режет
    private void buildMine() {
        List<UserPreset> saved = PresetLibrary.all();
        addPresetList(saved);

        int x = rowsLeft();
        int y = contentTop() + PANEL_PAD;
        nameField = new MenuField(x, y, rowsWidth(), Component.translatable("battlecraft.custom.mine.name"),
                presetName, value -> presetName = value);
        addRenderableWidget(nameField.box());
        y += MenuField.BLOCK_HEIGHT + ROW_GAP;

        for (ConfigSection section : ConfigSection.values()) {
            addRenderableWidget(sectionRow(section, x, y));
            y += ROW_HEIGHT + ROW_GAP;
        }
        addMineActions(x, y);

        rows = MINE_ROWS;
        shownRows = MINE_ROWS;
        rowScroll = clampScroll(0, MINE_ROWS, MINE_ROWS);
    }

    private void addMineActions(int x, int y) {
        addRenderableWidget(mineAction(x, y, "battlecraft.custom.mine.save", "battlecraft.custom.mine.do_save",
                this::saveMine, false));
        y += ROW_HEIGHT + ROW_GAP;

        addRenderableWidget(mineAction(x, y, "battlecraft.custom.mine.apply", "battlecraft.custom.mine.do_apply",
                this::applyMine, true));
        y += ROW_HEIGHT + ROW_GAP;

        addRenderableWidget(mineAction(x, y, "battlecraft.custom.mine.delete", "battlecraft.custom.mine.do_delete",
                this::deleteMine, true).alerting());
        y += ROW_HEIGHT + ROW_GAP;

        addRenderableWidget(mineAction(x, y, "battlecraft.custom.mine.folder", "battlecraft.custom.mine.do_open",
                this::openFolder, false));
    }

    private ActionRow mineAction(int x, int y, String label, String value, Runnable run, boolean needsPick) {
        ActionRow row = new ActionRow(x, y, rowsWidth(), ROW_HEIGHT, Component.translatable(label),
                () -> Component.translatable(value), run);
        row.hint(label + HINT_SUFFIX);
        if (needsPick && selected() == null) {
            row.block(Component.translatable("battlecraft.custom.blocked.no_preset"));
        }
        return row;
    }

    private ToggleRow sectionRow(ConfigSection section, int x, int y) {
        ToggleRow row = new ToggleRow(x, y, rowsWidth(), ROW_HEIGHT,
                Component.translatable(section.translationKey()),
                () -> chosen.contains(section), value -> {
                    if (value) {
                        chosen.add(section);
                    } else {
                        chosen.remove(section);
                    }
                });
        row.hint("battlecraft.custom.mine.section" + HINT_SUFFIX);
        return row;
    }

    private void addPresetList(List<UserPreset> saved) {
        int capacity = rowCapacity(contentHeight() - PANEL_PAD * 2, saved.size());
        listScroll = clampList(listScroll, saved.size(), capacity);

        int x = listLeft();
        int y = listWindowTop();
        for (UserPreset preset : listWindow(saved)) {
            UiButton button = new UiButton(x, y, LIST_WIDTH, ROW_HEIGHT, Component.literal(preset.name()),
                    pressed -> pickPreset(preset));
            button.active = !preset.file().equals(selectedFile);
            button.anchor(y, GlidingRow.Lane.LIST);
            addRenderableWidget(button);
            y += ROW_HEIGHT + ROW_GAP;
        }
    }

    // WHY: выбор хранится путём, а не самим пресетом: перечитывание папки собирает новые записи,
    // WHY: и ссылка на прежнюю превратилась бы в вечно невыбранный список
    private UserPreset selected() {
        if (selectedFile == null) return null;

        for (UserPreset preset : PresetLibrary.all()) {
            if (preset.file().equals(selectedFile)) return preset;
        }
        return null;
    }

    private void pickPreset(UserPreset preset) {
        selectedFile = preset.file();
        presetName = preset.name();
        if (!preset.sections().isEmpty()) {
            chosen.clear();
            chosen.addAll(preset.sections());
        }
        rebuild();
    }

    private void saveMine() {
        PresetLibrary.Result result = PresetLibrary.save(presetName, chosen);
        MenuFeedback.show(Component.translatable(result.translationKey()), result.alerting());
        if (result == PresetLibrary.Result.SAVED) {
            selectedFile = null;
            rebuild();
        }
    }

    private void applyMine() {
        UserPreset preset = selected();
        if (preset == null) return;

        PresetLibrary.apply(preset);
        cached.clear();
        MenuFeedback.show(Component.translatable("battlecraft.custom.mine.applied",
                Component.literal(preset.name())), false);
        rebuild();
    }

    private void deleteMine() {
        UserPreset preset = selected();
        if (preset == null) return;

        boolean gone = PresetLibrary.delete(preset);
        MenuFeedback.show(Component.translatable(gone
                ? "battlecraft.custom.mine.deleted" : "battlecraft.custom.mine.failed"), !gone);
        selectedFile = null;
        rebuild();
    }

    private void openFolder() {
        Util.getPlatform().openFile(PresetLibrary.folder().toFile());
    }

    private HeadingRow heading(String key) {
        return new HeadingRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, Component.translatable(key));
    }

    private ToggleRow toggle(String key, java.util.function.BooleanSupplier value,
                             java.util.function.Consumer<Boolean> apply) {
        ToggleRow row = new ToggleRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable(key), value, apply);
        row.hint(key + HINT_SUFFIX);
        return row;
    }

    @Override
    protected int rowsLeft() {
        return contentLeft() + PANEL_PAD + (listWidth() == 0 ? 0 : listWidth() + COLUMN_GAP);
    }

    @Override
    protected int rowsWidth() {
        return PANEL_WIDTH - PANEL_PAD * 2 - (listWidth() == 0 ? 0 : listWidth() + COLUMN_GAP);
    }

    @Override
    protected void renderBody(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (voiceKey() != null) talkKey.active = VoiceOptions.pushToTalk();
        preview.render(graphics, contentLeft() + PANEL_WIDTH + PREVIEW_GAP, panelTop(), panelHeight());
        if (tab != TAB_MINE) return;

        renderListWell(graphics);
        if (nameField != null) nameField.render(graphics);
    }

    @Override
    protected float enterSeconds() {
        return ENTER_SECONDS;
    }

    @Override
    protected void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        windows.render(graphics, this.width, this.height, mouseX, mouseY);
    }

    @Override
    protected boolean inputCaptured() {
        return windows.covering(cursorX(), cursorY());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (windows.mouseClicked(mouseX, mouseY)) return true;
        for (KeyRow row : listeningRows()) {
            if (row.takeButton(button)) return true;
        }

        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (windows.any()) setFocused(null);
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (windows.mouseDragged(mouseX, mouseY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (windows.mouseReleased()) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        for (KeyRow row : listeningRows()) {
            if (row.take(key, scanCode)) return true;
        }
        if (windows.keyPressed(key)) return true;
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char symbol, int modifiers) {
        if (windows.charTyped(symbol)) return true;
        return super.charTyped(symbol, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (leaving() || windows.covering(mouseX, mouseY)) return true;

        int step = amount > 0 ? -1 : 1;
        if (tab == TAB_MINE && localX(mouseX) < contentLeft() + PANEL_PAD + LIST_WIDTH) {
            return scrollList(step) || true;
        }
        return scrollRows(step) || true;
    }
}
