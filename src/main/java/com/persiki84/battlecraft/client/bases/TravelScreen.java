package com.persiki84.battlecraft.client.bases;

import com.persiki84.battlecraft.bases.BaseNetwork;
import com.persiki84.battlecraft.bases.S2CBasesPacket;
import com.persiki84.shared.client.menu.GlassScreen;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiBackdrop;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiGlassStyle;
import com.persiki84.shared.client.ui.UiRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

// WHY: окно открывается из магазина базы или клавишей на точке-базе без магазина. Выбор базы сразу
// WHY: закрывает окна: канал срывается от движения, и стоять игрок должен в мире, а не в меню.
// WHY: Список снимается на сборке и пересобирается, когда сервер прислал новый
public final class TravelScreen extends GlassScreen {
    private static final int PANEL_WIDTH = 232;
    private static final int ROW_WIDTH = 200;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 6;
    private static final float TOP_PAD = 40.0f;
    private static final float BOTTOM_PAD = 16.0f;
    private static final float FIT_MARGIN = 8.0f;
    private static final float HINT_SCALE = 0.75f;
    private static final int BACKDROP = 0xC00A0A0C;
    private static final Component HINT = Component.translatable("battlecraft.bases.travel.hint");

    private final List<S2CBasesPacket.Entry> targets = new ArrayList<>();
    @Nullable
    private Screen parent;
    private int placed;
    private int shownBases;

    private TravelScreen(@Nullable Screen parent) {
        super(Component.translatable("battlecraft.bases.travel.title"));
        this.parent = parent;
    }

    public static void open(@Nullable Screen parent) {
        Minecraft.getInstance().setScreen(new TravelScreen(parent));
    }

    @Override
    protected void init() {
        placed = 0;
        shownBases = ClientBases.version();
        collectTargets();
        for (S2CBasesPacket.Entry base : targets) {
            this.addRenderableWidget(new UiButton(left(), nextTop(), ROW_WIDTH, ROW_HEIGHT, base.name(),
                    pressed -> travel(base.id())));
        }
        this.addRenderableWidget(new UiButton(left(), nextTop(), ROW_WIDTH, ROW_HEIGHT,
                Component.translatable("gui.back"), pressed -> this.onClose()));
    }

    // WHY: базу, на которой стоит игрок, и перелёт из дома в дом сервер отклонит, поэтому их нет в списке
    private void collectTargets() {
        targets.clear();
        String here = ClientBases.currentBaseId();
        boolean fromHome = here == null || here.startsWith(BaseNetwork.HOME);
        for (S2CBasesPacket.Entry base : ClientBases.bases()) {
            if (base.id().equals(here) || fromHome && base.home()) continue;
            targets.add(base);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (ClientBases.version() != shownBases) this.rebuildWidgets();
    }

    private void travel(String id) {
        ClientBases.requestTravel(id);
        parent = null;
        this.onClose();
    }

    private int left() {
        return this.width / 2 - ROW_WIDTH / 2;
    }

    private int nextTop() {
        return (int) (panelTop() + TOP_PAD) + placed++ * (ROW_HEIGHT + ROW_GAP);
    }

    private float panelHeight() {
        int rows = targets.size() + 1;
        return rows * (ROW_HEIGHT + ROW_GAP) - ROW_GAP + TOP_PAD + BOTTOM_PAD;
    }

    private float panelTop() {
        return (this.height - panelHeight()) / 2.0f;
    }

    // WHY: панель стоит по центру, и масштаб идёт от центра экрана, поэтому длинный список вписывается
    // WHY: в высоту простым отношением
    @Override
    protected float contentScale() {
        float needed = panelHeight() + FIT_MARGIN * 2.0f;
        return needed <= this.height ? 1.0f : this.height / needed;
    }

    @Override
    protected void renderBackdrop(GuiGraphics graphics) {
        UiRender.rect(graphics, 0, 0, this.width, this.height, BACKDROP);
        graphics.flush();
        UiBackdrop.restage();
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float panelX = (this.width - PANEL_WIDTH) / 2.0f;
        float panelY = panelTop();
        UiGlass.sheet(graphics, panelX, panelY, PANEL_WIDTH, panelHeight(), UiGlassStyle.radiusPanel() + 3.0f, 1.0f);
        UiGlass.layer(graphics);
        UiRender.textCentered(graphics, this.font, this.title, this.width / 2.0f, panelY + 8.0f, 1.0f,
                UiAccent.text(), false);
        UiRender.textCentered(graphics, this.font, HINT, this.width / 2.0f, panelY + 22.0f, HINT_SCALE,
                UiAccent.textDim(), false);
        renderWidgets(graphics, mouseX, mouseY, partialTick);
        MenuFeedback.render(graphics, this.width / 2.0f, panelY + panelHeight() + ROW_GAP);
    }

    @Override
    protected void closing() {
        if (parent instanceof GlassScreen glass) glass.reenter();
        this.minecraft.setScreen(parent);
    }
}
