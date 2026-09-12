package com.persiki84.minimap.client;

import com.persiki84.minimap.network.MapRequestPacket;
import com.persiki84.minimap.network.PacketHandler;
import com.persiki84.minimap.server.MapShareScope;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiBackdrop;
import com.persiki84.shared.client.ui.UiGlassStyle;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSlider;
import com.persiki84.shared.client.ui.UiAccent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MinimapSettingsScreen extends Screen {
    private static final int PANEL_WIDTH = 232;
    private static final int ROW_WIDTH = 200;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 8;
    private static final int HALF_GAP = 6;
    private static final int OPERATOR_LEVEL = 2;
    private static final int BASE_ROWS = 6;
    private static final int OPERATOR_ROWS = 2;
    private static final long CONFIRM_MS = 3000L;
    private static final long NOTICE_MS = 2600L;
    private static final float PANEL_TOP_PAD = 22.0f;
    private static final float PANEL_BOTTOM_PAD = 22.0f;
    private static final float NOTICE_GAP = 8.0f;

    private final Screen parent;

    private UiButton armed;
    private String armedLabel;
    private long armedUntil;
    private Component notice;
    private long noticeUntil;
    private int rows;
    private int placed;

    public MinimapSettingsScreen(Screen parent) {
        super(Component.translatable("minimap.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rows = BASE_ROWS + (operator() ? OPERATOR_ROWS : 0);
        placed = 0;
        armed = null;

        addToggle("minimap.settings.minimap", () -> ClientMapData.enableMinimap,
                value -> ClientMapData.enableMinimap = value);
        addToggle("minimap.settings.other_markers", () -> ClientMapData.showOtherMarkers,
                value -> ClientMapData.showOtherMarkers = value);
        addSize();
        addZoom();
        addMapRows();
        addServerRows();

        this.addRenderableWidget(new UiButton(left(), nextTop(), ROW_WIDTH, ROW_HEIGHT,
                Component.translatable("minimap.button.close"), button -> this.onClose()));
    }

    private void addMapRows() {
        int top = nextTop();
        this.addRenderableWidget(new UiButton(left(), top, halfWidth(), ROW_HEIGHT,
                Component.translatable("minimap.button.pull"), button -> pull()));
        this.addRenderableWidget(new UiButton(rightHalf(), top, halfWidth(), ROW_HEIGHT,
                Component.translatable("minimap.button.forget"),
                button -> guard(button, "minimap.button.forget", this::forget)));
    }

    private void addServerRows() {
        if (!operator()) return;

        int top = nextTop();
        this.addRenderableWidget(new UiButton(left(), top, halfWidth(), ROW_HEIGHT,
                Component.translatable("minimap.button.publish"),
                button -> guard(button, "minimap.button.publish", this::publish)));
        this.addRenderableWidget(new UiButton(rightHalf(), top, halfWidth(), ROW_HEIGHT,
                Component.translatable("minimap.button.wipe_shared"),
                button -> guard(button, "minimap.button.wipe_shared",
                        () -> command("bc map reset shared", "minimap.notice.wiped_shared"))));

        this.addRenderableWidget(new UiButton(left(), nextTop(), ROW_WIDTH, ROW_HEIGHT,
                Component.translatable("minimap.button.wipe_teams"),
                button -> guard(button, "minimap.button.wipe_teams",
                        () -> command("bc map reset teams", "minimap.notice.wiped_teams"))));
    }

    private void addToggle(String key, java.util.function.BooleanSupplier value,
                           java.util.function.Consumer<Boolean> apply) {
        this.addRenderableWidget(new UiButton(left(), nextTop(), ROW_WIDTH, ROW_HEIGHT,
                toggleLabel(key, value.getAsBoolean()), button -> {
            apply.accept(!value.getAsBoolean());
            button.setMessage(toggleLabel(key, value.getAsBoolean()));
        }));
    }

    private void addSize() {
        this.addRenderableWidget(new UiSlider(left(), nextTop(), ROW_WIDTH, ROW_HEIGHT,
                Component.translatable("minimap.settings.size", ClientMapData.minimapSize),
                (ClientMapData.minimapSize - ClientMapData.MIN_SIZE)
                        / (double) (ClientMapData.MAX_SIZE - ClientMapData.MIN_SIZE)) {
            @Override
            protected void updateMessage() {
                this.setMessage(Component.translatable("minimap.settings.size", ClientMapData.minimapSize));
            }

            @Override
            protected void applyValue() {
                ClientMapData.minimapSize = (int) (ClientMapData.MIN_SIZE
                        + this.value * (ClientMapData.MAX_SIZE - ClientMapData.MIN_SIZE));
            }
        });
    }

    private void addZoom() {
        this.addRenderableWidget(new UiSlider(left(), nextTop(), ROW_WIDTH, ROW_HEIGHT,
                Component.translatable("minimap.settings.zoom", String.format("%.1f", ClientMapData.minimapZoom)),
                (ClientMapData.minimapZoom - 0.5) / 4.5) {
            @Override
            protected void updateMessage() {
                this.setMessage(Component.translatable("minimap.settings.zoom", String.format("%.1f", ClientMapData.minimapZoom)));
            }

            @Override
            protected void applyValue() {
                ClientMapData.minimapZoom = (float) (0.5 + this.value * 4.5);
            }
        });
    }

    private void pull() {
        if (!ClientMapData.serverTakesChunks()) {
            show("minimap.notice.no_server");
            return;
        }

        PacketHandler.INSTANCE.sendToServer(new MapRequestPacket());
        show("minimap.notice.pulled");
    }

    private void forget() {
        ClientMapStorage.reset();
        show("minimap.notice.forgotten");
    }

    private void publish() {
        int sent = MapUploader.upload(MapShareScope.EVERYONE, "");
        show(sent == 0 ? "minimap.notice.nothing" : "minimap.notice.published");
    }

    private void command(String line, String noticeKey) {
        if (this.minecraft == null || this.minecraft.player == null) return;

        this.minecraft.player.connection.sendCommand(line);
        show(noticeKey);
    }

    // WHY: сброс стирает файл и не спрашивается заново, поэтому кнопка взводится и ждёт второго
    // WHY: нажатия: случайный клик по соседней строке иначе уносит карту целиком
    private void guard(UiButton button, String label, Runnable action) {
        if (armed == button) {
            disarm();
            action.run();
            return;
        }

        disarm();
        armed = button;
        armedLabel = label;
        armedUntil = System.currentTimeMillis() + CONFIRM_MS;
        button.setMessage(Component.translatable("minimap.button.sure"));
    }

    private void disarm() {
        if (armed == null) return;

        armed.setMessage(Component.translatable(armedLabel));
        armed = null;
    }

    private void show(String key) {
        notice = Component.translatable(key);
        noticeUntil = System.currentTimeMillis() + NOTICE_MS;
    }

    private int left() {
        return this.width / 2 - ROW_WIDTH / 2;
    }

    private int rightHalf() {
        return left() + halfWidth() + HALF_GAP;
    }

    private int halfWidth() {
        return (ROW_WIDTH - HALF_GAP) / 2;
    }

    private int nextTop() {
        return (int) (panelTop() + PANEL_TOP_PAD) + placed++ * (ROW_HEIGHT + ROW_GAP);
    }

    private boolean operator() {
        return this.minecraft != null && this.minecraft.player != null
                && this.minecraft.player.hasPermissions(OPERATOR_LEVEL);
    }

    private float panelTop() {
        return (this.height - panelHeight()) / 2.0f;
    }

    private float panelHeight() {
        return rows * (ROW_HEIGHT + ROW_GAP) - ROW_GAP + PANEL_TOP_PAD + PANEL_BOTTOM_PAD;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (armed != null && System.currentTimeMillis() > armedUntil) disarm();

        UiRender.rect(guiGraphics, 0, 0, this.width, this.height, 0xC00A0A0C);
        UiBackdrop.capture();

        float panelX = (this.width - PANEL_WIDTH) / 2.0f;
        float panelY = panelTop();

        UiGlass.sheet(guiGraphics, panelX, panelY, PANEL_WIDTH, panelHeight(), UiGlassStyle.radiusPanel() + 3.0f, 1.0f);
        UiRender.textCentered(guiGraphics, this.font, this.title, this.width / 2.0f, panelY + 8.0f, 1.0f,
                UiAccent.text(), false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderNotice(guiGraphics, panelY + panelHeight() + NOTICE_GAP);
    }

    private void renderNotice(GuiGraphics guiGraphics, float y) {
        if (notice == null) return;
        if (System.currentTimeMillis() > noticeUntil) {
            notice = null;
            return;
        }

        UiRender.textCentered(guiGraphics, this.font, notice, this.width / 2.0f, y, 1.0f, UiAccent.text(), false);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private static Component toggleLabel(String key, boolean enabled) {
        return Component.translatable(key, Component.translatable(enabled ? "minimap.toggle.on" : "minimap.toggle.off"));
    }
}
