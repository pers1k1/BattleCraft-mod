package com.persiki84.sellmod.money;

import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.battlecraft.modules.ModuleSwitches;
import com.persiki84.knockdown.cap.KnockdownCapability;
import com.persiki84.knockdown.cap.KnockdownProvider;
import com.persiki84.shared.economy.WalletTransfer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

// WHY: перевод доступен любому игроку в сети, но не тому, кто уже лежит: сбитый сливал бы счёт
// WHY: союзнику за секунду до смерти, и доля, теряемая при смерти, не терялась бы никогда
public final class MoneyPayments {

    private MoneyPayments() {}

    public static boolean pay(ServerPlayer from, ServerPlayer to, long amount) {
        if (!ModuleSwitches.allows(ModuleId.SELL)) {
            from.sendSystemMessage(Component.translatable("battlecraft.module.disabled",
                    Component.translatable(ModuleId.SELL.label())).withStyle(ChatFormatting.RED));
            return false;
        }
        String refusal = refusal(from);
        if (refusal != null) {
            from.sendSystemMessage(Component.translatable(refusal).withStyle(ChatFormatting.RED));
            return false;
        }
        return WalletTransfer.pay(from, to, amount);
    }

    private static String refusal(ServerPlayer from) {
        if (!from.isAlive() || from.isSpectator() || knocked(from)) return "sellmod.pay.error.downed";
        return null;
    }

    private static boolean knocked(ServerPlayer player) {
        KnockdownCapability state = player.getCapability(KnockdownProvider.KNOCKDOWN_CAP).orElse(null);
        return state != null && state.isKnocked();
    }
}
