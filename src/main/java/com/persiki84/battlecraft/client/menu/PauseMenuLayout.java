package com.persiki84.battlecraft.client.menu;

import com.persiki84.battlecraft.client.menu.custom.PaletteButton;
import com.persiki84.battlecraft.client.menu.custom.CustomizeScreen;
import com.persiki84.battlecraft.BattleCraftManager;
import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.client.ClientGameData;
import com.persiki84.battlecraft.client.ServerRules;
import com.persiki84.shared.client.ui.UiButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class PauseMenuLayout {
    private static final int CORNER_MARGIN = 12;
    private static final String MODS_KEY = "fml.menu.mods";
    private static final String ADVANCEMENTS_KEY = "gui.advancements";
    private static final List<String> LEAVE_KEYS = List.of("menu.returnToMenu", "menu.disconnect");

    private PauseMenuLayout() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen screen)) return;

        AbstractWidget mods = find(screen, MODS_KEY);
        AbstractWidget leave = find(screen, LEAVE_KEYS);
        if (mods == null || leave == null) return;

        int matchX = leave.getX();
        int matchY = leave.getY();
        int matchWidth = leave.getWidth();
        int matchHeight = leave.getHeight();

        event.removeListener(mods);
        leave.setPosition(mods.getX(), mods.getY());
        blockAdvancements(screen);

        UiButton match = matchButton(screen, matchX, matchY, matchWidth, matchHeight);
        if (match != null) event.addListener(match);

        event.addListener(new PaletteButton(CORNER_MARGIN,
                screen.height - CORNER_MARGIN - PaletteButton.SIZE, button -> CustomizeScreen.open()));
    }

    private static UiButton matchButton(PauseScreen screen, int x, int y, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ClientGameData.isSoftDisabled()) return null;

        if (ClientGameData.getCurrentPhase() == BattleCraftManager.GamePhase.LOBBY) {
            return new UiButton(x, y, width, height, Component.translatable("battlecraft.button.team_select"),
                    pressed -> {
                        screen.onClose();
                        TeamSelectScreen.open();
                    });
        }
        if (ClientGameData.getCurrentPhase() != BattleCraftManager.GamePhase.ACTIVE
                || mc.player.getTeam() == null) {
            return null;
        }
        return new UiButton(x, y, width, height, Component.translatable("battlecraft.button.surrender"),
                pressed -> {
                    mc.player.connection.sendCommand("battlecraft surrender start");
                    screen.onClose();
                });
    }

    private static void blockAdvancements(PauseScreen screen) {
        if (!ServerRules.restricted()) return;

        AbstractWidget advancements = find(screen, ADVANCEMENTS_KEY);
        if (advancements != null) advancements.active = false;
    }

    private static AbstractWidget find(PauseScreen screen, String key) {
        return find(screen, List.of(key));
    }

    private static AbstractWidget find(PauseScreen screen, List<String> keys) {
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof AbstractWidget widget)) continue;
            if (widget.getMessage().getContents() instanceof TranslatableContents contents
                    && keys.contains(contents.getKey())) {
                return widget;
            }
        }
        return null;
    }
}
