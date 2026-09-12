package com.persiki84.battlecraft.client.combat;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.combat.Adrenaline;
import com.persiki84.battlecraft.combat.AdrenalineDose;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class AdrenalineRelief {
    private static final int UNKNOWN = -1;

    private static float carry;
    private static int last = UNKNOWN;

    private AdrenalineRelief() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof LocalPlayer player) || !player.isLocalPlayer()) return;

        AdrenalineDose dose = Adrenaline.active(player);
        if (dose == null) {
            forget();
            return;
        }
        refund(player, dose);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        forget();
    }

    @SubscribeEvent
    public static void onRespawn(ClientPlayerNetworkEvent.Clone event) {
        forget();
    }

    // WHY: у ParCool нет атрибута расхода выносливости, поэтому доза возвращает долю падения за тик
    private static void refund(LocalPlayer player, AdrenalineDose dose) {
        int value = ParkourStamina.value(player);
        if (value == UNKNOWN || last == UNKNOWN) {
            last = value;
            return;
        }

        carry += Math.max(0, last - value) * (float) dose.reliefShare();
        int whole = (int) carry;
        if (whole > 0) {
            carry -= whole;
            ParkourStamina.restore(player, whole);
            value = ParkourStamina.value(player);
        }
        last = value;
    }

    private static void forget() {
        carry = 0.0f;
        last = UNKNOWN;
    }
}
