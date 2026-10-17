package com.persiki84.sellmod.client;

import com.persiki84.shared.client.menu.FieldRow;
import com.persiki84.shared.client.menu.GlassScreen;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.SearchField;
import com.persiki84.shared.client.menu.TickerLine;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiTitle;
import com.persiki84.shared.client.ui.UiVeil;
import com.persiki84.shared.economy.Money;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.scores.PlayerTeam;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

// WHY: перевод собран в одно окно, где видно всё сразу: кому (лица игроков в сети, тиммейты первыми),
// WHY: сколько (число с клавиатуры и быстрые кнопки), что останется и что ответил сервер. Отправка
// WHY: идёт вторым нажатием: деньги назад не забрать, а случайный щелчок по кнопке ничего не стоит
public final class TransferScreen extends GlassScreen {
    private static final int LIST_WIDTH = 222;
    private static final int SIDE_WIDTH = 198;
    private static final int GAP = 8;
    private static final int HEADER = 76;
    private static final int PANEL_LIMIT = 252;
    private static final int TITLE_TOP = 12;
    private static final int HEADER_ROW = 20;
    private static final int BALANCE_WIDTH = 190;
    private static final int ROW = 22;
    private static final int CONTROL = 16;
    private static final int SEND_HEIGHT = 22;
    private static final int CARD = 60;
    private static final int LEFT_LINE = 14;
    private static final int AMOUNT_LIMIT = 13;
    private static final int REFRESH_TICKS = 20;
    private static final int QUICK_SMALL = 10;
    private static final int QUICK_LARGE = 100;
    private static final long ARM_MS = 3000L;
    private static final long WAIT_MS = 4000L;
    private static final float RADIUS = 9.0f;
    private static final float FIT_MARGIN = 10.0f;
    private static final float FACE = 26.0f;
    private static final float FACE_FRAME = 2.0f;
    private static final float CARD_SPEED = 7.0f;
    private static final float CARD_BLUR = 4.0f;
    private static final float CARD_SLIDE = 6.0f;
    private static final float LINE_SCALE = 0.72f;
    private static final float NAME_SCALE = 0.95f;
    private static final float BALANCE_ICON = 12.0f;
    private static final int FACE_SHADE = 0x55000000;
    private static final int ANSWER_LINES = 3;
    private static final float ANSWER_MS = 300.0f;
    private static final float ANSWER_RISE = 3.0f;

    private enum Phase { TARGET, AMOUNT, SHORT, LIMIT, GONE, READY, ARMED, WAITING }

    private final Screen parent;
    private final TransferGrid grid = new TransferGrid();
    private final SearchField search;
    private final FieldRow amountRow;
    private final UiButton plusSmall;
    private final UiButton plusLarge;
    private final UiButton everything;
    private final UiButton send;
    private final Smooth cardShown = new Smooth(1.0f, CARD_SPEED);
    private UUID chosen;
    private UUID cardFor;
    private long amount = -1L;
    private long armedAt;
    private long sentAt;
    private long answeredAt;
    private long shownState = Long.MIN_VALUE;
    private int shownBalance = Integer.MIN_VALUE;
    private Component balanceLabel = Component.empty();
    private final TickerLine balanceLine = new TickerLine(0.5f);
    private Component leftLabel = Component.empty();
    private boolean leftShort;
    private int ticks;
    private Component cardName = Component.empty();
    private Component cardTeam = Component.empty();
    private long answerBuiltFor;
    private List<Component> answerLines = List.of();
    private final Map<String, List<Component>> wrapped = new HashMap<>();

    private TransferScreen(Screen parent) {
        super(Component.translatable("sellmod.transfer.title"));
        this.parent = parent;
        this.search = new SearchField(Component.translatable("sellmod.transfer.search"), grid::search);
        this.amountRow = new FieldRow(0, 0, 10, ROW, Component.translatable("sellmod.transfer.amount"),
                Component.translatable("sellmod.transfer.amount.placeholder"), "", AMOUNT_LIMIT, this::typed);
        this.amountRow.box().setFilter(TransferScreen::digitsOnly);
        this.amountRow.hint("sellmod.transfer.amount.hint");
        this.plusSmall = quick(Component.literal("+" + QUICK_SMALL), pressed -> addAmount(QUICK_SMALL));
        this.plusLarge = quick(Component.literal("+" + QUICK_LARGE), pressed -> addAmount(QUICK_LARGE));
        this.everything = quick(Component.translatable("sellmod.transfer.all"), pressed -> setAmount(balance()))
                .hint("sellmod.transfer.all.hint");
        this.send = new UiButton(0, 0, 10, SEND_HEIGHT, Component.empty(), pressed -> pressSend())
                .hint("sellmod.transfer.send.hint");
    }

