package com.persiki84.battlecraft.client.menu.browse;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiRender;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

import java.util.Arrays;

public final class ServerCard extends BrowseCard {
    private static final float COUNT_SCALE = 0.68f;
    private static final float COUNT_TOP = 5.0f;
    private static final float BARS_BOTTOM = 8.0f;
    private static final float TRAILING = 34.0f;

    private final ServerBrowseScreen screen;
    private final ServerData data;
    private final FaviconTexture icon;
    private final boolean lan;
    private byte[] shownIcon;
    private boolean asked;

    public ServerCard(ServerBrowseScreen screen, ServerData data, boolean lan) {
        super(screen, BrowseScreen.LIST_WIDTH, BrowseScreen.CARD_HEIGHT, Component.literal(data.name));
        this.screen = screen;
        this.data = data;
        this.lan = lan;
        this.icon = FaviconTexture.forServer(Minecraft.getInstance().getTextureManager(), data.ip);
    }

    public ServerData data() {
        return data;
    }

    public boolean lan() {
        return lan;
    }

    public boolean compatible() {
        return data.protocol == SharedConstants.getCurrentVersion().getProtocolVersion();
    }

    public boolean answered() {
        return data.pinged && data.ping != -2L;
    }

    @Override
    public String identity() {
        return (lan ? "lan:" : "saved:") + data.ip;
    }

    @Override
    protected Component titleLine() {
        return Component.literal(data.name);
    }

    @Override
    protected Component noteLine() {
        if (data.motd != null && !data.motd.getString().isEmpty()) return data.motd;
        return Component.literal(data.ip);
    }

    @Override
    protected float trailingWidth() {
        return TRAILING;
    }

    @Override
    protected void paintIcon(GuiGraphics graphics, float x, float y, float size) {
        askPing();
        refreshIcon();
        blitSquare(graphics, icon.textureLocation(), x, y, size);
    }

    @Override
    protected void paintTrailing(GuiGraphics graphics, float rightX, float focus) {
        UiRender.textRight(graphics, font(), players(), rightX, getY() + COUNT_TOP,
                COUNT_SCALE, UiAccent.textDim(), false);
        PingBars.paint(graphics, rightX, getY() + height - BARS_BOTTOM,
                compatible() ? PingBars.level(data.ping) : 0, !answered());
    }

    private Component players() {
        if (!compatible()) return Component.translatable("battlecraft.servers.incompatible");
        if (!answered()) return Component.empty();
        if (data.ping < 0L) return Component.translatable("battlecraft.servers.offline");
        if (data.players == null) return Component.empty();
        return Component.literal(data.players.online() + "/" + data.players.max());
    }

    private void askPing() {
        if (asked || data.pinged) return;
        asked = true;
        screen.ping(data);
    }

    private void refreshIcon() {
        byte[] bytes = data.getIconBytes();
        if (Arrays.equals(bytes, shownIcon)) return;

        shownIcon = bytes;
        if (bytes == null) {
            icon.clear();
            return;
        }
        try {
            icon.upload(NativeImage.read(bytes));
        } catch (Throwable error) {
            LogUtils.getLogger().warn("[battlecraft] значок сервера {} не прочитан", data.ip);
            data.setIconBytes(null);
            icon.clear();
        }
    }

    @Override
    public void close() {
        icon.close();
    }
}
