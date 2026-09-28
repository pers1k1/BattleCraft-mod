package com.persiki84.battlecraft.client.menu.desktop;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.Spring;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiRestFrame;
import com.persiki84.shared.client.ui.UiSiri;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiIcon;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

final class Spotlight {
    private static final float WIDTH = 250.0f;
    private static final float FIELD = 22.0f;
    private static final float ROW = 17.0f;
    private static final float GAP = 5.0f;
    private static final float TOP_SHARE = 0.26f;
    private static final float DROP = 10.0f;
    private static final float DEPTH = 400.0f;
    private static final float BORN_SCALE = 0.95f;
    private static final float STAGGER_SECONDS = 0.03f;
    private static final float ROW_SECONDS = 0.16f;
    private static final float ROW_SLIDE = 6.0f;
    private static final float TEXT_SCALE = 0.9f;
    private static final int MAX_LENGTH = 48;
    private static final float QUERY_INSET = 24.0f;
    private static final float QUERY_END = 12.0f;
    private static final float TITLE_INSET = 28.0f;
    private static final float TITLE_SHARE = 0.95f;
    private static final float KIND_INSET = 10.0f;
    private static final float KIND_GAP = 8.0f;
    private static final float KIND_SCALE = 0.75f;
    private static final String ELLIPSIS = "...";
    private static final float HIDDEN = 0.01f;
    private static final Component HINT = Component.translatable("battlecraft.desktop.search.hint");

    private final SpotlightSource source;
    private final Spring shown = new Spring(0.36f, 0.78f, 0.0f);
    private final Smooth pick = new Smooth(0.0f, 22.0f);
    private boolean open;
    private String query = "";
    private Component queryText = Component.empty();
    private List<SpotlightSource.Result> results = List.of();
    private List<Component> titles = List.of();
    private float caret;
    private int selected;
    private float listClock;
    private float left;
    private float top;
    private boolean hoverArmed;
    private int hoverX;
    private int hoverY;
    private int issueSeen;
    private Screen owner;

    Spotlight(SpotlightSource source) {
        this.source = source;
    }

    boolean open() {
        return open;
    }

    // WHY: подсветка выбора на новом открытии встаёт сразу на первую строку: иначе она выезжала
    // WHY: из-под строки прошлого поиска, пока панель ещё только появляется
    void show(Screen owner, String first) {
        this.owner = owner;
        source.load();
        boolean hidden = shown.get() <= HIDDEN;
        open = true;
        query = first;
        hoverArmed = false;
        refresh();
        if (hidden) pick.snap(selected);
        UiSound.press();
    }

    // WHY: запрос и строки остаются до следующего открытия: панель гаснет с тем, что в ней было,
    // WHY: а не подменяет набранное подсказкой в первом же кадре ухода
    void close() {
        open = false;
    }

    void prepare() {
        source.prepareAhead();
    }

    private void refresh() {
        issueSeen = source.issue();
        fill(source.find(query));
        queryText = Component.literal(query);
        caret = query.isEmpty() ? 0.0f : UiRender.width(Minecraft.getInstance().font, query) * TEXT_SCALE;
        selected = 0;
        listClock = 0.0f;
    }

    private void fill(List<SpotlightSource.Result> found) {
        results = found;
        titles = fitted(Minecraft.getInstance().font, found);
    }

    // WHY: миры и английские названия дочитываются в фоне уже после первой буквы: без досборки
    // WHY: свежий стол не находил их до следующего нажатия, а подсказки стояли без недавних миров.
    // WHY: Выбор держится за ту же строку, а не за номер, и не изменившийся список не переигрывается
    private void restock() {
        issueSeen = source.issue();
        List<SpotlightSource.Result> found = source.find(query);
        if (found.equals(results)) return;

        SpotlightSource.Result chosen = selected >= 0 && selected < results.size() ? results.get(selected) : null;
        fill(found);
        selected = Math.max(0, found.indexOf(chosen));
        listClock = 0.0f;
    }

    // WHY: длинное название строки кастомизации наезжало на подпись вида справа: обрезка считается
    // WHY: один раз на набор результатов, а не замером текста в каждом кадре
    private static List<Component> fitted(Font font, List<SpotlightSource.Result> found) {
        List<Component> made = new ArrayList<>(found.size());
        for (SpotlightSource.Result result : found) {
            float kind = UiRender.width(font, result.kind()) * KIND_SCALE;
            float room = (WIDTH - TITLE_INSET - KIND_INSET - KIND_GAP - kind) / (TEXT_SCALE * TITLE_SHARE);
            made.add(fit(font, result.title(), room));
        }
        return List.copyOf(made);
    }

