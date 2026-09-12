package com.persiki84.battlecraft.client.menu.browse;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class WorldCard extends BrowseCard {
    private static final DateFormat STAMP = new SimpleDateFormat();
    private static final float LOCK_SIZE = 9.0f;
    private static final float LOCK_INSET = 6.0f;

    private final LevelSummary summary;
    private final FaviconTexture icon;

    public WorldCard(BrowseScreen owner, LevelSummary summary) {
        super(owner, BrowseScreen.LIST_WIDTH, BrowseScreen.CARD_HEIGHT,
                Component.literal(summary.getLevelName()));
        this.summary = summary;
        this.icon = FaviconTexture.forWorld(Minecraft.getInstance().getTextureManager(),
                summary.getLevelId());
        this.active = !summary.isDisabled();
        loadIcon();
    }

    public LevelSummary summary() {
        return summary;
    }

    public static String stamp(long millis) {
        return STAMP.format(new Date(millis));
    }

    @Override
    public String identity() {
        return summary.getLevelId();
    }

    @Override
    protected Component titleLine() {
        String name = summary.getLevelName();
        return Component.literal(name.isEmpty() ? summary.getLevelId() : name);
    }

    @Override
    protected Component noteLine() {
        long played = summary.getLastPlayed();
        if (played == -1L) return Component.literal(summary.getLevelId());
        return Component.literal(summary.getLevelId() + "  ·  " + stamp(played));
    }

    @Override
    protected void paintIcon(GuiGraphics graphics, float x, float y, float size) {
        blitSquare(graphics, icon.textureLocation(), x, y, size);
    }

    @Override
    protected float trailingWidth() {
        return warned() ? LOCK_SIZE + LOCK_INSET : 0.0f;
    }

    @Override
    protected void paintTrailing(GuiGraphics graphics, float rightX, float focus) {
        if (!warned()) return;

        UiRender.iconLock(graphics, rightX - LOCK_SIZE / 2.0f, getY() + height / 2.0f,
                LOCK_SIZE, true, UiAccent.faint());
    }

    private boolean warned() {
        return summary.isLocked() || summary.requiresManualConversion() || summary.isDisabled();
    }

    private void loadIcon() {
        Path file = summary.getIcon();
        if (file == null || !Files.isRegularFile(file)) {
            icon.clear();
            return;
        }
        try (InputStream stream = Files.newInputStream(file)) {
            icon.upload(NativeImage.read(stream));
        } catch (Throwable error) {
            LogUtils.getLogger().warn("[battlecraft] значок мира {} не прочитан",
                    summary.getLevelId());
            icon.clear();
        }
    }

    @Override
    public void close() {
        icon.close();
    }
}
