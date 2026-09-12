package com.persiki84.battlecraft.client.combat;

import com.persiki84.battlecraft.client.ClientGameRules;
import com.persiki84.battlecraft.rules.GameRule;
import com.persiki84.battlecraft.BattleCraftMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class CombatRules {

    private static final int SHOOT_HOLD_TICKS = 10;

    private static int shootHold;

    private CombatRules() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTickStart(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        apply();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTickEnd(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) trackTrigger(player);

        apply();
        if (shootHold > 0) shootHold--;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator()) return;
        if (aiming(player) && ClientGameRules.allows(GameRule.ZOOM_LOCKED_WHILE_AIMING)) ZoomBridge.suppress();
    }

    private static void apply() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator()) return;
        if (reloading(player)) {
            cancelAim(player);
            walk(player);
            return;
        }
        if (!grounded(player)) return;

        if (aiming(player) && ClientGameRules.allows(GameRule.ZOOM_LOCKED_WHILE_AIMING)) ZoomBridge.suppress();
        walk(player);
    }

    public static boolean grounded(LocalPlayer player) {
        if (aiming(player)) return ClientGameRules.allows(GameRule.AIM_WALKS);
        if (reloading(player)) return ClientGameRules.allows(GameRule.RELOAD_WALKS);
        return shooting() && ClientGameRules.allows(GameRule.FIRE_WALKS);
    }

    private static boolean shooting() {
        return shootHold > 0;
    }

    private static void trackTrigger(LocalPlayer player) {
        if (GunHold.armed(player) && triggerHeld()) shootHold = SHOOT_HOLD_TICKS;
    }

    private static boolean triggerHeld() {
        return TaczCombat.firing() || KeyPressed.physically(Minecraft.getInstance().options.keyAttack);
    }

    private static void walk(LocalPlayer player) {
        if (player.isSprinting()) player.setSprinting(false);
        TaczCombat.holdSprintLock();
    }

    private static void cancelAim(LocalPlayer player) {
        TaczCombat.cancelAim(player);
        SuperbCombat.cancelAim();
    }

    static boolean aiming(LocalPlayer player) {
        return TaczCombat.aiming(player) || SuperbCombat.aiming(player);
    }

    private static boolean reloading(LocalPlayer player) {
        return TaczCombat.reloading(player) || SuperbCombat.reloading(player);
    }
}
