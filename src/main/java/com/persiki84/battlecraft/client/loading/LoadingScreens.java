package com.persiki84.battlecraft.client.loading;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.battlecraft.client.menu.MenuBackground;
import com.persiki84.battlecraft.mixin.ConnectScreenAccessor;
import com.persiki84.battlecraft.mixin.LevelLoadingScreenAccessor;
import com.persiki84.battlecraft.mixin.ProgressScreenAccessor;
import com.persiki84.shared.client.ui.UiBackdrop;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class LoadingScreens {
    private static final float PERCENT_SPAN = 100.0f;
    private static final float MOSAIC_GAP = 14.0f;
    private static final float BUTTON_GAP = 18.0f;

    private LoadingScreens() {}

    public static boolean handles(Screen screen) {
        if (!HudConfig.loadingScreens()) return false;

        return screen instanceof ConnectScreen
                || screen instanceof ReceivingLevelScreen
                || screen instanceof LevelLoadingScreen
                || screen instanceof ProgressScreen
                || screen instanceof GenericDirtMessageScreen;
    }

    // WHY: низкий приоритет: среда экрана поднимается обработчиком ScreenRestyle на обычном
    // WHY: приоритете, и наши виджеты должны рисоваться уже одетыми
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderPre(ScreenEvent.Render.Pre event) {
        Screen screen = event.getScreen();
        if (event.isCanceled() || !handles(screen)) return;

        event.setCanceled(true);
        paint(screen, event.getGuiGraphics(), event.getMouseX(), event.getMouseY(),
                event.getPartialTick());
    }

    private static void paint(Screen screen, GuiGraphics graphics, int mouseX, int mouseY,
                              float partialTick) {
        int width = screen.width;
        int height = screen.height;
        LoadingClock.advance();
        float seconds = LoadingClock.seconds();

        boolean crowded = screen instanceof LevelLoadingScreen;
        MenuBackground.shared().render(graphics, width, height);
        graphics.flush();
        UiBackdrop.restage();
        float taken = LoadingArt.paint(graphics, width, height, note(screen), progress(screen),
                seconds, LoadingClock.age(screen), crowded);
        if (crowded) middle((LevelLoadingScreen) screen, graphics, width, height, taken + MOSAIC_GAP);
        widgets(screen, graphics, mouseX, mouseY, partialTick, crowded ? height : taken);
    }

    // WHY: мозаика центруется в остатке кадра под блоком метки, а не по доле высоты: блок
    // WHY: растёт вместе с подписью, и постоянная доля наезжала бы на неё в низком окне
    private static void middle(LevelLoadingScreen screen, GuiGraphics graphics, int width,
                              int height, float taken) {
        float room = height - taken;
        if (room <= 0.0f) return;

        ChunkMosaic.paint(graphics, ((LevelLoadingScreenAccessor) screen).battlecraft$progressListener(),
                width / 2.0f, taken + room / 2.0f, width, Math.round(room));
    }

    // WHY: ванильная кнопка отмены стоит по своей доле высоты и попадает ровно в метку, потому
    // WHY: что наш блок центрован иначе; ставим её под подписью, а на экране с мозаикой - снизу
    private static void widgets(Screen screen, GuiGraphics graphics, int mouseX, int mouseY,
                                float partialTick, float taken) {
        float y = taken + BUTTON_GAP;
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof Renderable renderable)) continue;

            if (child instanceof AbstractWidget widget) {
                widget.setY(Math.round(stacked(y, widget, screen.height)));
                y += widget.getHeight() + BUTTON_GAP;
            }
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private static float stacked(float y, AbstractWidget widget, int height) {
        float floor = height - widget.getHeight() - BUTTON_GAP;
        return Math.min(y, floor);
    }

    private static Component note(Screen screen) {
        if (screen instanceof ConnectScreen connecting) {
            return ((ConnectScreenAccessor) connecting).battlecraft$status();
        }
        if (screen instanceof ReceivingLevelScreen) {
            return Component.translatable("battlecraft.loading.terrain");
        }
        if (screen instanceof LevelLoadingScreen) {
            return Component.translatable("battlecraft.loading.world");
        }
        if (screen instanceof ProgressScreen working) return stage(working);
        return screen.getTitle();
    }

    private static Component stage(ProgressScreen screen) {
        ProgressScreenAccessor reading = (ProgressScreenAccessor) screen;
        Component stage = reading.battlecraft$stage();
        if (stage != null) return stage;

        Component header = reading.battlecraft$header();
        return header != null ? header : Component.translatable("battlecraft.loading.working");
    }

    private static float progress(Screen screen) {
        if (screen instanceof LevelLoadingScreen loading) {
            return ((LevelLoadingScreenAccessor) loading).battlecraft$progressListener().getProgress()
                    / PERCENT_SPAN;
        }
        if (screen instanceof ProgressScreen working) {
            int done = ((ProgressScreenAccessor) working).battlecraft$progress();
            return done <= 0 ? LoadingArt.UNKNOWN : done / PERCENT_SPAN;
        }
        return LoadingArt.UNKNOWN;
    }
}
