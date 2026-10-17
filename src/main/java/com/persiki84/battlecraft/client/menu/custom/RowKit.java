package com.persiki84.battlecraft.client.menu.custom;

import com.mojang.blaze3d.platform.InputConstants;
import com.persiki84.battlecraft.client.custom.Customization;
import com.persiki84.battlecraft.client.custom.GlassKey;
import com.persiki84.battlecraft.client.custom.HudSlot;
import com.persiki84.battlecraft.client.custom.PaletteKey;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.ColorRow;
import com.persiki84.shared.client.menu.HeadingRow;
import com.persiki84.shared.client.menu.KeyRow;
import com.persiki84.shared.client.menu.MenuHint;
import com.persiki84.shared.client.menu.MenuRow;
import com.persiki84.shared.client.menu.PickRow;
import com.persiki84.shared.client.menu.NumberRow;
import com.persiki84.shared.client.menu.ToggleRow;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

// WHY: все строки кастомизации собираются одной мастерской: ширина колонки, подсказка по ключу
// WHY: «<ключ>.hint» и учёт строк, которые экран потом гасит или слушает, живут в одном месте
final class RowKit {
    static final int ROW_HEIGHT = 22;
    static final String HINT = MenuHint.SUFFIX;
    private static final Component PERCENT = Component.literal(", %");

    private final CustomHost host;
    private final int width;
    private final Map<MenuRow, GlassKey> glassRows = new HashMap<>();
    private final Map<AbstractWidget, HudSlot> slotRows = new HashMap<>();
    private final List<KeyRow> bindingRows = new ArrayList<>();
    private ToggleRow hudAccentRow;
    private KeyRow talkKey;

    RowKit(CustomHost host, int width) {
        this.host = host;
        this.width = width;
    }

    CustomHost host() {
        return host;
    }

    Map<MenuRow, GlassKey> glassRows() {
        return glassRows;
    }

    Map<AbstractWidget, HudSlot> slotRows() {
        return slotRows;
    }

    List<KeyRow> bindingRows() {
        return bindingRows;
    }

    ToggleRow hudAccentRow() {
        return hudAccentRow;
    }

    KeyRow talkKey() {
        return talkKey;
    }

    void hudAccent(ToggleRow row) {
        hudAccentRow = row;
    }

    void talk(KeyRow row) {
        talkKey = row;
        listen(row);
    }

    void listen(KeyRow row) {
        bindingRows.add(row);
    }

    void slot(AbstractWidget row, HudSlot slot) {
        slotRows.put(row, slot);
    }

    int width() {
        return width;
    }

    HeadingRow heading(String key) {
        return new HeadingRow(0, 0, width, ROW_HEIGHT, Component.translatable(key));
    }

    ToggleRow toggle(String key, BooleanSupplier value, Consumer<Boolean> apply) {
        ToggleRow row = new ToggleRow(0, 0, width, ROW_HEIGHT, Component.translatable(key), value, apply);
        row.hint(key + HINT);
        return row;
    }

    // WHY: выключатель, от которого зависят соседние строки (блокировки, причины), после правки
    // WHY: просит экран переложить колонку: иначе заблокированная строка выглядела бы живой
    ToggleRow saved(String key, BooleanSupplier value, Consumer<Boolean> apply) {
        return toggle(key, value, chosen -> {
            apply.accept(chosen);
            Customization.save();
            host.refresh();
        });
    }

    // WHY: ручки стекла правятся числом, а не ползунком: владелец ставит точное значение с
    // WHY: клавиатуры или стрелками, а целое в строке это значение в долях шага ключа
    NumberRow glass(GlassKey key) {
        int divisor = divisorFor(key.step());
        NumberRow row = number(Component.translatable(key.translationKey()),
                () -> Math.round(Customization.glass(key) * divisor), raw -> {
                    Customization.glass(key, raw / (float) divisor);
                    Customization.save();
                }, key.minimum(), key.maximum(), key.step(), divisor);
        glassRows.put(row, key);
        row.hint(key.hintKey());
        return row;
    }

    private static int divisorFor(float step) {
        if (step >= 1.0f) return 1;
        if (step >= 0.05f) return 100;
        return 1000;
    }

    // WHY: доля показывается процентами, а знак стоит в подписи: строка числа хранит целое, и
    // WHY: «125» без единицы рядом читалось бы как абсолютная величина
    NumberRow percent(String key, Supplier<Float> value, Consumer<Float> apply, float least, float most,
                      float step) {
        NumberRow row = number(Component.translatable(key).append(PERCENT), () -> Math.round(value.get() * 100.0f),
                raw -> apply.accept(raw / 100.0f), least * 100.0f, most * 100.0f, step * 100.0f, 1);
        row.hint(key + HINT);
        return row;
    }

    NumberRow plain(String key, Supplier<Float> value, Consumer<Float> apply, float least, float most,
                    float step) {
        int divisor = divisorFor(step) == 1 ? 1 : Math.round(1.0f / step);
        NumberRow row = number(Component.translatable(key), () -> Math.round(value.get() * divisor),
                raw -> apply.accept(raw / (float) divisor), least, most, step, divisor);
        row.hint(key + HINT);
        return row;
    }

    private NumberRow number(Component label, IntSupplier value, IntConsumer apply, float least, float most,
                             float step, int divisor) {
        int scaledStep = Math.max(1, Math.round(step * divisor));
        NumberRow row = new NumberRow(0, 0, width, ROW_HEIGHT, label, value, apply, Math.round(least * divisor),
                Math.round(most * divisor), scaledStep);
        return divisor == 1 ? row : row.scaledBy(divisor).trimmed();
    }

    ColorRow color(PaletteKey key) {
        ColorRow row = new ColorRow(0, 0, width, ROW_HEIGHT, Component.translatable(key.translationKey()),
                () -> Customization.color(key), () -> Customization.colorTouched(key),
                () -> host.openPalette(key));
        row.hint(key.hintKey());
        return row;
    }

    PickRow pick(String key, List<Component> options, IntSupplier index, IntConsumer apply) {
        PickRow row = new PickRow(0, 0, width, ROW_HEIGHT, Component.translatable(key), options, index, apply);
        row.hint(key + HINT);
        return row;
    }

    ActionRow action(String key, String value, Runnable run) {
        ActionRow row = new ActionRow(0, 0, width, ROW_HEIGHT, Component.translatable(key),
                () -> Component.translatable(value), run);
        row.hint(key + HINT);
        return row;
    }

    ActionRow action(String key, Supplier<Component> value, Runnable run) {
        ActionRow row = new ActionRow(0, 0, width, ROW_HEIGHT, Component.translatable(key), value, run);
        row.hint(key + HINT);
        return row;
    }

    KeyRow key(String key, Supplier<Component> name, Consumer<InputConstants.Key> apply) {
        KeyRow row = new KeyRow(0, 0, width, ROW_HEIGHT, Component.translatable(key), name, apply);
        row.hint(key + HINT);
        listen(row);
        return row;
    }
}
