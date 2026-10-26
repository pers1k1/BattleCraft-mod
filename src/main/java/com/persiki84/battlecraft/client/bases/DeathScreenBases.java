package com.persiki84.battlecraft.client.bases;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.bases.S2CBasesPacket;
import com.persiki84.shared.client.ui.UiButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

// WHY: место возрождения выбирается прямо на экране смерти: кнопка на каждую свою точку-базу, а
// WHY: ванильная «Возродиться» ведёт домой. Кнопки оживают вместе с ванильной, через ту же задержку.
// WHY: Новый список баз пересобирает экран: при входе мёртвым экран смерти открывается раньше списка
@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class DeathScreenBases {
    private static final String RESPAWN_KEY = "deathScreen.respawn";
    private static final int WIDTH = 200;
    private static final int HEIGHT = 20;
    private static final int STEP = 24;
    private static final int COLUMN_GAP = 4;
    private static final int MAX_COLUMNS = 3;
    private static final int BELOW_VANILLA = 120;
    private static final int BOTTOM_MARGIN = 4;

    private static final List<UiButton> shown = new ArrayList<>();
    private static AbstractWidget respawn;
    private static boolean pressed;
    private static int builtVersion;

    private DeathScreenBases() {}

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof DeathScreen screen)) return;
        shown.clear();
        pressed = false;
        builtVersion = ClientBases.respawnVersion();
        respawn = find(screen);
        if (respawn == null || !ClientBases.respawn()) return;
        List<S2CBasesPacket.Entry> points = pointBases();
        int top = screen.height / 4 + BELOW_VANILLA;
        int rows = Math.max(1, (screen.height - BOTTOM_MARGIN - top + STEP - HEIGHT) / STEP);
        int columns = Math.min(MAX_COLUMNS, (points.size() + rows - 1) / rows);
        int width = (WIDTH - COLUMN_GAP * (columns - 1)) / Math.max(1, columns);
        int count = Math.min(points.size(), rows * columns);
        for (int index = 0; index < count; index++) {
            int x = screen.width / 2 - WIDTH / 2 + index % columns * (width + COLUMN_GAP);
            int y = top + index / columns * STEP;
            event.addListener(button(points.get(index), x, y, width));
        }
    }

    private static List<S2CBasesPacket.Entry> pointBases() {
        List<S2CBasesPacket.Entry> points = new ArrayList<>();
        for (S2CBasesPacket.Entry base : ClientBases.bases()) {
            if (!base.home()) points.add(base);
        }
        return points;
    }

    private static UiButton button(S2CBasesPacket.Entry base, int x, int y, int width) {
        UiButton button = new UiButton(x, y, width, HEIGHT,
                Component.translatable("battlecraft.bases.respawn_at", base.name()), clicked -> respawnAt(base.id()));
        button.active = false;
        shown.add(button);
        return button;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof DeathScreen screen)) {
            shown.clear();
            return;
        }
        if (ClientBases.respawnVersion() != builtVersion && !pressed) {
            screen.resize(minecraft, screen.width, screen.height);
            return;
        }
        boolean ready = respawn != null && respawn.active && !pressed;
        for (int index = 0; index < shown.size(); index++) {
            shown.get(index).active = ready;
        }
    }

    // WHY: заявка уходит раньше пакета возрождения, и сервер разбирает их по порядку прихода
    private static void respawnAt(String id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        pressed = true;
        if (respawn != null) respawn.active = false;
        ClientBases.requestSpawn(id);
        minecraft.player.respawn();
    }

    private static AbstractWidget find(DeathScreen screen) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.getMessage().getContents() instanceof TranslatableContents
                    contents && RESPAWN_KEY.equals(contents.getKey())) {
                return widget;
            }
        }
        return null;
    }
}
