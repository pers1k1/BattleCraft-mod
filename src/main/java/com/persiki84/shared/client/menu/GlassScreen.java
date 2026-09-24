package com.persiki84.shared.client.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiAssemble;
import com.persiki84.shared.client.ui.UiDress;
import com.persiki84.shared.client.ui.UiEmber;
import com.persiki84.shared.client.ui.UiFarewell;
import com.persiki84.shared.client.ui.UiBackdrop;
import com.persiki84.shared.client.ui.UiPlane;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiReveal;
import com.persiki84.shared.client.ui.UiStage;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiMotion;
import com.persiki84.shared.client.ui.UiMotionSet;
import com.persiki84.shared.menu.MenuFace;
import com.persiki84.shared.menu.MenuKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public abstract class GlassScreen extends Screen implements DimmedScreen, UiEmber {
    private static final float ENTER_SCALE = 0.94f;
    private static final float ENTER_LIFT = 7.0f;
    private static final float ENTER_SPEED = 17.0f;
    private static final float LEAVE_SPEED = 34.0f;
    private static final float ENTER_SECONDS = 0.85f;
    private static final float DIM_LIGHT = 0.0f;
    private static final float GONE = 0.012f;
    private static final float ENTER_CHARGE = 0.55f;
    private static final float SETTLED = 0.999f;
    private static final int OFF_SCREEN = -1;
    private static final float CENTRE = 0.5f;
    protected static final int OVERSCAN = 3;

    private final Smooth entrance = new Smooth(0.0f, ENTER_SPEED);
    private final UiReveal reveal = new UiReveal();
    private UiPlane plane;
    private int pointerX;
    private int pointerY;
    private int cursorX;
    private int cursorY;
    private double rawX;
    private double rawY;
    private boolean leaving;

    protected GlassScreen(Component title) {
        super(title);
    }

    protected abstract void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    protected void renderBackdrop(GuiGraphics graphics) {
    }

    protected void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    protected float enterSeconds() {
        return ENTER_SECONDS;
    }

    protected float revealTop() {
        return 0.0f;
    }

    protected float revealSpan() {
        return this.height;
    }

    public boolean revealing() {
        return reveal.active();
    }

    protected float contentScale() {
        return 1.0f;
    }

    public MenuKind presence() {
        return MenuKind.MENU;
    }

    public MenuFace face() {
        return MenuFace.of(presence(), getTitle());
    }

    // WHY: живой кадр окна уходит тиммейтам, и админские панели в него не попадают: там данные,
    // WHY: которые сервер обычному игроку нарочно не отдаёт
    public boolean broadcast() {
        return presence() != MenuKind.ADMIN;
    }

    public boolean settled() {
        return !leaving && !reveal.active() && shown() >= SETTLED;
    }

    protected Area frameArea() {
        return new Area(0.0f, 0.0f, this.width, this.height);
    }

    public Area frame() {
        Area local = frameArea();
        float scale = entranceScale(1.0f);
        float left = this.width / 2.0f + (local.x() - this.width / 2.0f) * scale;
        float top = this.height / 2.0f + (local.y() - this.height / 2.0f) * scale;
        return new Area(left, top, local.width() * scale, local.height() * scale);
    }

    public record Area(float x, float y, float width, float height) {}

    protected boolean worldAnchored() {
        return UiPlane.allowed() && this.minecraft != null && this.minecraft.level != null;
    }

    protected static <T> List<T> window(List<T> source, int scroll, int capacity) {
        int from = Math.min(scroll, Math.max(0, source.size() - 1));
        int to = Math.min(source.size(), from + capacity);
        return from >= to ? List.of() : source.subList(from, to);
    }

    // WHY: соседи окна кладутся за кромку списка и режутся по ней: без них строка,
    // WHY: уходящая с прокруткой, пропадала бы мгновенно, а входящая возникала бы целиком
    protected static <T> List<T> around(List<T> source, int scroll, int capacity) {
        int from = Math.max(0, Math.min(scroll, source.size()) - OVERSCAN);
        int to = Math.min(source.size(), scroll + capacity + OVERSCAN);
        return from >= to ? List.of() : source.subList(from, to);
    }

    protected static int lead(int scroll) {
        return Math.min(OVERSCAN, Math.max(0, scroll));
    }

    protected boolean leaving() {
        return leaving;
    }

    protected boolean inputCaptured() {
        return false;
    }

    protected int cursorX() {
        return cursorX;
    }

    protected int cursorY() {
        return cursorY;
    }

    public void dismiss() {
        leaving = true;
    }

    // WHY: экран, к которому вернулись, это тот же экземпляр, и его проявление уже доиграно:
    // WHY: без завода заново возврат из подменю подменял бы картинку кадром, без всякого хода
    public void reenter() {
        leaving = false;
        plane = null;
        reveal.restart();
        entrance.snap(0.0f);
    }

    @Override
    public void onClose() {
        if (leaving) {
            closing();
            return;
        }
        leaving = true;
    }

    // WHY: горение доигрывает после закрытия, поэтому уход с экрана случается не в onClose,
    // WHY: а здесь: экрану с родителем нужна одна точка, где он возвращает управление ему
    protected void closing() {
        super.onClose();
    }

    @Override
    public void resize(Minecraft client, int width, int height) {
        plane = null;
        super.resize(client, width, height);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        reveal.pace(motion() == UiMotionSet.GLASS ? UiMotionSet.GLASS.enterSeconds() : enterSeconds());
        reveal.advance();
        float shown = advance();
        if (leaving && !UiReveal.enabled() && shown <= GONE) {
            closing();
            return;
        }

        anchor();
        veil(graphics);
        renderBackdrop(graphics);
        graphics.flush();
        boolean staged = (reveal.active() || leaving) && UiStage.begin();
        reveal.report(getClass().getSimpleName(), staged);

        // WHY: содержимое экрана рисуют чужие виджеты и мосты: исключение между begin и end
        // WHY: оставляло кадр писать в офскрин, и дальше игрок видел чёрный экран до перезахода
        try {
            paintContent(graphics, mouseX, mouseY, partialTick, shown, staged);
        } finally {
            if (staged) UiStage.end();
        }

        if (!staged) {
            if (leaving) closing();
            return;
        }
        resolveStage(graphics);
    }

    // WHY: мир не на паузе, пока экран открыт: игрока несёт инерция, качание головы и camera-overhaul
    // WHY: двигают камеру, и панель, прибитая к точке мира, уезжает от игрока прямо под курсором
    private void anchor() {
        if (!leaving || plane != null || !worldAnchored()) return;
        plane = UiPlane.capture(this.width, this.height);
    }

    // WHY: курсор берётся замороженным на закрытии, иначе наведённый элемент начал бы гаснуть
    // WHY: прямо в горении и первый его кадр разошёлся бы с последним кадром экрана
    @Override
    public void paintEmber(GuiGraphics graphics, float partialTick) {
        UiDress.begin(graphics, this);
        MenuHint.freeze(true);
        try {
            paintContent(graphics, cursorX, cursorY, partialTick, 1.0f, true);
        } finally {
            MenuHint.freeze(false);
            UiDress.end(graphics, this);
        }
    }

    private void paintContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                              float shown, boolean staged) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, staged ? 1.0f : shown);
        boolean quantized = UiRender.rawScale(settling(seated(shown)));
        aim(mouseX, mouseY);
        try {
            paintScaled(graphics, partialTick, shown);
            renderOverlay(graphics, cursorX, cursorY, partialTick);
            graphics.flush();
        } finally {
            UiRender.rawScale(quantized);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    private void aim(int mouseX, int mouseY) {
        rawX = mouseX;
        rawY = mouseY;
        cursorX = mouseX;
        cursorY = mouseY;
        pointerX = (int) localX(mouseX, mouseY);
        pointerY = (int) localY(mouseX, mouseY);
    }

    // WHY: подсказка рисуется здесь, а не поверх экрана: в этой позе она живёт в тех же координатах,
    // WHY: что и строки, и попадает в офскрин-стадию, то есть в проявление, горение и разворот в мир
    private void paintScaled(GuiGraphics graphics, float partialTick, float shown) {
        graphics.pose().pushPose();
        applyEntrance(graphics, shown);
        try {
            renderContent(graphics, inputCaptured() ? OFF_SCREEN : pointerX,
                    inputCaptured() ? OFF_SCREEN : pointerY, partialTick);
            MenuHint.render(graphics);
            graphics.flush();
        } finally {
            graphics.pose().popPose();
        }
    }

    private void resolveStage(GuiGraphics graphics) {
        UiMotionSet set = motion();
        if (leaving) {
            UiFarewell.begin(this, plane, revealTop() / this.height, revealSpan() / this.height,
                    burnOriginX(), burnOriginY(), set);
            UiFarewell.first(graphics, this.width, this.height);
            closing();
            return;
        }
        // WHY: заряд у входа из света даёт фронту цвет акцента, как на титульном экране: без него
        // WHY: свет шёл белой полосой, а свечения кромки не было вовсе
        UiAssemble.draw(graphics, this.width, this.height, UiStage.texture(),
                reveal.phase(), reveal.seconds(), set.enterMode(), revealTop(), revealSpan(), ENTER_CHARGE);
    }

    protected UiMotionSet motion() {
        return UiMotion.of(this);
    }

    private float burnOriginX() {
        return CENTRE;
    }

    private float burnOriginY() {
        return 1.0f - revealMiddle() / this.height;
    }

    private float revealMiddle() {
        return revealTop() + revealSpan() / 2.0f;
    }

    protected void renderWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void veil(GuiGraphics graphics) {
        ScreenDim.render(graphics, this.width, this.height, DIM_LIGHT);
        graphics.flush();
        UiBackdrop.capture();
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
    }

    private static boolean settling(float shown) {
        return shown < SETTLED;
    }

    protected float shown() {
        return UiAnim.easeOut(entrance.get());
    }

    private float advance() {
        boolean fading = leaving && !UiReveal.enabled();
        float target = fading ? 0.0f : 1.0f;
        return UiAnim.easeOut(entrance.to(target, fading ? LEAVE_SPEED : ENTER_SPEED, UiFrame.delta()));
    }

    private void applyEntrance(GuiGraphics graphics, float shown) {
        float scale = entranceScale(shown);
        graphics.pose().translate(this.width / 2.0f, this.height / 2.0f + entranceLift(shown), 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.pose().translate(-this.width / 2.0f, -this.height / 2.0f, 0.0f);
    }

    // WHY: масштаб въезда сдвигает кромки на доли пикселя, и между элементами проступает кадр под интерфейсом
    private static float seated(float shown) {
        return UiReveal.enabled() ? 1.0f : shown;
    }

    private float entranceScale(float shown) {
        return (ENTER_SCALE + (1.0f - ENTER_SCALE) * seated(shown)) * contentScale();
    }

    private static float entranceLift(float shown) {
        return (1.0f - seated(shown)) * ENTER_LIFT;
    }

    public double localX(double mouseX) {
        return localX(mouseX, rawY);
    }

    public double localY(double mouseY) {
        return localY(rawX, mouseY);
    }

    public double localX(double mouseX, double mouseY) {
        return this.width / 2.0 + (mouseX - this.width / 2.0) / entranceScale(shown());
    }

    public double localY(double mouseX, double mouseY) {
        return this.height / 2.0 + (mouseY - this.height / 2.0 - entranceLift(shown())) / entranceScale(shown());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (leaving) return true;
        remember(mouseX, mouseY);
        return super.mouseClicked(localX(mouseX, mouseY), localY(mouseX, mouseY), button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        remember(mouseX, mouseY);
        return super.mouseReleased(localX(mouseX, mouseY), localY(mouseX, mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (leaving) return true;
        remember(mouseX, mouseY);
        return super.mouseDragged(localX(mouseX, mouseY), localY(mouseX, mouseY), button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (leaving) return true;
        remember(mouseX, mouseY);
        return super.mouseScrolled(localX(mouseX, mouseY), localY(mouseX, mouseY), amount);
    }

    private void remember(double mouseX, double mouseY) {
        rawX = mouseX;
        rawY = mouseY;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
