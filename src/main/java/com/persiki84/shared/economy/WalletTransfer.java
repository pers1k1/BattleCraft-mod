package com.persiki84.shared.economy;

import com.persiki84.shared.ActionGate;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;

// WHY: перевод приходит и командой, и пакетом из окна магазина, и оба это заявки: сумма, получатель,
// WHY: частота и наличие денег проверяются здесь. Перевод либо проходит целиком, либо не проходит
// WHY: вовсе: в режиме «Предмет» получатель без места под всю сумму получает отказ, а не кучу под ноги.
// WHY: Мёртвому монеты не передаются: экран смерти держит старый объект игрока, а возрождение без
// WHY: keepInventory его инвентарь не переносит, то есть сумма пропала бы у обоих
public final class WalletTransfer {
    private static final String GATE = "walletPayTick";
    private static final int GATE_TICKS = 10;

    private WalletTransfer() {}

    public static boolean pay(ServerPlayer from, ServerPlayer to, long amount) {
        Component refusal = ActionGate.allow(from, GATE, GATE_TICKS) ? refusal(from, to, amount)
                : Component.translatable("sellmod.pay.error.too_fast");
        if (refusal != null) {
            from.sendSystemMessage(refusal.copy().withStyle(ChatFormatting.RED));
            return false;
        }

        if (Wallet.accounts()) {
            moveAccount(from, to, amount);
        } else {
            moveItems(from, to, (int) amount);
        }
        announce(from, to, amount);
        return true;
    }

    private static Component refusal(ServerPlayer from, ServerPlayer to, long amount) {
        if (amount <= 0L || amount > Wallets.LIMIT) return Component.translatable("sellmod.pay.error.amount");
        if (to == null || to.hasDisconnected()) return Component.translatable("sellmod.pay.error.offline");
        if (from.getUUID().equals(to.getUUID())) return Component.translatable("sellmod.pay.error.self");
        if (!Wallet.accounts() && !to.isAlive()) {
            return Component.translatable("sellmod.pay.error.recipient_dead", to.getDisplayName());
        }

        long balance = Wallet.balance(from);
        if (balance < amount) {
            return Component.translatable("sellmod.pay.error.not_enough", Wallet.amountText(amount - balance));
        }
        long free = Wallet.transferable(from);
        if (free < amount) return Component.translatable("sellmod.pay.error.start_locked", Wallet.amountText(free));
        if (Wallet.room(to) >= amount) return null;
        return Component.translatable(Wallet.accounts() ? "sellmod.pay.error.limit" : "sellmod.pay.error.no_room",
                to.getDisplayName());
    }

    private static void moveAccount(ServerPlayer from, ServerPlayer to, long amount) {
        Wallet.post(from, -amount, WalletEntryKind.SENT, to.getGameProfile().getName(), 0);
        Wallet.post(to, amount, WalletEntryKind.RECEIVED, from.getGameProfile().getName(), 0);
    }

    // WHY: место под всю сумму у получателя проверено заранее, поэтому выдача не падает под ноги;
    // WHY: если ваниль всё же не приняла часть, она возвращается отправителю, а не пропадает
    private static void moveItems(ServerPlayer from, ServerPlayer to, int amount) {
        Item item = Wallet.currencyItem();
        CurrencyItems.take(from, item, amount);
        int returned = CurrencyItems.give(to, item, amount);
        if (returned > 0) {
            int lost = CurrencyItems.give(from, item, returned);
            if (lost > 0) CurrencyItems.drop(from, item, lost);
        }
        int moved = amount - returned;
        Wallet.note(from, -moved, WalletEntryKind.SENT, to.getGameProfile().getName(), 0);
        Wallet.note(to, moved, WalletEntryKind.RECEIVED, from.getGameProfile().getName(), 0);
    }

    private static void announce(ServerPlayer from, ServerPlayer to, long amount) {
        Component sum = Wallet.amountText(amount);
        from.sendSystemMessage(Component.translatable("sellmod.pay.sent", to.getDisplayName(), sum)
                .withStyle(ChatFormatting.GREEN));
        to.sendSystemMessage(Component.translatable("sellmod.pay.received", from.getDisplayName(), sum)
                .withStyle(ChatFormatting.GREEN));
        to.level().playSound(null, to.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS,
                0.6f, 1.2f);
    }
}
