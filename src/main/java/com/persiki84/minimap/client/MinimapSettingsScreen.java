package com.persiki84.minimap.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MinimapSettingsScreen extends Screen {
    private final Screen parent;

    public MinimapSettingsScreen(Screen parent) {
        super(Component.translatable("minimap.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.addRenderableWidget(Button.builder(
            Component.translatable("minimap.settings.minimap", Component.translatable(ClientMapData.enableMinimap ? "minimap.toggle.on" : "minimap.toggle.off")),
            button -> {
                ClientMapData.enableMinimap = !ClientMapData.enableMinimap;
                button.setMessage(Component.translatable("minimap.settings.minimap", Component.translatable(ClientMapData.enableMinimap ? "minimap.toggle.on" : "minimap.toggle.off")));
            }
        ).bounds(centerX - 100, centerY - 80, 200, 20).build());

        this.addRenderableWidget(Button.builder(
            Component.translatable("minimap.settings.other_markers", Component.translatable(ClientMapData.showOtherMarkers ? "minimap.toggle.on" : "minimap.toggle.off")),
            button -> {
                ClientMapData.showOtherMarkers = !ClientMapData.showOtherMarkers;
                button.setMessage(Component.translatable("minimap.settings.other_markers", Component.translatable(ClientMapData.showOtherMarkers ? "minimap.toggle.on" : "minimap.toggle.off")));
            }
        ).bounds(centerX - 100, centerY - 50, 200, 20).build());

        this.addRenderableWidget(new AbstractSliderButton(centerX - 100, centerY - 20, 200, 20, Component.translatable("minimap.settings.size", ClientMapData.minimapSize), ClientMapData.minimapSize / 300.0) {
            @Override
            protected void updateMessage() {
                this.setMessage(Component.translatable("minimap.settings.size", ClientMapData.minimapSize));
            }

            @Override
            protected void applyValue() {
                ClientMapData.minimapSize = (int) (50 + this.value * 250);
            }
        });

        this.addRenderableWidget(new AbstractSliderButton(centerX - 100, centerY + 10, 200, 20, Component.translatable("minimap.settings.zoom", String.format("%.1f", ClientMapData.minimapZoom)), ClientMapData.minimapZoom / 5.0) {
            @Override
            protected void updateMessage() {
                this.setMessage(Component.translatable("minimap.settings.zoom", String.format("%.1f", ClientMapData.minimapZoom)));
            }

            @Override
            protected void applyValue() {
                ClientMapData.minimapZoom = (float) (0.5 + this.value * 4.5);
            }
        });

        this.addRenderableWidget(Button.builder(Component.translatable("minimap.button.close"), b -> this.onClose())
                .bounds(centerX - 100, centerY + 50, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}
