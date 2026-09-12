package com.persiki84.battlecraft.compat.goggles;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.network.PacketHandler;
import com.persiki84.battlecraft.network.S2CGogglesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID)
public final class GogglesServer {
    private static final float BATTERY_STEP = 0.005f;

    private static final Map<UUID, S2CGogglesPacket> sent = new HashMap<>();

    private GogglesServer() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player) || !Goggles.available()) return;

        ItemStack goggles = Goggles.equipped(player);
        S2CGogglesPacket state = snapshot(goggles);
        if (unchanged(sent.get(player.getUUID()), state)) return;

        sent.put(player.getUUID(), state);
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), state);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        sent.remove(event.getEntity().getUUID());
    }

    private static S2CGogglesPacket snapshot(ItemStack goggles) {
        if (!Goggles.isGoggles(goggles) || !Goggles.isActive(goggles)) {
            return new S2CGogglesPacket(false, Goggles.MODE_NONE, 0.0f, false);
        }
        return new S2CGogglesPacket(true, Goggles.modeOf(goggles), Goggles.batteryOf(goggles),
                Goggles.hasModule(goggles, Goggles.MODULE_ZOOM));
    }

    private static boolean unchanged(S2CGogglesPacket previous, S2CGogglesPacket state) {
        return previous != null
                && previous.active == state.active
                && previous.mode == state.mode
                && previous.zoom == state.zoom
                && Math.abs(previous.battery - state.battery) < BATTERY_STEP;
    }
}
