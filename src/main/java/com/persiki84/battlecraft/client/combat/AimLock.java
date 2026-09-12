package com.persiki84.battlecraft.client.combat;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.client.ClientGameRules;
import com.persiki84.battlecraft.rules.GameRule;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class AimLock {

    private AimLock() {}

    public static boolean jumpDenied(LocalPlayer player) {
        return player != null && !player.isSpectator()
                && ClientGameRules.allows(GameRule.NO_JUMP_WHILE_AIMING)
                && CombatRules.aiming(player);
    }

    public static boolean dodgeDenied(LocalPlayer player) {
        return player != null && !player.isSpectator()
                && ClientGameRules.allows(GameRule.NO_DODGE_WHILE_AIMING)
                && CombatRules.aiming(player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || !jumpDenied(player)) return;
        event.getInput().jumping = false;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKey(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!jumpDenied(minecraft.player)) return;
        drain(minecraft.options.keyJump);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTickStart(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (!jumpDenied(minecraft.player)) return;
        drain(minecraft.options.keyJump);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTickEnd(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (!jumpDenied(player)) return;

        drain(minecraft.options.keyJump);
        player.input.jumping = false;
    }

    private static void drain(KeyMapping jump) {
        jump.setDown(false);
        while (jump.consumeClick()) {
        }
    }
}
