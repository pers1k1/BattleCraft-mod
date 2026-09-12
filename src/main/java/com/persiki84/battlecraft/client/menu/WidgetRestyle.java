package com.persiki84.battlecraft.client.menu;

import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.shared.client.ui.SelfPainted;
import com.persiki84.shared.client.ui.UiFarewell;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiGlassStyle;
import com.persiki84.shared.client.ui.UiField;
import com.persiki84.shared.client.ui.UiInput;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSkin;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.LockIconButton;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class WidgetRestyle {
    private static final String SLIDER_VALUE = "f_93577_";
    private static final int MAX_DEPTH = 4;
    private static final int CLIP_MARGIN = 8;

    private static final float FIELD_FOCUS_ALPHA = 0.22f;
    private static final float FIELD_RIM = 1.0f;
    private static final float FIELD_RIM_ALPHA = 0.55f;
    private static final String[] BANISHED = {"de.keksuccino.justzoom"};

    private static final Map<AbstractWidget, State> states = new WeakHashMap<>();
    private static final List<Slot> slots = new ArrayList<>();
    private static final List<EditBox> fields = new ArrayList<>();
    private static final Set<AbstractWidget> repainted =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private static boolean probed;
    private static boolean muted;
    private static boolean owed;
    private static String strayName = "-";
    private static int strayCount;
    private static long strayFrame;
    private static int dressCount;
    private static float strayX;
    private static float strayY;
    private static float strayWidth;
    private static float strayHeight;

    private static final long STRAY_MEMORY = 120L;
    private static Field sliderValue;

    private WidgetRestyle() {}

    public static void mute(Screen screen) {
        owed = false;
        restore();
        slots.clear();
        fields.clear();
        collect(screen.children(), 0, null, -1);
        // WHY: строка чата живёт своей раскладкой: у неё своя подложка, свой подсказчик команд и
        // WHY: подъём при открытии, и перекраска поля разошлась бы со всем этим
        if (screen instanceof ChatScreen) fields.clear();
        for (Slot slot : slots) {
            slot.widget.setMessage(Component.empty());
            slot.widget.setAlpha(0.0f);
            repainted.add(slot.widget);
        }
        muted = true;
    }

    public static boolean dressing() {
        return muted || owed;
    }

    public static boolean repainted(AbstractWidget widget) {
        return (muted || owed) && repainted.contains(widget);
    }

    // WHY: поле рисуем сами только там, где одеваем экран: в чужом окне без нашей среды курсор
    // WHY: и подсказка встали бы по нашей метрике поверх чужой раскладки
    public static boolean dressing(EditBox field) {
        return (muted || owed) && known(field);
    }

    // WHY: щелчок приходит между кадрами, когда среда экрана уже снята, поэтому принадлежность
    // WHY: поля спрашивается по списку, а не по признаку идущей отрисовки
    public static boolean known(EditBox field) {
        if ((HudConfig.plainScreens() & 1) != 0) return false;
        return fields.contains(field) || UiField.owns(field);
    }

    public static void noteStray(AbstractWidget widget) {
        strayName = widget.getClass().getSimpleName();
        strayCount++;
        strayFrame = com.persiki84.shared.client.ui.UiFrame.frame();
        strayX = widget.getX();
        strayY = widget.getY();
        strayWidth = widget.getWidth();
        strayHeight = widget.getHeight();
    }

    public static void markStray(GuiGraphics graphics) {
        if (com.persiki84.shared.client.ui.UiFrame.frame() - strayFrame > STRAY_MEMORY) return;

        UiRender.rim(graphics, strayX, strayY, strayWidth, strayHeight, 3.0f, 1.0f, 0xFFFF4040);
    }

    public static void noteDress() {
        dressCount++;
    }

    public static String report() {
        return "muted=" + (muted ? 1 : 0) + " owed=" + (owed ? 1 : 0)
                + " slots=" + slots.size() + " kept=" + repainted.size()
                + " dress=" + dressCount;
    }

    public static String strayReport() {
        return "stray=" + strayCount + " last=" + strayName
                + " frame=" + (com.persiki84.shared.client.ui.UiFrame.frame() - strayFrame);
    }

    public static void restore() {
        if (!muted) return;
        muted = false;
        repainted.clear();
        for (Slot slot : slots) {
            slot.widget.setMessage(slot.message);
            slot.widget.setAlpha(1.0f);
        }
    }

    // WHY: плашка не лезет выше поля: ванильный экран оставляет над ним четыре пикселя до подписи,
    // WHY: и симметричный отступ съедал их целиком - надпись ложилась на кромку стекла
    public static void paintFields(GuiGraphics graphics) {
        for (EditBox field : fields) {
            // WHY: поле со своей плашкой второй не нужно: две утопленные подложки на одном месте
            // WHY: дают ореол шире самого поля, и кромка перестаёт совпадать с текстом
            if (!field.visible || UiField.owns(field)) continue;
            float x = field.getX() - UiField.PAD_X;
            float y = field.getY() - UiField.PAD_Y;
            float width = field.getWidth() + UiField.PAD_X * 2.0f;
            float height = field.getHeight() + UiField.PAD_Y * 2.0f;
            float radius = Math.min(UiGlassStyle.radiusPanel(), height / 2.0f);
            float focus = UiField.focus(field);
            UiGlass.sunken(graphics, x, y, width, height, radius, 1.0f);
            if (focus > 0.01f) {
                UiGlass.inner(graphics, x, y, width, height, radius, FIELD_FOCUS_ALPHA * focus, 0.4f);
                UiRender.rim(graphics, x, y, width, height, radius, FIELD_RIM,
                        UiTheme.alpha(UiAccent.color(), FIELD_RIM_ALPHA * focus));
            }
        }
    }

    public static void paint(GuiGraphics graphics, float delta) {
        float scale = UiSkin.fit(graphics);
        for (Slot slot : slots) {
            paint(graphics, slot, delta, scale);
        }
    }

    public static boolean press(double mouseX, double mouseY) {
        for (Slot slot : slots) {
            AbstractWidget widget = slot.widget;
            if (!widget.active || !widget.visible || !widget.isMouseOver(mouseX, mouseY)) continue;
            if (slot.clip != null && !slot.clip.isMouseOver(mouseX, mouseY)) continue;
            state(widget).press.snap(1.0f);
            return true;
        }
        return false;
    }

    public static void forget() {
        if (UiFarewell.burning()) {
            owed = true;
            return;
        }
        wipe();
    }

    public static void settle() {
        if (!owed || UiFarewell.burning()) return;

        owed = false;
        wipe();
    }

    private static void wipe() {
        restore();
        slots.clear();
        fields.clear();
        states.clear();
        repainted.clear();
    }

    private static void collect(List<? extends GuiEventListener> children, int depth, AbstractSelectionList<?> clip, int row) {
        if (depth > MAX_DEPTH) return;

        for (GuiEventListener listener : children) {
            if (listener instanceof AbstractWidget foreign && banished(foreign)) {
                foreign.visible = false;
                foreign.active = false;
                continue;
            }
            if (listener instanceof EditBox field) {
                fields.add(field);
            } else if (listener instanceof AbstractWidget widget && paintable(widget)) {
                slots.add(new Slot(widget, widget.getMessage(), clip, row));
            }
            if (listener instanceof AbstractSelectionList<?> list) {
                collectRows(list, depth + 1);
            } else if (listener instanceof ContainerEventHandler container) {
                collect(container.children(), depth + 1, clip, row);
            }
        }
    }

    private static void collectRows(AbstractSelectionList<?> list, int depth) {
        if (depth > MAX_DEPTH) return;

        List<? extends GuiEventListener> rows = list.children();
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index) instanceof ContainerEventHandler entry) {
                collect(entry.children(), depth + 1, list, index);
            }
        }
    }

    private static boolean banished(AbstractWidget widget) {
        for (String prefix : BANISHED) {
            if (widget.getClass().getName().startsWith(prefix)) return true;
        }
        return false;
    }

    private static boolean paintable(AbstractWidget widget) {
        if (!widget.visible || widget.getWidth() <= 8 || widget.getHeight() <= 6) return false;
        if (widget instanceof SelfPainted) return false;
        if (widget instanceof Checkbox) return true;
        if (widget instanceof LockIconButton) return true;
        if (widget instanceof AbstractSliderButton) return true;
        return widget instanceof AbstractButton && !widget.getMessage().getString().isBlank();
    }

    private static void paint(GuiGraphics graphics, Slot slot, float delta, float labelScale) {
        if (slot.clip == null) {
            paintWidget(graphics, slot, delta, labelScale);
            return;
        }
        if (!clipToRow(graphics, slot)) return;
        try {
            paintWidget(graphics, slot, delta, labelScale);
        } finally {
            graphics.disableScissor();
        }
    }

    private static boolean clipToRow(GuiGraphics graphics, Slot slot) {
        if (!ScrollRestyle.rowVisible(slot.clip, slot.row)) return false;
        ScrollRestyle.Bounds bounds = ScrollRestyle.boundsOf(slot.clip);
        if (bounds == null) return false;

        AbstractWidget widget = slot.widget;
        if (widget.getY() + widget.getHeight() < bounds.top() || widget.getY() > bounds.bottom()) return false;

        int left = (int) Math.min(bounds.left() - CLIP_MARGIN, widget.getX() - CLIP_MARGIN);
        int right = (int) Math.max(bounds.right() + CLIP_MARGIN,
                widget.getX() + widget.getWidth() + CLIP_MARGIN);
        graphics.enableScissor(left, (int) bounds.top(), right, (int) bounds.bottom());
        return true;
    }

    private static void paintWidget(GuiGraphics graphics, Slot slot, float delta, float labelScale) {
        AbstractWidget widget = slot.widget;
        State state = state(widget);
        boolean focused = widget.active && UiInput.pointed(widget);
        if (focused != state.announced) {
            state.announced = focused;
            if (focused) UiSound.hover();
        }

        paintSkin(graphics, slot, state, state.hover.to(focused ? 1.0f : 0.0f, delta), delta, labelScale);
    }

    private static void paintSkin(GuiGraphics graphics, Slot slot, State state, float focus,
                                  float delta, float labelScale) {
        AbstractWidget widget = slot.widget;
        float x = widget.getX();
        float y = widget.getY();
        float width = widget.getWidth();
        float height = widget.getHeight();

        double raw = widget instanceof AbstractSliderButton slider ? value(slider) : -1.0;
        if (widget instanceof Checkbox box) {
            UiSkin.checkbox(graphics, x, y, width, height, slot.message, box.selected(), widget.active,
                    focus, labelScale);
        } else if (widget instanceof LockIconButton lock) {
            UiSkin.icon(graphics, x, y, width, height, widget.active, focus);
            UiRender.iconLock(graphics, x + width / 2.0f, y + height / 2.0f,
                    Math.min(width, height) * 0.62f, lock.isLocked(),
                    UiTheme.mix(UiAccent.text(), UiTheme.WHITE, focus));
        } else if (raw < 0.0) {
            UiSkin.button(graphics, x, y, width, height, slot.message, widget.active,
                    focus, state.press.to(0.0f, delta), labelScale);
        } else {
            UiSkin.slider(graphics, x, y, width, height, slot.message, widget.active,
                    focus, state.value.to((float) raw, delta), labelScale);
        }
    }

    private static State state(AbstractWidget widget) {
        return states.computeIfAbsent(widget, key -> new State());
    }

    private static double value(AbstractSliderButton slider) {
        if (!probed) {
            probed = true;
            try {
                sliderValue = ObfuscationReflectionHelper.findField(AbstractSliderButton.class, SLIDER_VALUE);
            } catch (Throwable error) {
                sliderValue = null;
            }
        }
        if (sliderValue == null) return -1.0;
        try {
            return sliderValue.getDouble(slider);
        } catch (Throwable error) {
            sliderValue = null;
            return -1.0;
        }
    }

    private record Slot(AbstractWidget widget, Component message, AbstractSelectionList<?> clip, int row) {}

    private static final class State {
        private final Smooth hover = new Smooth(0.0f, 16.0f);
        private final Smooth press = new Smooth(0.0f, 22.0f);
        private final Smooth value = new Smooth(24.0f);
        private boolean announced;
    }
}