    public static void open(Screen parent) {
        Minecraft.getInstance().setScreen(new TransferScreen(parent));
    }

    private static UiButton quick(Component label, Consumer<UiButton> action) {
        return new UiButton(0, 0, 10, CONTROL, label, action);
    }

    private static boolean digitsOnly(String text) {
        for (int index = 0; index < text.length(); index++) {
            char symbol = text.charAt(index);
            if ((symbol < '0' || symbol > '9') && symbol != ' ') return false;
        }
        return true;
    }

    private int contentWidth() {
        return LIST_WIDTH + GAP + SIDE_WIDTH;
    }

    private int contentLeft() {
        return (this.width - contentWidth()) / 2;
    }

    private int panelHeight() {
        return Math.max(sideNeed(), Math.min(this.height - HEADER - GAP * 2, PANEL_LIMIT));
    }

    private int sideNeed() {
        float answer = UiMetrics.GAP_WIDE + ANSWER_LINES * (this.font.lineHeight * LINE_SCALE + UiMetrics.GAP_TIGHT);
        return sendOffset() + SEND_HEIGHT + (int) Math.ceil(answer + UiMetrics.PAD);
    }

    private int contentTop() {
        return Math.max(HEADER, (this.height - panelHeight()) / 2);
    }

    private int sideLeft() {
        return contentLeft() + LIST_WIDTH + GAP;
    }

    private int sideRowsLeft() {
        return sideLeft() + (int) UiMetrics.PAD_WIDE;
    }

    private int sideRowsWidth() {
        return SIDE_WIDTH - (int) UiMetrics.PAD_WIDE * 2;
    }

    private static int amountOffset() {
        return (int) UiMetrics.PAD_WIDE + CARD + (int) UiMetrics.GAP;
    }

    private static int quickOffset() {
        return amountOffset() + ROW + (int) UiMetrics.GAP;
    }

    private static int leftLineOffset() {
        return quickOffset() + CONTROL + (int) UiMetrics.GAP_WIDE;
    }

    private static int sendOffset() {
        return leftLineOffset() + LEFT_LINE;
    }

    private int amountTop() {
        return contentTop() + amountOffset();
    }

    private int quickTop() {
        return contentTop() + quickOffset();
    }

    private int leftLineTop() {
        return contentTop() + leftLineOffset();
    }

    private int sendTop() {
        return contentTop() + sendOffset();
    }

    @Override
    protected float revealTop() {
        return contentTop();
    }

    @Override
    protected float revealSpan() {
        return panelHeight();
    }

    @Override
    protected float contentScale() {
        float needed = contentWidth() + FIT_MARGIN * 2.0f;
        float widthFit = needed <= this.width ? 1.0f : this.width / needed;
        return Math.min(widthFit, heightFit());
    }

    // WHY: масштаб GlassScreen идёт от середины экрана, поэтому низ панели b вписывается в высоту h
    // WHY: множителем (h/2) / (b - h/2), а не h / b: верх при таком сжатии остаётся на экране сам
    private float heightFit() {
        float bottom = contentTop() + panelHeight() + GAP;
        float half = this.height / 2.0f;
        return bottom <= this.height ? 1.0f : half / (bottom - half);
    }

    @Override
    protected void init() {
        boolean typing = getFocused() == amountRow;
        wrapped.clear();
        answerBuiltFor = 0L;
        clearWidgets();
        placeSearch();
        placeAmount();
        placeQuick();
        send.setPosition(sideRowsLeft(), sendTop());
        send.setWidth(sideRowsWidth());
        addRenderableWidget(send);
        if (ticks == 0) {
            grid.refresh();
            setInitialFocus(search.box());
        }
        if (typing) setFocused(amountRow);
        refreshControls();
    }

    private void placeSearch() {
        search.place(contentLeft() + (int) UiMetrics.PAD, contentTop() + (int) UiMetrics.PAD,
                LIST_WIDTH - (int) UiMetrics.PAD * 2);
        addWidget(search.box());
        float gridTop = contentTop() + UiMetrics.PAD + SearchField.HEIGHT + UiMetrics.GAP;
        grid.place(contentLeft() + UiMetrics.PAD, gridTop, LIST_WIDTH - UiMetrics.PAD * 2.0f,
                contentTop() + panelHeight() - UiMetrics.PAD - gridTop);
    }

    private void placeAmount() {
        amountRow.setX(sideRowsLeft());
        amountRow.setY(amountTop());
        amountRow.setWidth(sideRowsWidth());
        addRenderableWidget(amountRow);
    }

