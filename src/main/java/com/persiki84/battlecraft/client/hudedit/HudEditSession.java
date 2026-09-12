package com.persiki84.battlecraft.client.hudedit;

import com.persiki84.battlecraft.client.custom.Customization;
import com.persiki84.battlecraft.client.custom.HudBox;
import com.persiki84.battlecraft.client.custom.HudDock;
import com.persiki84.battlecraft.client.custom.HudLayout;
import com.persiki84.battlecraft.client.custom.HudPlacement;
import com.persiki84.battlecraft.client.custom.HudSlot;
import com.persiki84.minimap.client.ClientMapData;
import com.persiki84.minimap.client.MinimapOverlay;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.TopStack;
import com.persiki84.shared.client.ui.UiScale;
import com.persiki84.shared.client.ui.UiSound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Style;
import org.lwjgl.glfw.GLFW;

public final class HudEditSession {
    public static final float HANDLE = 7.0f;

    private static final float PRESENCE_SPEED = 11.0f;
    private static final float STAGGER = 0.028f;
    private static final float APPEAR = 0.22f;
    private static final float MIN_SPAN = 8.0f;
    private static final float INPUT_BAND = 14.0f;

    private enum Grab {
        NONE,
        MOVE,
        SIZE_START,
        SIZE_END
    }

    private static final Smooth presence = new Smooth(0.0f, PRESENCE_SPEED);

    private static boolean open;
    private static boolean measuring;
    private static float age;
    private static HudSlot hovered;
    private static HudSlot selected;
    private static Grab grab = Grab.NONE;
    private static float grabX;
    private static float grabY;
    private static float holdX;
    private static float holdY;
    private static int holdMapSize;
    private static float holdWidth;
    private static float holdHeight;
    private static float pointerX;
    private static float pointerY;
    private static float screenX;
    private static float screenY;

    private HudEditSession() {}

    public static void advance(float delta) {
        boolean chat = Minecraft.getInstance().screen instanceof ChatScreen;
        if (chat != open) begin(chat);

        age += delta;
        presence.to(open ? 1.0f : 0.0f, delta);
        readPointer();
        if (open) track();
        HudSlotMenu.advance(screenX, screenY, held(), delta);
    }

    private static void begin(boolean chat) {
        open = chat;
        age = 0.0f;
        hovered = null;
        measuring = chat;
        if (chat) return;

        finish();
        selected = null;
        HudSlotMenu.close();
    }

    private static void track() {
        if (grab == Grab.NONE) {
            hovered = HudLayout.pick(pointerX, pointerY);
            return;
        }
        if (!held()) {
            finish();
            return;
        }
        if (grab == Grab.MOVE) {
            drag();
            return;
        }
        resize();
    }

