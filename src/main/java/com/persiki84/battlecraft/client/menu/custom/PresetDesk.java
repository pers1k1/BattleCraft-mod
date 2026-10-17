package com.persiki84.battlecraft.client.menu.custom;

import com.persiki84.battlecraft.client.custom.CustomPreset;
import com.persiki84.battlecraft.client.custom.Customization;
import com.persiki84.battlecraft.client.custom.PresetLibrary;
import com.persiki84.battlecraft.client.custom.UserPreset;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.studio.StudioMenu;
import com.persiki84.shared.client.menu.studio.StudioStack;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// WHY: пресеты выбираются глазами: встроенные, свои и «новый» лежат плитками с миниатюрой в центре,
// WHY: справа то, что с выбранным можно сделать. Свои пресеты и встроенные больше не живут на разных
// WHY: вкладках: сравнивать их удобнее рядом
final class PresetDesk {
    static final int ART_HEIGHT = 98;
    private static final long DOUBLE_CLICK_MS = 400L;
    private static final float GLYPH_HEIGHT = 62.0f;
    private static final float NAME_SCALE = 0.95f;
    private static final float HINT_SCALE = 0.72f;

    private final RowKit kit;
    private final PresetGallery gallery = new PresetGallery();
    private final PresetRows rows;
    private List<PresetCard> cards = List.of();
    private String selected;
    private String typedNew = "";
    private String typedRename = "";
    private String lastPressed;
    private long lastPressedAt;
    private int version;

    PresetDesk(RowKit kit) {
        this.kit = kit;
        this.rows = new PresetRows(kit, this, typed -> typedNew = typed, typed -> typedRename = typed);
    }

    int version() {
        return version;
    }

    void reload() {
        PresetLibrary.refresh();
        List<PresetCard> built = new ArrayList<>();
        for (CustomPreset preset : CustomPreset.values()) {
            built.add(PresetCard.of(preset));
        }
        for (UserPreset preset : PresetLibrary.all()) {
            built.add(PresetCard.of(preset));
        }
        built.add(PresetCard.blank());
        cards = built;
        gallery.show(cards);
        if (card(selected) == null) select(PresetCard.of(Customization.preset()).key());
        version++;
    }

    void opened() {
        openedOn(null);
    }

    // WHY: поиск рабочего стола приводит к нужной плитке: она выбирается до того, как галерея
    // WHY: прокрутится к выбранной, иначе витрина встала бы на текущем пресете
    void openedOn(String wanted) {
        reload();
        if (card(wanted) != null) select(wanted);
        gallery.restart();
        gallery.reveal(selected);
    }

    ActionRow resetRow() {
        return rows.resetAll;
    }

    PresetCard card(String key) {
        if (key == null) return null;
        for (PresetCard card : cards) {
            if (card.key().equals(key)) return card;
        }
        return null;
    }

    PresetCard chosen() {
        return card(selected);
    }

    String selected() {
        return selected;
    }

    void select(String key) {
        if (key == null || key.equals(selected)) return;
        selected = key;
        PresetCard card = card(key);
        if (card != null && card.mine() != null) rows.rename.box().setValue(card.mine().name());
        version++;
    }

    void buildInspector(StudioStack stack, int x) {
        PresetCard card = chosen();
        if (card == null) return;
        if (card.fresh()) {
            rows.fresh(stack, x);
        } else if (card.mine() != null) {
            rows.mine(stack, x);
        } else {
            rows.builtin(stack, x, card.current());
        }
    }

    void render(GuiGraphics graphics, float left, float top, float width, float height, int mouseX, int mouseY) {
        gallery.place(left, top, width, height);
        gallery.render(graphics, selected, mouseX, mouseY);
    }

    // WHY: справа выбранный пресет показан крупной миниатюрой: сравнить с соседней плиткой проще, когда
    // WHY: та же картинка видна в размере, где заметны радуга кромки и линза
    void renderArt(GuiGraphics graphics, Font font, float left, float top, float width, float appear) {
        PresetCard card = chosen();
        if (card == null) return;
        PresetGlyph.paint(graphics, card, left, top, width, GLYPH_HEIGHT, appear);
        float center = left + width / 2.0f;
        UiRender.textTrackedFit(graphics, font, card.name(), center, top + GLYPH_HEIGHT + 4.0f, 14.0f, width,
                NAME_SCALE, 0.0f, UiTheme.alpha(UiAccent.text(), appear), false);
        UiRender.textTrackedFit(graphics, font, card.hint(), center, top + GLYPH_HEIGHT + 19.0f, 10.0f, width,
                HINT_SCALE, 0.0f, UiTheme.alpha(UiAccent.textDim(), appear), false);
    }

    boolean over(double mouseX, double mouseY) {
        return gallery.over(mouseX, mouseY);
    }

    void scroll(double amount) {
        gallery.scrollBy(amount > 0 ? -1 : 1);
    }

    // WHY: второй щелчок по той же плитке применяет её сразу: смотрят глазами и сразу пробуют
    boolean press(double mouseX, double mouseY) {
        PresetCard card = gallery.at(mouseX, mouseY);
        if (card == null) return false;
        long now = System.currentTimeMillis();
        boolean doubled = card.key().equals(lastPressed) && now - lastPressedAt < DOUBLE_CLICK_MS;
        lastPressed = doubled ? null : card.key();
        lastPressedAt = now;
        select(card.key());
        if (doubled) applySelected();
        kit.host().refresh();
        return true;
    }

