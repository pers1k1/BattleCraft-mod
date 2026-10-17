package com.persiki84.zones.client.menu.studio;

import com.persiki84.sellmod.client.MoneyText;
import com.persiki84.shared.client.menu.MenuRow;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.economy.WalletEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// WHY: строка журнала счёта: что случилось и с кем слева, сколько пришло или ушло справа. Подписи
// WHY: собираются при приходе снимка, а не в кадре, и время «сколько назад» берётся в тот же момент
final class WalletEntryRow extends MenuRow {
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60L * MINUTE;
    private static final long DAY = 24L * HOUR;
    private static final float VALUE_SCALE = 0.9f;

    private Component delta = Component.empty();
    private boolean gain;

    WalletEntryRow(int height) {
        super(0, 0, 10, height, Component.empty());
    }

    void show(WalletEntry entry, long now) {
        setMessage(Component.translatable(entry.kind().translationKey()));
        note(details(entry, now));
        gain = entry.delta() >= 0L;
        delta = Component.literal(MoneyText.signed(entry.delta()));
    }

    private static Component details(WalletEntry entry, long now) {
        Component when = ago(now - entry.time());
        boolean subject = !entry.subject().isEmpty();
        if (subject && entry.count() > 0) {
            return Component.translatable("sellmod.studio.money.log.counted", entry.subject(), entry.count(), when);
        }
        if (subject) return Component.translatable("sellmod.studio.money.log.subject", entry.subject(), when);
        return when;
    }

    static Component ago(long elapsed) {
        if (elapsed < MINUTE) return Component.translatable("sellmod.studio.money.ago.now");
        if (elapsed < HOUR) return Component.translatable("sellmod.studio.money.ago.minutes", elapsed / MINUTE);
        if (elapsed < DAY) return Component.translatable("sellmod.studio.money.ago.hours", elapsed / HOUR);
        return Component.translatable("sellmod.studio.money.ago.days", elapsed / DAY);
    }

    @Override
    protected void renderValue(GuiGraphics graphics, int mouseX, int mouseY, float focus) {
        float right = getX() + width - PAD;
        float room = width * 0.4f;
        UiRender.textTrackedBox(graphics, font(), delta, right - room, getY(), height, room, VALUE_SCALE, 0.0f,
                gain ? UiAccent.color() : UiPalette.alert(), false, 1.0f);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }
}
