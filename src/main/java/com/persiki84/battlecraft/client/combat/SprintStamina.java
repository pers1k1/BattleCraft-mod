package com.persiki84.battlecraft.client.combat;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.client.ClientGameRules;
import com.persiki84.battlecraft.rules.GameRule;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class SprintStamina {
    private static final float PERCENT = 100.0f;

    private static float pending;
    private static boolean wasOnGround = true;
    private static boolean leaping;

    private SprintStamina() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof LocalPlayer player) || !player.isLocalPlayer()) return;

        trackLeap(player);

        if (!running(player) || !ClientGameRules.allows(GameRule.SPRINT_COSTS_STAMINA)) {
            pending = 0.0f;
            return;
        }
        drain(player);
    }

    private static void drain(LocalPlayer player) {
        pending += owed(player);

        int whole = (int) pending;
        if (whole <= 0) return;

        pending -= whole;
        ParkourStamina.drain(player, whole);
    }

    private static float owed(LocalPlayer player) {
        float dashCost = ParkourState.staminaCost(player, ParkourRules.FAST_RUN);
        if (dashCost <= 0.0f) return 0.0f;

        int share = GunHold.armed(player)
                ? ClientGameRules.stamina().armedSprintDashPercent()
                : ClientGameRules.stamina().sprintDashPercent();
        float taken = ParkourState.doing(player, ParkourRules.FAST_RUN) ? dashCost : 0.0f;
        return Math.max(0.0f, dashCost * share / PERCENT - taken);
    }

    private static void trackLeap(LocalPlayer player) {
        boolean onGround = player.onGround();
        if (onGround || !sprintingOnFoot(player)) {
            leaping = false;
        } else if (wasOnGround) {
            leaping = player.getDeltaMovement().y > 0.0;
        }
        wasOnGround = onGround;
    }

    private static boolean running(LocalPlayer player) {
        return sprintingOnFoot(player) && (player.onGround() || leaping);
    }

    private static boolean sprintingOnFoot(LocalPlayer player) {
        return player.isSprinting() && !player.isSpectator()
                && !player.isSwimming() && !player.isPassenger()
                && !player.getAbilities().flying && !player.isFallFlying()
                && !player.onClimbable();
    }
}