    List<StudioMenu.Action> actions(double mouseX, double mouseY) {
        PresetCard card = gallery.at(mouseX, mouseY);
        if (card == null) return TabResets.actions(CustomTab.PRESETS, kit);
        select(card.key());
        kit.host().refresh();
        if (card.fresh()) return List.of(StudioMenu.Action.of("battlecraft.custom.mine.save", this::saveFresh),
                StudioMenu.Action.of("battlecraft.custom.mine.folder", PresetDesk::openFolder));
        if (card.mine() == null) return List.of(
                StudioMenu.Action.of("battlecraft.custom.mine.apply", this::applySelected).when(!card.current()));
        return List.of(StudioMenu.Action.of("battlecraft.custom.mine.apply", this::applySelected),
                StudioMenu.Action.of("battlecraft.custom.mine.rename", () -> kit.host().focus(rows.rename)),
                StudioMenu.Action.of("battlecraft.custom.mine.duplicate", this::duplicateSelected),
                StudioMenu.Action.careful("battlecraft.custom.mine.delete", this::deleteNow));
    }

    void applySelected() {
        PresetCard card = chosen();
        if (card == null) return;
        if (card.fresh()) {
            saveFresh();
            return;
        }
        if (card.mine() != null) {
            PresetLibrary.apply(card.mine());
            MenuFeedback.show(Component.translatable("battlecraft.custom.mine.applied",
                    Component.literal(card.mine().name())), false);
        } else {
            Customization.preset(card.builtin());
            Customization.save();
        }
        version++;
        kit.host().refresh();
    }

    void resetAll() {
        TabResets.done(kit, Customization::resetAll);
        select(PresetCard.of(Customization.preset()).key());
    }

    void saveFresh() {
        String name = typedNew.trim();
        PresetLibrary.Result result = PresetLibrary.save(name, rows.chosen);
        MenuFeedback.show(Component.translatable(result.translationKey()), result.alerting());
        if (result != PresetLibrary.Result.SAVED) return;
        rows.newName.box().setValue("");
        reload();
        selectNamed(name);
        kit.host().refresh();
    }

    void overwriteSelected() {
        PresetCard card = chosen();
        if (card == null || card.mine() == null) return;
        PresetLibrary.Result result = PresetLibrary.save(card.mine().name(), card.mine().sections());
        MenuFeedback.show(Component.translatable(result == PresetLibrary.Result.SAVED
                ? "battlecraft.custom.mine.overwritten" : result.translationKey()), result.alerting());
        reload();
        kit.host().refresh();
    }

    void renameSelected() {
        PresetCard card = chosen();
        if (card == null || card.mine() == null) return;
        PresetFiles.Result result = PresetFiles.rename(card.mine(), typedRename);
        boolean done = result == PresetFiles.Result.DONE;
        MenuFeedback.show(Component.translatable("battlecraft.custom.mine.rename."
                + result.name().toLowerCase(Locale.ROOT)), !done);
        if (!done) return;
        reload();
        selectNamed(typedRename.trim());
        kit.host().refresh();
    }

    void duplicateSelected() {
        PresetCard card = chosen();
        if (card == null || card.mine() == null) return;
        String name = Component.translatable("battlecraft.custom.mine.copy_of", card.mine().name()).getString();
        Path made = PresetFiles.duplicate(card.mine(), name);
        MenuFeedback.show(Component.translatable(made == null
                ? "battlecraft.custom.mine.failed" : "battlecraft.custom.mine.duplicated"), made == null);
        reload();
        if (made != null) select("u:" + made);
        kit.host().refresh();
    }

    // WHY: Enter в поле имени делает то, ради чего поле открыто: в новом пресете сохраняет, в своём
    // WHY: переименовывает. Иначе после набора приходилось бы тянуться к строке ниже
    boolean submit(Object focused) {
        if (focused == rows.newName) {
            saveFresh();
            return true;
        }
        if (focused == rows.rename) {
            renameSelected();
            return true;
        }
        return false;
    }

    boolean deletable() {
        PresetCard card = chosen();
        return card != null && card.mine() != null;
    }

    String deleteKey() {
        return "preset.delete:" + selected;
    }

    // WHY: удаление файла необратимо, поэтому строка просит второе нажатие, а пункт меню по правой
    // WHY: кнопке спрашивает своё подтверждение сам
    void deleteSelected() {
        if (kit.host().sure(deleteKey())) {
            deleteNow();
        } else {
            MenuFeedback.show(Component.translatable("studio.menu.sure"), true);
        }
    }

    private void deleteNow() {
        PresetCard card = chosen();
        if (card == null || card.mine() == null) return;
        int index = cards.indexOf(card);
        boolean gone = PresetLibrary.delete(card.mine());
        MenuFeedback.show(Component.translatable(gone
                ? "battlecraft.custom.mine.deleted" : "battlecraft.custom.mine.failed"), !gone);
        reload();
        selectNeighbour(index);
        kit.host().refresh();
    }

    // WHY: после удаления выбор встаёт на соседа, а не в начало: так снимают несколько подряд
    private void selectNeighbour(int index) {
        if (cards.isEmpty()) return;
        int at = Math.max(0, Math.min(cards.size() - 1, index - 1));
        selected = null;
        select(cards.get(at).key());
    }

    private void selectNamed(String name) {
        for (PresetCard card : cards) {
            if (card.mine() != null && card.mine().name().equals(name)) {
                select(card.key());
                gallery.reveal(card.key());
                return;
            }
        }
    }

    static void openFolder() {
        Util.getPlatform().openFile(PresetLibrary.folder().toFile());
    }
}
