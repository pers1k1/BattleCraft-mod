package com.persiki84.battlecraft.client.menu.browse;

import com.mojang.logging.LogUtils;
import com.persiki84.shared.client.ui.UiButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelStorageException;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class WorldBrowseScreen extends BrowseScreen {
    private static final int FOOTER_WIDTH = 96;
    private static final int FOOTER_GAP = 6;

    private final List<LevelSummary> summaries = new ArrayList<>();
    private CompletableFuture<List<LevelSummary>> pending;
    private List<LevelSummary> shown;

    public WorldBrowseScreen(Screen parent) {
        super(Component.translatable("battlecraft.worlds.title"), parent);
    }

    @Override
    protected void init() {
        if (pending == null) pending = load();
        super.init();
    }

    @Override
    public void tick() {
        List<LevelSummary> arrived = poll();
        if (arrived == shown) return;

        shown = arrived;
        summaries.clear();
        if (arrived != null) summaries.addAll(arrived);
        refill();
    }

    private List<LevelSummary> poll() {
        try {
            return pending.getNow(null);
        } catch (CancellationException | CompletionException error) {
            return null;
        }
    }

    private CompletableFuture<List<LevelSummary>> load() {
        LevelStorageSource source = Minecraft.getInstance().getLevelSource();
        try {
            LevelStorageSource.LevelCandidates found = source.findLevelCandidates();
            if (found.isEmpty()) return CompletableFuture.completedFuture(List.of());
            return source.loadLevelSummaries(found).exceptionally(error -> List.of());
        } catch (LevelStorageException error) {
            LogUtils.getLogger().error("[battlecraft] список миров не прочитан", error);
            return CompletableFuture.completedFuture(List.of());
        }
    }

    private void reload() {
        pending = load();
        shown = null;
    }

    @Override
    protected void fill(String filter) {
        String needle = filter.toLowerCase(Locale.ROOT);
        for (LevelSummary summary : summaries) {
            if (!matches(summary, needle)) continue;
            cards.add(new WorldCard(this, summary));
        }
    }

    private static boolean matches(LevelSummary summary, String needle) {
        return summary.getLevelName().toLowerCase(Locale.ROOT).contains(needle)
                || summary.getLevelId().toLowerCase(Locale.ROOT).contains(needle);
    }

    private LevelSummary chosen() {
        return picked() instanceof WorldCard card ? card.summary() : null;
    }

    @Override
    public void enter() {
        LevelSummary summary = chosen();
        if (summary != null) WorldActions.join(this, summary);
    }

    @Override
    protected void buildActions(int left, int top, int width) {
        boolean ready = chosen() != null;
        UiButton play = action(left, top, width,
                Component.translatable("battlecraft.worlds.play"), this::enter);
        play.active = ready;
        addRenderableWidget(play);

        int small = (width - ACTION_GAP * 2) / 3;
        int y = top + ACTION_HEIGHT + ACTION_GAP;
        addSmall(left, y, small, "battlecraft.worlds.edit", ready, this::editChosen);
        addSmall(left + small + ACTION_GAP, y, small, "battlecraft.worlds.recreate", ready,
                this::recreateChosen);
        addSmall(left + (small + ACTION_GAP) * 2, y, small, "battlecraft.worlds.delete", ready,
                this::deleteChosen);
    }

    private void addSmall(int x, int y, int width, String label, boolean ready, Runnable run) {
        UiButton button = action(x, y, width, Component.translatable(label), run);
        button.active = ready;
        addRenderableWidget(button);
    }

    private void editChosen() {
        LevelSummary summary = chosen();
        if (summary != null) WorldActions.edit(this, summary, this::reload);
    }

    private void recreateChosen() {
        LevelSummary summary = chosen();
        if (summary != null) WorldActions.recreate(this, summary);
    }

    private void deleteChosen() {
        LevelSummary summary = chosen();
        if (summary != null) WorldActions.delete(this, summary, this::reload);
    }

    @Override
    protected void buildFooter(int centerX, int y) {
        int left = centerX - FOOTER_WIDTH - FOOTER_GAP / 2;
        addRenderableWidget(action(left, y, FOOTER_WIDTH,
                Component.translatable("battlecraft.worlds.create"), this::create));
        addRenderableWidget(action(centerX + FOOTER_GAP / 2, y, FOOTER_WIDTH,
                Component.translatable("battlecraft.menu.back"), this::leave));
    }

    private void create() {
        CreateWorldScreen.openFresh(Minecraft.getInstance(), this);
    }

    @Override
    protected Component emptyHint() {
        return Component.translatable(shown == null
                ? "battlecraft.worlds.loading" : "battlecraft.worlds.empty");
    }

    @Override
    protected Component searchHint() {
        return Component.translatable("battlecraft.worlds.search");
    }

    @Override
    protected void paintPreview(GuiGraphics graphics, int left, int top, int width, int height) {
        LevelSummary summary = chosen();
        if (summary == null) {
            PreviewColumn.nothing(graphics, this.font, left, top, width, height);
            return;
        }
        PreviewColumn column = new PreviewColumn(graphics, this.font, left, top, width, height);
        column.head(picked(), Component.literal(summary.getLevelName()));
        column.fact("battlecraft.worlds.folder", Component.literal(summary.getLevelId()));
        column.fact("battlecraft.worlds.mode",
                Component.translatable("selectWorld.gameMode." + summary.getGameMode().getName()));
        column.fact("battlecraft.worlds.version", summary.getWorldVersionName());
        column.fact("battlecraft.worlds.played", played(summary));
    }

    private static Component played(LevelSummary summary) {
        long stamp = summary.getLastPlayed();
        return stamp == -1L ? Component.translatable("battlecraft.worlds.never")
                : Component.literal(WorldCard.stamp(stamp));
    }
}
