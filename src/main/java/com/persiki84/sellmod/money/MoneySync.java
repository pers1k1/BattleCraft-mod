package com.persiki84.sellmod.money;

import com.persiki84.sellmod.network.PacketHandler;
import com.persiki84.shared.economy.WalletListener;
import com.persiki84.shared.economy.Wallets;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

// WHY: общий слой денег не знает каналов модулей, поэтому о перемене счёта и настроек он сообщает
// WHY: сюда, а канал скупки развозит это клиентам: свой баланс владельцу, настройки всем
public final class MoneySync implements WalletListener {
    public static final MoneySync INSTANCE = new MoneySync();

    private MoneySync() {}

    @Override
    public void balanceChanged(ServerPlayer player) {
        if (player.connection == null) return;
        PacketHandler.balanceTo(player, Wallets.open(player).balance());
    }

    @Override
    public void settingsChanged(MinecraftServer server) {
        PacketHandler.syncToAll(server);
    }
}
