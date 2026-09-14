package com.persiki84.battlecraft.client.loading;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.persiki84.battlecraft.client.hud.HudConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraftforge.client.ForgeHooksClient;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.function.Consumer;

// WHY: наследование от LoadingOverlay обязательно: Minecraft дважды спрашивает
// WHY: overlay instanceof LoadingOverlay, решая, можно ли начинать перезагрузку ресурсов.
// WHY: Сам родитель никогда не работает, кадр целиком отдаётся вложенному оверлею
public final class BootOverlay extends LoadingOverlay {
    private static final Consumer<Optional<Throwable>> IDLE = error -> {};
    private static final float HOLD_SECONDS = 1.0f;
    private static final float FADE_SECONDS = 1.0f;
    private static final float SMOOTHING = 0.9f;
    private static final float COVERED = 1.0f;

    private final Minecraft client;
    private final Overlay inner;
    private final ReloadInstance watched;
    private float shownProgress;
    private float doneAt = -1.0f;

    private static boolean spent;
    private static boolean driving;

    private BootOverlay(Minecraft client, Overlay inner, ReloadInstance watched) {
        super(client, watched, IDLE, false);
        this.client = client;
        this.inner = inner;
        this.watched = watched;
    }

    // WHY: подменяется только самый первый оверлей запуска: перезагрузка ресурсов из настроек
    // WHY: поднимает точно такой же, и накрывать её кадром запуска игры незачем
    public static Overlay claim(Minecraft client, Overlay overlay) {
        if (spent || !HudConfig.bootScreen()) return overlay;
        if (overlay == null) {
            spent = true;
            return null;
        }
        if (!(overlay instanceof LoadingOverlay) || overlay instanceof BootOverlay) return overlay;

        ReloadInstance watched = reloadOf(overlay);
        if (watched == null) {
            spent = true;
            LogUtils.getLogger().warn("[battlecraft] экран запуска пропущен: не найден ход загрузки");
            return overlay;
        }
        return new BootOverlay(client, overlay, watched);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drive(graphics, mouseX, mouseY, partialTick);
        LoadingClock.advance();
        advance();
        try {
            cover(graphics, mouseX, mouseY, partialTick);
        } catch (Throwable error) {
            surrender(error);
        }
    }

    private void cover(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float shown = shown();
        BootPaint.open();
        try {
            if (shown < COVERED) understudy(graphics, mouseX, mouseY, partialTick);
            BootPaint.paint(graphics, this, shownProgress, shown);
        } finally {
            BootPaint.close(graphics);
        }
    }

    // WHY: кадр запуска идёт раньше любой нашей проверки, и сбой в нём убивал бы вход в игру:
    // WHY: при первой же ошибке оверлей отдаётся обратно загрузчику и игра доходит до меню
    private void surrender(Throwable error) {
        spent = true;
        LogUtils.getLogger().error("[battlecraft] экран запуска снят после сбоя", error);
        client.setOverlay(inner);
    }

    // WHY: ножницы нулевого размера гасят растеризацию вложенного оверлея, включая его glClear:
    // WHY: заставка загрузчика не тратит кадр и не проступает из-под нашего, а вся его логика
    // WHY: ожидания ресурсов, вызова onFinish и снятия себя отрабатывает как обычно
    private void drive(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.flush();
        RenderSystem.enableScissor(0, 0, 0, 0);
        driving = true;
        try {
            inner.render(graphics, mouseX, mouseY, partialTick);
            graphics.flush();
        } finally {
            driving = false;
            RenderSystem.disableScissor();
        }
    }

    // WHY: погашенный ножницами кадр загрузчика не должен поднимать события отрисовки экрана:
    // WHY: снимок подложки взялся бы из нулевой области, а сам экран рисуется следом по-настоящему
    public static boolean driving() {
        return driving;
    }

    // WHY: экран под оверлеем рисует только сам оверлей, а его мы погасили: на затухании кадр
    // WHY: обязан показать уже открытое главное меню, иначе он растворяется в мусор прошлого кадра.
    // WHY: путь через ForgeHooksClient, а не screen.render, чтобы кадр затухания собирался
    // WHY: с нашим оформлением экрана, как любой обычный кадр
    private void understudy(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Screen screen = client.screen;
        if (screen == null) return;

        ForgeHooksClient.drawScreen(screen, graphics, mouseX, mouseY, partialTick);
        graphics.flush();
    }

    @Override
    public boolean isPauseScreen() {
        return inner.isPauseScreen();
    }

    private void advance() {
        shownProgress = shownProgress * SMOOTHING + watched.getActualProgress() * (1.0f - SMOOTHING);
        if (doneAt < 0.0f && watched.isDone()) doneAt = LoadingClock.seconds();
    }

    // WHY: ваниль после готовности ещё секунду держит кадр непрозрачным и вторую гасит его,
    // WHY: и наш кадр идёт по тому же расписанию: оверлей снимает себя ровно на этом сроке
    private float shown() {
        if (doneAt < 0.0f) return COVERED;

        float waited = LoadingClock.seconds() - doneAt - HOLD_SECONDS;
        if (waited <= 0.0f) return COVERED;
        return Math.max(0.0f, 1.0f - waited / FADE_SECONDS);
    }

    private static ReloadInstance reloadOf(Overlay overlay) {
        for (Class<?> owner = overlay.getClass(); owner != null; owner = owner.getSuperclass()) {
            ReloadInstance found = fieldOf(owner, overlay);
            if (found != null) return found;
        }
        return null;
    }

    private static ReloadInstance fieldOf(Class<?> owner, Overlay overlay) {
        for (Field field : owner.getDeclaredFields()) {
            if (!ReloadInstance.class.isAssignableFrom(field.getType())) continue;

            try {
                field.setAccessible(true);
                return (ReloadInstance) field.get(overlay);
            } catch (Throwable error) {
                return null;
            }
        }
        return null;
    }
}