    private void placeQuick() {
        int third = (sideRowsWidth() - (int) UiMetrics.GAP * 2) / 3;
        int x = sideRowsLeft();
        for (UiButton button : List.of(plusSmall, plusLarge, everything)) {
            button.setPosition(x, quickTop());
            button.setWidth(third);
            addRenderableWidget(button);
            x += third + (int) UiMetrics.GAP;
        }
        everything.setWidth(sideRowsLeft() + sideRowsWidth() - everything.getX());
    }

    @Override
    public void tick() {
        ticks++;
        if (ticks % REFRESH_TICKS == 0) grid.refresh();
        settleAnswer();
        refreshControls();
    }

    private int balance() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? 0 : ClientSellData.balance(minecraft.player);
    }

    private void typed(String text) {
        amount = MoneyText.parse(text);
        armedAt = 0L;
        refreshControls();
    }

    private void addAmount(long step) {
        setAmount(Math.max(0L, amount) + step);
    }

    private void setAmount(long value) {
        long clamped = Math.max(0L, Math.min(Money.LIMIT, value));
        amountRow.box().setValue(clamped <= 0L ? "" : MoneyText.digits(clamped));
        amountRow.box().moveCursorToEnd();
    }

    private Phase phase() {
        if (sentAt > 0L && System.currentTimeMillis() - sentAt < WAIT_MS) return Phase.WAITING;
        if (chosen == null) return Phase.TARGET;
        if (grid.person(chosen) == null) return Phase.GONE;
        if (amount <= 0L) return Phase.AMOUNT;
        if (amount > Money.LIMIT) return Phase.LIMIT;
        if (amount > balance()) return Phase.SHORT;
        return armed() ? Phase.ARMED : Phase.READY;
    }

    private boolean armed() {
        return armedAt > 0L && System.currentTimeMillis() - armedAt < ARM_MS;
    }

    // WHY: подпись кнопки зависит от суммы, остатка и взвода, а UiButton играет переход на каждую
    // WHY: смену подписи: она пересобирается только при смене состояния, а не каждый тик
    private void refreshControls() {
        Phase phase = phase();
        int held = balance();
        long state = ((phase.ordinal() * 31L + Math.max(-1L, amount)) * 31L + held) * 31L
                + (chosen == null ? 0 : chosen.hashCode());
        if (state == shownState) return;

        shownState = state;
        send.setMessage(sendLabel(phase));
        send.active = phase == Phase.READY || phase == Phase.ARMED;
        everything.active = held > 0;
        relabelLeft(phase, held);
    }

    private Component sendLabel(Phase phase) {
        return switch (phase) {
            case TARGET -> Component.translatable("sellmod.transfer.need_target");
            case AMOUNT -> Component.translatable("sellmod.transfer.need_amount");
            case SHORT -> Component.translatable("sellmod.transfer.short", ClientSellData.amountText(amount - balance()));
            case LIMIT -> Component.translatable("sellmod.transfer.limit", MoneyText.digits(Money.LIMIT));
            case GONE -> Component.translatable("sellmod.transfer.gone");
            case ARMED -> Component.translatable("sellmod.transfer.sure");
            case WAITING -> Component.translatable("sellmod.transfer.waiting");
            case READY -> Component.translatable("sellmod.transfer.send", ClientSellData.amountText(amount));
        };
    }

    private void relabelLeft(Phase phase, int held) {
        leftShort = phase == Phase.SHORT;
        if (amount > 0L && amount <= held) {
            leftLabel = Component.translatable("sellmod.transfer.left", ClientSellData.amountText(held - amount));
        } else if (leftShort) {
            leftLabel = Component.translatable("sellmod.transfer.left.short", ClientSellData.amountText(held));
        } else {
            leftLabel = Component.translatable("sellmod.transfer.left", ClientSellData.amountText(held));
        }
    }

    private void pressSend() {
        Phase phase = phase();
        if (phase == Phase.READY) {
            armedAt = System.currentTimeMillis();
            refreshControls();
            return;
        }
        if (phase != Phase.ARMED || !MoneyRequests.pay(chosen, amount)) return;

        armedAt = 0L;
        sentAt = System.currentTimeMillis();
        refreshControls();
    }

    // WHY: удачный перевод очищает сумму, а получатель остаётся выбранным: второй перевод тому же
    // WHY: игроку не требует искать его заново. Отказ оставляет сумму, чтобы её можно было поправить
    private void settleAnswer() {
        Component answer = PayAnswers.since(sentAt);
        if (sentAt <= 0L || answer == null || PayAnswers.arrivedAt() == answeredAt) return;

        answeredAt = PayAnswers.arrivedAt();
        sentAt = 0L;
        if (!PayAnswers.refused()) setAmount(0L);
    }

    private void choose(UUID id) {
        if (id.equals(chosen)) return;
        chosen = id;
        armedAt = 0L;
        grid.choose(id);
        refreshControls();
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiTitle.render(graphics, this.font, getTitle(), this.width / 2.0f, TITLE_TOP, 1.0f, 0.0f, UiAccent.text());
        renderBalancePlate(graphics);
        int top = contentTop();
        int height = panelHeight();
        UiGlass.window(graphics, contentLeft(), top, LIST_WIDTH, height, RADIUS, 1.0f);
        UiGlass.window(graphics, sideLeft(), top, SIDE_WIDTH, height, RADIUS, 1.0f);
        UiGlass.layer(graphics);

        search.render(graphics, mouseX, mouseY, partialTick);
        renderList(graphics, mouseX, mouseY);
        renderCard(graphics);
        renderWidgets(graphics, mouseX, mouseY, partialTick);
        renderLeftLine(graphics);
        renderAnswer(graphics);
    }

    private void renderBalancePlate(GuiGraphics graphics) {
        int held = balance();
        if (held != shownBalance) {
            shownBalance = held;
            balanceLabel = Component.translatable("sellmod.transfer.balance", ClientSellData.amountText(held));
        }
        float left = (this.width - BALANCE_WIDTH) / 2.0f;
        float top = contentTop() - HEADER_ROW - GAP;
        UiGlass.panel(graphics, left, top, BALANCE_WIDTH, HEADER_ROW, UiMetrics.radius(HEADER_ROW), 1.0f);
        CurrencyIcon.draw(graphics, left + UiMetrics.PAD_WIDE, top + (HEADER_ROW - BALANCE_ICON) / 2.0f,
                BALANCE_ICON, UiAccent.color(), 1.0f);
        float textLeft = left + UiMetrics.PAD_WIDE + BALANCE_ICON + UiMetrics.GAP;
        float room = left + BALANCE_WIDTH - UiMetrics.PAD_WIDE - textLeft;
        balanceLine.box(graphics, this.font, balanceLabel, textLeft, top, HEADER_ROW, room, 0.85f, UiAccent.text());
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        if (grid.empty()) {
            String key = grid.nobodyOnline() ? "sellmod.transfer.nobody" : "sellmod.transfer.not_found";
            renderWrapped(graphics, key, contentLeft() + LIST_WIDTH / 2.0f,
                    contentTop() + panelHeight() / 2.0f, LIST_WIDTH);
            return;
        }
        grid.render(graphics, mouseX, mouseY);
    }

    private void renderWrapped(GuiGraphics graphics, String key, float centerX, float centerY, float width) {
        List<Component> lines = wrapped.get(key);
        if (lines == null) {
            lines = split(graphics, Component.translatable(key), width - UiMetrics.PAD_WIDE * 2.0f);
            wrapped.put(key, lines);
        }
        float step = this.font.lineHeight * LINE_SCALE + UiMetrics.GAP;
        float y = centerY - lines.size() * step / 2.0f;
        for (int index = 0; index < lines.size(); index++) {
            UiRender.textCentered(graphics, this.font, lines.get(index), centerX, y, LINE_SCALE, UiAccent.textDim(),
                    false);
            y += step;
        }
    }

    private List<Component> split(GuiGraphics graphics, Component text, float width) {
        List<Component> lines = new ArrayList<>();
        for (FormattedCharSequence line : UiRender.split(graphics, this.font, text, LINE_SCALE,
                (int) (width / LINE_SCALE))) {
            lines.add(Component.literal(UiRender.flatten(line)));
        }
        return lines;
    }

    // WHY: смена получателя размывает карточку и проясняет её с новым лицом, как переходы Apple:
    // WHY: подмена кадром читалась бы как отсутствие отклика на щелчок
    private void renderCard(GuiGraphics graphics) {
        if (chosen == null ? cardFor != null : !chosen.equals(cardFor)) {
            cardFor = chosen;
            cardShown.snap(0.0f);
            relabelCard();
        }
        float shown = UiAnim.easeOut(cardShown.to(1.0f, UiFrame.delta()));
        float left = sideRowsLeft();
        float top = contentTop() + UiMetrics.PAD_WIDE;
        boolean veiled = shown < 1.0f && UiVeil.begin(graphics);
        try {
            paintCard(graphics, left, top + (1.0f - shown) * CARD_SLIDE, sideRowsWidth(), veiled ? 1.0f : shown);
        } finally {
            if (veiled) UiVeil.end(graphics, left, top, sideRowsWidth(), CARD, 0.0f, (1.0f - shown) * CARD_BLUR, shown);
        }
    }

    private void paintCard(GuiGraphics graphics, float left, float top, float width, float alpha) {
        TransferGrid.Person person = grid.person(chosen);
        if (person == null) {
            renderWrapped(graphics, chosen == null ? "sellmod.transfer.pick" : "sellmod.transfer.gone",
                    left + width / 2.0f, top + CARD / 2.0f, width);
            return;
        }
        float faceX = left + (width - FACE) / 2.0f;
        UiRender.panel(graphics, faceX - FACE_FRAME, top - FACE_FRAME, FACE + FACE_FRAME * 2.0f,
                FACE + FACE_FRAME * 2.0f, FACE_FRAME * 2.0f, UiTheme.alpha(FACE_SHADE, alpha));
        PlayerHeads.face(graphics, person.id(), faceX, top, FACE, alpha);
        UiRender.textTrackedFit(graphics, this.font, cardName, left + width / 2.0f,
                top + FACE + 4.0f, 12.0f, width, NAME_SCALE, 0.0f, UiTheme.alpha(UiAccent.text(), alpha), false);
        UiRender.textTrackedFit(graphics, this.font, cardTeam, left + width / 2.0f, top + FACE + 17.0f,
                10.0f, width, LINE_SCALE, 0.0f, UiTheme.alpha(teamColor(person), alpha), false);
    }

    private void relabelCard() {
        TransferGrid.Person person = grid.person(chosen);
        cardName = person == null ? Component.empty() : Component.literal(person.name());
        cardTeam = person == null ? Component.empty() : teamLine(person);
    }

    private Component teamLine(TransferGrid.Person person) {
        if (person.mate()) return Component.translatable("sellmod.transfer.team.own");
        PlayerTeam team = this.minecraft == null || this.minecraft.level == null ? null
                : this.minecraft.level.getScoreboard().getPlayersTeam(person.name());
        if (team == null) return Component.translatable("sellmod.transfer.team.none");
        return Component.translatable("sellmod.transfer.team.other", team.getDisplayName());
    }

    private static int teamColor(TransferGrid.Person person) {
        return person.mate() ? UiAccent.color() : UiAccent.textDim();
    }

    private void renderLeftLine(GuiGraphics graphics) {
        int color = leftShort ? UiPalette.alert() : UiAccent.textDim();
        UiRender.textTrackedFit(graphics, this.font, leftLabel, sideRowsLeft() + sideRowsWidth() / 2.0f,
                leftLineTop(), 10.0f, sideRowsWidth(), LINE_SCALE, 0.0f, color, false);
    }

    private void renderAnswer(GuiGraphics graphics) {
        Component answer = PayAnswers.since(answeredAt);
        if (answer == null || answeredAt <= 0L) return;
        if (answerBuiltFor != answeredAt) {
            answerBuiltFor = answeredAt;
            answerLines = split(graphics, answer, sideRowsWidth());
        }

        float shown = UiAnim.easeOut(UiAnim.clamp01((System.currentTimeMillis() - answeredAt) / ANSWER_MS));
        int color = PayAnswers.refused() ? UiPalette.alert() : UiAccent.color();
        float top = sendTop() + SEND_HEIGHT + UiMetrics.GAP_WIDE + (1.0f - shown) * ANSWER_RISE;
        for (int index = 0; index < Math.min(ANSWER_LINES, answerLines.size()); index++) {
            UiRender.textCentered(graphics, this.font, answerLines.get(index), sideRowsLeft() + sideRowsWidth() / 2.0f,
                    top, LINE_SCALE, UiTheme.alpha(color, shown), false);
            top += this.font.lineHeight * LINE_SCALE + UiMetrics.GAP_TIGHT;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (leaving()) return true;

        double x = localX(mouseX);
        double y = localY(mouseY);
        if (button == 0 && grid.over(x, y)) {
            UUID picked = grid.pick(x, y);
            if (picked != null) choose(picked);
            setFocused(null);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (leaving()) return true;
        if (grid.over(localX(mouseX), localY(mouseY))) {
            grid.scrollBy(amount > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
        if (enter && getFocused() == amountRow) {
            pressSend();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    // WHY: ответ сервера уже показан в самом окне: в шапке магазина он повторился бы вторым сообщением
    @Override
    protected void closing() {
        if (answeredAt > 0L) MenuFeedback.clear();
        if (parent instanceof GlassScreen glass) glass.reenter();
        Minecraft.getInstance().setScreen(parent);
    }
}
