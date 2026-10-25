package com.persiki84.battlecraft.client.menu.rules;

import com.persiki84.battlecraft.client.menu.panel.mode.ModeView;
import com.persiki84.sellmod.client.ClientSellData;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.HeadingRow;
import com.persiki84.shared.client.menu.studio.StudioStack;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

// WHY: цифры режима для игрока. Строки создаются один раз, значения пересчитываются только на приходе
// WHY: снимка или смене режима, а в кадре строка отдаёт готовый текст
public final class RulesModeRows {
    private static final int ROW = 22;
    private static final int SECTION_GAP = 6;
    private static final String KEY = "rules.match.";

    private enum Field { TARGET, CAP, SUDDEN, POINTS, KILLS, VEHICLES, TRANSPORT, QUARRIES, MONEY, KIT }

    private final Component[] values = new Component[Field.values().length];
    private final List<AbstractWidget> rows = new ArrayList<>();
    private final List<Integer> gaps = new ArrayList<>();

    public RulesModeRows() {
        heading("group.win");
        reading("target", Field.TARGET);
        reading("cap", Field.CAP);
        reading("sudden", Field.SUDDEN);
        heading("group.score");
        reading("points", Field.POINTS);
        reading("kills", Field.KILLS);
        reading("vehicles", Field.VEHICLES);
        reading("transport", Field.TRANSPORT);
        heading("group.start");
        reading("quarries", Field.QUARRIES);
        reading("money", Field.MONEY);
        reading("kit", Field.KIT);
    }

    private void heading(String key) {
        gaps.add(rows.isEmpty() ? 0 : SECTION_GAP);
        rows.add(new HeadingRow(0, 0, 10, ROW, Component.translatable(KEY + key)));
    }

    private void reading(String key, Field field) {
        values[field.ordinal()] = Component.empty();
        ActionRow row = new ActionRow(0, 0, 10, ROW, Component.translatable(KEY + key), () -> values[field.ordinal()],
                () -> { });
        row.hint(KEY + key + ".hint");
        row.active = false;
        gaps.add(0);
        rows.add(row);
    }

    public void refresh(ModeView.Settings settings) {
        values[Field.TARGET.ordinal()] = numberOr(settings.scoreTarget(), "target.value", "target.off");
        values[Field.CAP.ordinal()] = numberOr(settings.capMinutes(), "cap.value", "cap.off");
        values[Field.SUDDEN.ordinal()] = sudden(settings)
                ? text("sudden.value", settings.suddenMinutes(), settings.suddenMargin()) : text("sudden.off");
        values[Field.POINTS.ordinal()] = numberOr(settings.pointScore(), "points.value", "score.off");
        values[Field.KILLS.ordinal()] = numberOr(settings.killScore(), "score.value", "score.off");
        values[Field.VEHICLES.ordinal()] = numberOr(settings.vehicleScore(), "score.value", "score.off");
        values[Field.TRANSPORT.ordinal()] = numberOr(settings.transportScore(), "score.value", "score.off");
        values[Field.QUARRIES.ordinal()] = text(settings.quarries() ? "quarries.on" : "quarries.off");
        values[Field.MONEY.ordinal()] = settings.startMoney() > 0 ? ClientSellData.amountText(settings.startMoney())
                : text("money.off");
        values[Field.KIT.ordinal()] = numberOr(settings.kit().size(), "kit.value", "kit.off");
    }

    public void build(StudioStack stack, int x, int width) {
        for (int index = 0; index < rows.size(); index++) {
            AbstractWidget row = rows.get(index);
            row.setWidth(width);
            stack.add(row, x, gaps.get(index));
        }
    }

    // WHY: правило словами собирается из тех же цифр, что строки: игрок читает, как выиграть, а
    // WHY: оператор дописывает своё под ним, а не переписывает цифры текстом, который разойдётся с ними
    public static List<Component> about(ModeView.Settings settings) {
        List<Component> lines = new ArrayList<>();
        lines.add(settings.scoreTarget() > 0 ? text("about.target", settings.scoreTarget()) : text("about.final"));
        if (settings.capMinutes() > 0) lines.add(text("about.cap", settings.capMinutes()));
        if (sudden(settings)) lines.add(text("about.sudden", settings.suddenMargin(), settings.suddenMinutes()));
        Component sources = sources(settings);
        if (sources != null) lines.add(text("about.points", sources));
        if (!settings.quarries()) lines.add(text("about.no_quarries"));
        if (!settings.kit().isEmpty()) lines.add(text("about.kit"));
        if (!settings.description().isEmpty()) lines.add(Component.literal(settings.description()));
        return lines;
    }

    // WHY: внезапная смерть нужна и потолку, и своей длине, и разнице: при разнице 0 часы её не начинают
    private static boolean sudden(ModeView.Settings settings) {
        return settings.capMinutes() > 0 && settings.suddenMinutes() > 0 && settings.suddenMargin() > 0;
    }

    // WHY: абзац называет только то, что в режиме приносит очки, иначе обещал бы их за убийства при нуле
    private static Component sources(ModeView.Settings settings) {
        MutableComponent listed = Component.empty();
        boolean any = false;
        if (settings.pointScore() > 0) any = join(listed, false, "about.source.points");
        if (settings.killScore() > 0) any = join(listed, any, "about.source.kills");
        if (settings.vehicleScore() > 0 || settings.transportScore() > 0) any = join(listed, any, "about.source.vehicles");
        return any ? listed : null;
    }

    private static boolean join(MutableComponent listed, boolean any, String key) {
        if (any) listed.append(", ");
        listed.append(text(key));
        return true;
    }

    private static Component numberOr(int value, String shown, String off) {
        return value > 0 ? text(shown, value) : text(off);
    }

    private static Component text(String key, Object... arguments) {
        return Component.translatable(KEY + key, arguments);
    }
}
