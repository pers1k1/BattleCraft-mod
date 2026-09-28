package com.persiki84.battlecraft.client.menu.desktop;

import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.battlecraft.client.wallpaper.WallpaperLibrary;
import com.persiki84.battlecraft.client.wallpaper.WallpaperPlayer;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAmbience;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiBoot;
import com.persiki84.shared.client.ui.UiDots;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Objects;

public final class DesktopWallpaper {
    private static final float DOTTED_SWAP_SECONDS = 1.1f;
    // WHY: macOS меняет обои плавным наплывом примерно за 0.7 секунды, без сдвигов и масштаба
    private static final float PLAIN_SWAP_SECONDS = 0.7f;
    private static final float ASSEMBLE_SECONDS = 1.5f;
    private static final float THEME_WHITEN = 0.6f;
    private static final float THEME_GROUND = 0.035f;
    private static final int MONO_INK = 0xFFF1F1F4;
    private static final int MONO_GROUND = 0xFF030304;
    private static final long RECHECK_MS = 2000L;
    // WHY: сон подобран стендом: фон слегка расфокусирован и гаснет до 0.4 яркости, картина ещё
    // WHY: читается, но уже не спорит с тем, что лежит поверх
    private static final float SLEEP_HAZE = 0.35f;
    private static final float SLEEP_DIM = 0.6f;
    // WHY: битый кеш или неотображённая текстура не отдадут кадр никогда, и стол не ждёт их вечно
    private static final long LOAD_LIMIT_MS = 3000L;
    // WHY: кольцо видеообоев 4K держит около 130 МБ видеопамяти и потоки декода. В мире стол не
    // WHY: виден, и через десять секунд без показа проигрыватель гасится; короткие заходы в мир
    // WHY: и экраны загрузки между измерениями его не трогают
    private static final long REST_AFTER_MS = 10_000L;
    // WHY: при возврате первый кадр ждётся в рендер-потоке: кадр 4K декодируется десятки
    // WHY: миллисекунд, а переход из мира и так идёт через экран сохранения. Предел не даёт
    // WHY: зависнуть на битом кеше, тогда кадр доедет сам, как при первом показе
    private static final long WAKE_LIMIT_NANOS = 250_000_000L;

    private static final DesktopWallpaper SHARED = new DesktopWallpaper();

    private enum Step {
        STAY,
        HOLD,
        SWAP
    }

    private final UiDots.Scene scene = new UiDots.Scene();
    private boolean applied;
    private boolean holding;
    private boolean reloading;
    private String shownId = "";
    private boolean shownDots = true;
    private boolean shownThemed;
    private float swap = 1.0f;
    private float swapSeconds = DOTTED_SWAP_SECONDS;
    private float assemble;
    private boolean assembledOnce;
    private float sleep;
    private long stamp = -1L;
    private String checkedId = "";
    private boolean checkedExists;
    private long checkedAt;
    private String waitingFor;
    private long waitingSince;
    private long paintedAt = System.currentTimeMillis();
    private boolean resting;

    private DesktopWallpaper() {}

    public static DesktopWallpaper shared() {
        return SHARED;
    }

    // WHY: обои собираются из точек один раз за запуск игры: возврат в меню из мира или из
    // WHY: настроек продолжает ту же сцену, а не собирает её заново
    public void assembleOnce() {
        if (assembledOnce) return;
        assembledOnce = true;
        assemble = 0.0f;
    }

    public void sleep(float amount) {
        sleep = UiAnim.clamp01(amount);
    }

    public boolean shows(String id) {
        return applied && shownId.equals(id);
    }

    public boolean render(GuiGraphics graphics, int width, int height, float pointerX, float pointerY) {
        advance();
        paintedAt = System.currentTimeMillis();
        wake();
        String wantedId = wantedId();
        if (!applied && ready(wantedId)) {
            adopt(wantedId);
            applied = true;
        }
        Step step = applied ? step(wantedId) : Step.STAY;
        compose(pointerX, pointerY);
        if (!UiDots.paint(graphics, width, height, scene)) return false;

        finish(step, wantedId);
        return true;
    }

    public void restIfAway(boolean inWorld) {
        if (resting || !inWorld || !applied || holding || shownId.isEmpty()) return;
        if (System.currentTimeMillis() - paintedAt < REST_AFTER_MS) return;
        WallpaperPlayer.hide();
        resting = true;
    }

    // WHY: проигрыватель поднимается до первого рисования сцены: без этого стол на кадр-другой
    // WHY: вставал бы на пустой фон, пока фоновый поток читает первый кадр
    private void wake() {
        if (!resting) return;
        resting = false;
        WallpaperPlayer.show(shownId);
        WallpaperPlayer.awaitFrame(WAKE_LIMIT_NANOS);
    }