    public static boolean press(double atX, double atY, int button) {
        if (!open) return false;

        if (HudSlotMenu.press(atX, atY, button)) return true;
        if (atY >= Minecraft.getInstance().getWindow().getGuiScaledHeight() - INPUT_BAND) return false;
        if (chatOwns(atX, atY)) return false;

        float ui = UiScale.factor();
        float pointX = (float) (atX / ui);
        float pointY = (float) (atY / ui);
        Grab corner = handleAt(pointX, pointY);
        HudSlot picked = corner == Grab.NONE ? HudLayout.pick(pointX, pointY) : selected;
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            selected = picked;
            HudSlotMenu.open(picked, (float) atX, (float) atY);
            return true;
        }
        if (picked == null) {
            selected = null;
            return false;
        }
        return button == GLFW.GLFW_MOUSE_BUTTON_LEFT && start(picked, pointX, pointY, corner);
    }

    private static boolean chatOwns(double atX, double atY) {
        ChatComponent chat = Minecraft.getInstance().gui.getChat();
        if (chat.getMessageTagAt(atX, atY) != null) return true;

        Style style = chat.getClickedComponentStyleAt(atX, atY);
        return style != null && style.getClickEvent() != null;
    }

    private static boolean start(HudSlot picked, float pointX, float pointY, Grab corner) {
        if (picked != selected) UiSound.press();
        selected = picked;
        HudSlotMenu.close();
        HudBox box = HudLayout.box(picked);
        holdWidth = box.width();
        holdHeight = box.height();
        grab = corner == Grab.NONE ? Grab.MOVE : corner;
        grabX = pointX - box.x();
        grabY = pointY - box.y();
        holdX = corner == Grab.SIZE_START ? box.x() + box.width() * box.scale() : box.x();
        holdY = corner == Grab.SIZE_START ? box.y() + box.height() * box.scale() : box.y();
        holdMapSize = ClientMapData.minimapSize;
        HudLayout.dragging(picked);
        return true;
    }

    private static Grab handleAt(float pointX, float pointY) {
        if (selected == null || !HudLayout.box(selected).drawn()) return Grab.NONE;

        HudBox box = HudLayout.box(selected);
        float width = box.width() * box.scale();
        float height = box.height() * box.scale();
        if (near(pointX, pointY, box.x(), box.y())) return Grab.SIZE_START;
        if (near(pointX, pointY, box.x() + width, box.y() + height)) return Grab.SIZE_END;
        return Grab.NONE;
    }

    private static boolean near(float pointX, float pointY, float cornerX, float cornerY) {
        return Math.abs(pointX - cornerX) <= HANDLE && Math.abs(pointY - cornerY) <= HANDLE;
    }

    private static void drag() {
        HudBox box = HudLayout.box(selected);
        float width = box.width() * box.scale();
        float height = box.height() * box.scale();
        float screenWidth = logicalWidth();
        float screenHeight = logicalHeight();

        HudGuides.solve(selected, pointerX - grabX, pointerY - grabY, width, height,
                screenWidth, screenHeight);
        HudPlacement placement = HudLayout.of(selected);
        placement.dock(null);
        HudLayout.moveTo(selected, HudGuides.across().position(), HudGuides.down().position(),
                screenWidth, screenHeight);
        placement.dock(allowed(HudGuides.dock()));
        TopStack.forget();
    }

    private static HudDock allowed(HudDock dock) {
        if (dock == null || !HudLayout.free(selected, dock.target())) return null;
        return dock;
    }

    private static void resize() {
        HudPlacement placement = HudLayout.of(selected);
        float wantWidth = grab == Grab.SIZE_END ? pointerX - holdX : holdX - pointerX;
        float wantHeight = grab == Grab.SIZE_END ? pointerY - holdY : holdY - pointerY;
        if (selected == HudSlot.MINIMAP) {
            resizeMap(reach(wantWidth, holdWidth, wantHeight, holdHeight));
            return;
        }

        placement.scale(reach(wantWidth, holdWidth, wantHeight, holdHeight));
        if (placement.dock() != null) return;

        float shownWidth = holdWidth * placement.scale();
        float shownHeight = holdHeight * placement.scale();
        HudLayout.moveTo(selected,
                grab == Grab.SIZE_END ? holdX : holdX - shownWidth,
                grab == Grab.SIZE_END ? holdY : holdY - shownHeight,
                logicalWidth(), logicalHeight());
    }

    private static void resizeMap(float scale) {
        ClientMapData.minimapSize = Math.round(Math.max(ClientMapData.MIN_SIZE,
                Math.min(ClientMapData.MAX_SIZE, holdMapSize * scale)));
        if (grab != Grab.SIZE_START) return;

        float span = MinimapOverlay.renderSize();
        HudLayout.moveTo(selected, holdX - span, holdY - span, logicalWidth(), logicalHeight());
    }

    private static float reach(float wantWidth, float baseWidth, float wantHeight, float baseHeight) {
        float byWidth = Math.max(MIN_SPAN, wantWidth) / Math.max(MIN_SPAN, baseWidth);
        float byHeight = Math.max(MIN_SPAN, wantHeight) / Math.max(MIN_SPAN, baseHeight);
        return (byWidth + byHeight) / 2.0f;
    }

    private static void finish() {
        if (grab == Grab.NONE) return;

        grab = Grab.NONE;
        HudLayout.dragging(null);
        HudGuides.clear();
        Customization.save();
        UiSound.slot();
    }

    public static boolean escape() {
        if (!HudSlotMenu.showing()) return false;

        HudSlotMenu.close();
        return true;
    }

    private static void readPointer() {
        Minecraft mc = Minecraft.getInstance();
        double scale = mc.getWindow().getScreenWidth() / (double) mc.getWindow().getGuiScaledWidth();
        float ui = UiScale.factor();
        if (scale <= 0.0) return;

        screenX = (float) (mc.mouseHandler.xpos() / scale);
        screenY = (float) (mc.mouseHandler.ypos() / scale);
        pointerX = screenX / ui;
        pointerY = screenY / ui;
    }

    private static boolean held() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    public static float logicalWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth() / UiScale.factor();
    }

    public static float logicalHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight() / UiScale.factor();
    }

    public static float presence() {
        return presence.get();
    }
    public static boolean claimMeasure() {
        if (!measuring) return false;

        measuring = false;
        return true;
    }

    public static boolean fading() {
        return !open && presence.get() > 0.004f;
    }

    public static float appear(int index) {
        float since = age - index * STAGGER;
        if (since <= 0.0f) return 0.0f;
        return Math.min(1.0f, since / APPEAR);
    }

    public static HudSlot hovered() {
        return hovered;
    }

    public static HudSlot selected() {
        return selected;
    }

    public static boolean dragging() {
        return grab == Grab.MOVE;
    }
}
