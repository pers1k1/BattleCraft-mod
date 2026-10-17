package com.persiki84.zones.client.menu.studio;

import com.persiki84.sellmod.client.MoneySnapshot;
import com.persiki84.sellmod.client.MoneyText;
import com.persiki84.sellmod.client.PlayerHeads;
import com.persiki84.shared.client.menu.studio.StudioTile;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

// WHY: счёт на витрине студии это голова игрока с ником и суммой на плашке: сумма видна сразу у всех,
// WHY: а вышедшие из игры погашены. В «Предмете» сумму вышедшего сервер не знает, и плашка честно
// WHY: говорит «?», а не ноль
record AccountTile(MoneySnapshot.Account account, String key, ItemStack icon, Component name, Component plate,
                   Component mark) implements StudioTile {
    private static final Component UNKNOWN = Component.literal("?");
    private static final Component NONE = Component.empty();

    static AccountTile of(MoneySnapshot.Account account, boolean self) {
        Component plate = account.known() ? Component.literal(MoneyText.digits(account.balance())) : UNKNOWN;
        Component mark = self ? Component.translatable("sellmod.studio.money.you") : NONE;
        return new AccountTile(account, account.id().toString(), PlayerHeads.stack(account.id(), account.name()),
                Component.literal(account.name()), plate, mark);
    }

    @Override
    public Component corner() {
        return NONE;
    }

    @Override
    public boolean faded() {
        return !account.online();
    }

    // WHY: команды адресуют счёт ником, а ник у записи без имени (игрок ни разу не заходил после
    // WHY: обновления) пуст: тогда идёт идентификатор, который сервер тоже принимает
    String address() {
        return account.name().isEmpty() ? key() : account.name();
    }
}