    // WHY: под тающей плашкой загрузки Mojang стол уже рисуется, но сборка ждёт её ухода, как и
    // WHY: вступление меню: иначе готовая картина проступала под плашкой, а с первым кадром меню
    // WHY: гасла и собиралась заново. Стол, открытый не с титульного экрана, собирается сам
    private void advance() {
        UiAmbience.advance();
        long frame = UiFrame.frame();
        if (frame == stamp) return;
        stamp = frame;
        float delta = UiFrame.delta();
        swap = Math.min(1.0f, swap + delta / swapSeconds);
        if (!applied || UiBoot.loading()) return;
        assembleOnce();
        assemble = Math.min(1.0f, assemble + delta / ASSEMBLE_SECONDS);
    }

    // WHY: проигрыватель своих обоев один, и запуск новых гасит старые: прежде чем их грузить,
    // WHY: снимается кадр со старыми, и пока новые не отдали первый кадр, на столе стоит этот снимок.
    // WHY: Так же, через снимок, заново открываются обои, пересобранные прямо под показом
    private Step step(String wantedId) {
        reloading = !holding && !shownId.isEmpty() && WallpaperPlayer.outdated();
        if (!holding && !reloading && !differs(wantedId)) return Step.STAY;
        if (!holding && (reloading || replacesCustom(wantedId))) return Step.HOLD;
        return ready(wantedId) ? Step.SWAP : Step.STAY;
    }

    private boolean replacesCustom(String wantedId) {
        return !shownId.isEmpty() && !wantedId.isEmpty() && !shownId.equals(wantedId);
    }

    private void finish(Step step, String wantedId) {
        if (step == Step.HOLD) {
            hold();
            return;
        }
        if (step != Step.SWAP) return;

        if (!holding) scene.stale(UiDots.freeze());
        holding = false;
        swapSeconds = HudConfig.wallpaperDots() || wantedId.isEmpty() ? DOTTED_SWAP_SECONDS : PLAIN_SWAP_SECONDS;
        adopt(wantedId);
        swap = 0.0f;
    }

    private void hold() {
        scene.stale(UiDots.freeze());
        holding = true;
        if (reloading) WallpaperPlayer.hide();
        reloading = false;
    }

    private void compose(float pointerX, float pointerY) {
        dress(shownId);
        scene.progress(holding ? 0.0f : swap, applied ? assemble : 0.0f);
        scene.pointer(pointerX, pointerY);
        scene.mood(UiAmbience.zoom(), Math.max(UiAmbience.haze(), sleep * SLEEP_HAZE), UiAmbience.veil(),
                sleep * SLEEP_DIM);
    }

    // WHY: наличие обоев читается с диска, поэтому выбранные проверяются при смене выбора и раз
    // WHY: в пару секунд, и только свои: вся библиотека это чтение каждого meta.json в рендер-потоке
    private String wantedId() {
        String id = HudConfig.wallpaper();
        if (id == null || id.isEmpty()) return "";
        long now = System.currentTimeMillis();
        if (!id.equals(checkedId) || now - checkedAt > RECHECK_MS) {
            checkedId = id;
            checkedAt = now;
            checkedExists = WallpaperLibrary.has(id);
        }
        return checkedExists ? id : "";
    }

    private boolean differs(String wantedId) {
        boolean dotsMatter = !wantedId.isEmpty() && shownDots != HudConfig.wallpaperDots();
        return !Objects.equals(shownId, wantedId) || dotsMatter || shownThemed != HudConfig.wallpaperThemed();
    }

    private boolean ready(String id) {
        if (sourceReady(id)) return true;
        long now = System.currentTimeMillis();
        if (!id.equals(waitingFor)) {
            waitingFor = id;
            waitingSince = now;
        }
        return now - waitingSince > LOAD_LIMIT_MS;
    }

    // WHY: смена ждёт, пока новые обои отдадут первый кадр: иначе между старыми и новыми
    // WHY: на секунду мелькнул бы пустой фон, пока фоновый поток читает кеш
    private static boolean sourceReady(String id) {
        if (id.isEmpty()) return true;
        WallpaperPlayer.show(id);
        return WallpaperPlayer.texture() != 0;
    }

    private void adopt(String id) {
        if (id.isEmpty()) WallpaperPlayer.hide();
        shownId = id;
        shownDots = HudConfig.wallpaperDots();
        shownThemed = HudConfig.wallpaperThemed();
        waitingFor = null;
    }

    private void dress(String id) {
        boolean custom = !id.isEmpty() && !holding;
        int texture = custom ? WallpaperPlayer.texture() : 0;
        scene.picture(texture, custom ? WallpaperPlayer.width() : 0.0f, custom ? WallpaperPlayer.height() : 0.0f);
        boolean dots = !holding && (id.isEmpty() || shownDots);
        if (shownThemed) {
            int accent = UiAccent.color();
            scene.look(dots, accent, THEME_WHITEN, UiTheme.mix(0xFF000000, accent, THEME_GROUND));
            return;
        }
        scene.look(dots, MONO_INK, 0.0f, MONO_GROUND);
    }
}