    // WHY: ширина начала строки растёт с его длиной, поэтому место обрезки ищется делением пополам:
    // WHY: имя мира из чужого level.dat бывает в десятки тысяч знаков, и перебор с конца вешал ввод
    private static Component fit(Font font, Component title, float room) {
        if (UiRender.width(font, title) <= room) return title;

        String text = title.getString();
        int fits = 0;
        int over = text.length();
        while (over - fits > 1) {
            int middle = (fits + over) >>> 1;
            if (UiRender.width(font, text.substring(0, middle) + ELLIPSIS) <= room) {
                fits = middle;
            } else {
                over = middle;
            }
        }
        return Component.literal(text.substring(0, wholeCharacters(text, fits)).stripTrailing() + ELLIPSIS);
    }

    // WHY: знак вне базовой плоскости (эмодзи в имени мира) занимает две половинки, и обрезка
    // WHY: между ними оставляла бы перед многоточием битый глиф
    private static int wholeCharacters(String text, int end) {
        return end > 0 && Character.isHighSurrogate(text.charAt(end - 1)) ? end - 1 : end;
    }

    // WHY: подъём и рост панели заявлены ходом от места покоя: запрос и строки стоят на его сетке
    // WHY: и не идут ступенями по пикселю, пока панель выезжает
    void render(GuiGraphics graphics, Font font, float screenWidth, float screenHeight, int mouseX, int mouseY) {
        float delta = UiFrame.delta();
        if (open && source.issue() != issueSeen) restock();
        shown.to(open ? 1.0f : 0.0f, delta);
        UiSiri.render(graphics, screenWidth, screenHeight, open, 0.0f);
        float amount = UiAnim.clamp01(shown.get());
        if (amount <= HIDDEN) return;

        listClock += delta;
        left = (screenWidth - WIDTH) / 2.0f;
        top = screenHeight * TOP_SHARE;
        float scale = BORN_SCALE + (1.0f - BORN_SCALE) * shown.get();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, DEPTH);
        UiRestFrame.push(graphics, screenWidth / 2.0f, top, scale, scale, 0.0f, -(1.0f - shown.get()) * DROP);
        try {
            paintField(graphics, font, amount);
            paintResults(graphics, font, amount, mouseX, mouseY, delta);
        } finally {
            UiRestFrame.pop(graphics);
            graphics.pose().popPose();
        }
    }

    private void paintField(GuiGraphics graphics, Font font, float alpha) {
        UiGlass.above(graphics, left, top, WIDTH, FIELD);
        UiGlass.hush(graphics, left, top, WIDTH, FIELD, FIELD / 2.0f, alpha);
        UiGlass.window(graphics, left, top, WIDTH, FIELD, FIELD / 2.0f, alpha, 0.2f);
        UiIcon.draw(graphics, UiIcon.Kind.SEARCH, left + 14.0f, top + FIELD / 2.0f, 9.0f,
                UiTheme.alpha(UiAccent.text(), alpha));
        boolean empty = query.isEmpty();
        float textX = left + QUERY_INSET;
        float textY = top + (FIELD - 8.0f * TEXT_SCALE) / 2.0f;
        UiRender.textScaled(graphics, font, empty ? HINT : queryText, textX, textY, TEXT_SCALE,
                UiTheme.alpha(empty ? UiAccent.textFaint() : UiAccent.text(), alpha), false);
        paintCaret(graphics, textX + caret, textY, alpha);
    }

    // WHY: курсор ввода не мигает жёстко, а дышит, как в полях macOS
    private void paintCaret(GuiGraphics graphics, float x, float y, float alpha) {
        float breath = 0.5f + 0.5f * (float) Math.cos(System.currentTimeMillis() / 1000.0 * Math.PI * 2.0);
        UiRender.panel(graphics, x + 0.5f, y - 1.0f, 1.0f, 9.0f * TEXT_SCALE, 0.5f,
                UiTheme.alpha(UiAccent.color(), alpha * (0.35f + 0.65f * breath)));
    }

    private void paintResults(GuiGraphics graphics, Font font, float alpha, int mouseX, int mouseY, float delta) {
        if (results.isEmpty()) return;
        float listTop = top + FIELD + GAP;
        float height = results.size() * ROW + 8.0f;
        UiGlass.above(graphics, left, listTop, WIDTH, height);
        UiGlass.hush(graphics, left, listTop, WIDTH, height, 12.0f, alpha);
        UiGlass.window(graphics, left, listTop, WIDTH, height, 12.0f, alpha);
        UiGlass.layer(graphics);
        hoverPick(mouseX, mouseY, listTop);
        pick.to(selected, delta);
        UiGlass.window(graphics, left + 4.0f, listTop + 4.0f + pick.get() * ROW, WIDTH - 8.0f, ROW, ROW / 2.0f,
                alpha, 0.5f);
        for (int index = 0; index < results.size(); index++) {
            float arrived = UiAnim.easeOut(UiAnim.clamp01((listClock - index * STAGGER_SECONDS) / ROW_SECONDS));
            paintRow(graphics, font, results.get(index), titles.get(index), listTop + 4.0f + index * ROW,
                    alpha * arrived, arrived);
        }
    }

    private void paintRow(GuiGraphics graphics, Font font, SpotlightSource.Result result, Component title, float y,
                          float alpha, float arrived) {
        UiRestFrame.shift(graphics, (1.0f - arrived) * ROW_SLIDE, 0.0f);
        try {
            UiIcon.draw(graphics, result.glyph(), left + 16.0f, y + ROW / 2.0f, 9.0f,
                    UiTheme.alpha(UiAccent.text(), alpha));
            UiRender.textScaled(graphics, font, title, left + TITLE_INSET, y + 5.0f, TEXT_SCALE * TITLE_SHARE,
                    UiTheme.alpha(UiAccent.text(), alpha), false);
        } finally {
            UiRestFrame.pop(graphics);
        }
        UiRender.textRight(graphics, font, result.kind(), left + WIDTH - KIND_INSET, y + 5.5f, KIND_SCALE,
                UiTheme.alpha(UiAccent.textDim(), alpha), false);
    }

    // WHY: наведение выбирает строку, только когда мышь сдвинулась: курсор, стоящий над списком,
    // WHY: иначе каждый кадр отбирал выбор у стрелок, и Enter открывал строку под мышью
    private void hoverPick(int mouseX, int mouseY, float listTop) {
        boolean moved = hoverArmed && (mouseX != hoverX || mouseY != hoverY);
        hoverArmed = true;
        hoverX = mouseX;
        hoverY = mouseY;
        if (!moved || mouseX < left || mouseX > left + WIDTH) return;

        int row = (int) Math.floor((mouseY - listTop - 4.0f) / ROW);
        if (row >= 0 && row < results.size()) selected = row;
    }

    boolean contains(double mouseX, double mouseY) {
        float bottom = top + FIELD + GAP + results.size() * ROW + 8.0f;
        return open && mouseX >= left && mouseX <= left + WIDTH && mouseY >= top && mouseY <= bottom;
    }

    boolean click(double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY)) return false;
        float listTop = top + FIELD + GAP;
        int row = (int) Math.floor((mouseY - listTop - 4.0f) / ROW);
        if (row >= 0 && row < results.size()) run(row);
        return true;
    }

    boolean typed(char symbol) {
        if (!open) return false;
        if (!SharedConstants.isAllowedChatCharacter(symbol) || strayHalf(symbol)) return true;
        if (!fits(query + symbol)) {
            dropOpenHalf();
            return true;
        }
        query += symbol;
        if (Character.isHighSurrogate(symbol)) return true;

        refresh();
        UiSiri.pulse();
        return true;
    }

    // WHY: знак вне базовой плоскости приходит двумя вызовами, по половинке пары. Упёршийся в предел
    // WHY: ввод принимал первую и отвергал вторую, и в поле оставалась одинокая половинка битым глифом
    private boolean strayHalf(char symbol) {
        return Character.isLowSurrogate(symbol) && !endsHalfway();
    }

    private boolean endsHalfway() {
        return !query.isEmpty() && Character.isHighSurrogate(query.charAt(query.length() - 1));
    }

    private void dropOpenHalf() {
        if (endsHalfway()) query = query.substring(0, query.length() - 1);
    }

    // WHY: запрос ограничен и шириной поля, а не только числом знаков: широкие буквы уходили за
    // WHY: стекло. Знак параграфа ваниль в ввод не пускает: он перекрашивал бы набранное кодом
    private static boolean fits(String next) {
        return next.length() <= MAX_LENGTH
                && UiRender.width(Minecraft.getInstance().font, next) * TEXT_SCALE <= WIDTH - QUERY_INSET - QUERY_END;
    }

    boolean key(int key) {
        if (!open) return false;
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> close();
            case GLFW.GLFW_KEY_BACKSPACE -> erase();
            case GLFW.GLFW_KEY_DOWN -> selected = Math.max(0, Math.min(results.size() - 1, selected + 1));
            case GLFW.GLFW_KEY_UP -> selected = Math.max(0, selected - 1);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> run(selected);
            default -> {
                return true;
            }
        }
        return true;
    }

    private void erase() {
        if (query.isEmpty()) return;
        query = query.substring(0, query.offsetByCodePoints(query.length(), -1));
        refresh();
        UiSiri.pulse();
    }

    private void run(int index) {
        if (index < 0 || index >= results.size()) return;
        SpotlightSource.Result result = results.get(index);
        close();
        UiSound.press();
        result.run().accept(owner);
    }

    // WHY: уход со стола обрывает отрисовку посреди угасания панели: без сброса возврат на стол
    // WHY: начинался с призрака поиска и свечения по кромке. Зовётся из removed стола, поэтому
    // WHY: покрывает и строку поиска, и уход через док, пока панель ещё гаснет
    void dismiss() {
        close();
        shown.snap(0.0f);
        UiSiri.hide();
    }
}
