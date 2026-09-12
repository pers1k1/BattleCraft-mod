package com.persiki84.battlecraft.client.combat;

import com.mojang.blaze3d.platform.InputConstants;
import com.persiki84.battlecraft.client.ClientGameRules;
import com.persiki84.battlecraft.rules.GameRule;
import com.persiki84.battlecraft.BattleCraftMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class ParkourHands {

    private static final List<String> BUSY_HANDS = List.of(
            "com.alrex.parcool.common.action.impl.ClimbUp",
            "com.alrex.parcool.common.action.impl.Vault",
            "com.alrex.parcool.common.action.impl.ClingToCliff",
            "com.alrex.parcool.common.action.impl.HangDown",
            "com.alrex.parcool.common.action.impl.WallJump",
            "com.alrex.parcool.common.action.impl.HorizontalWallRun",
            "com.alrex.parcool.common.action.impl.VerticalWallRun",
            "com.alrex.parcool.common.action.impl.WallSlide",
            "com.alrex.parcool.common.action.impl.ClimbPoles",
            "com.alrex.parcool.common.action.impl.RideZipline",
            "com.alrex.parcool.common.action.impl.JumpFromBar",
            "com.alrex.parcool.common.action.impl.CatLeap",
            "com.alrex.parcool.common.action.impl.Flipping",
            "com.alrex.parcool.common.action.impl.Roll",
            "com.alrex.parcool.common.action.impl.Dive",
            "com.alrex.parcool.common.action.impl.SkyDive",
            "com.alrex.parcool.common.action.impl.FastSwim",
            "com.alrex.parcool.common.action.impl.Slide"
    );

    private static final List<String> ROLLING = List.of(
            "com.alrex.parcool.common.action.impl.Dodge"
    );

    private static final int MAX_HOLD_TICKS = 80;

    private static int held;

    private ParkourHands() {}

    public static boolean busy(Player player) {
        if (!ClientGameRules.allows(GameRule.PARKOUR_HIDES_WEAPON)) return false;
        if (player == null || !ParkourState.doingAny(player, BUSY_HANDS)) return false;
        return player != Minecraft.getInstance().player || held < MAX_HOLD_TICKS;
    }

    private static boolean rolling(Player player) {
        if (!ClientGameRules.allows(GameRule.DODGE_BLOCKS_FIRE)) return false;
        return player != null && ParkourState.doingAny(player, ROLLING);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !bound(minecraft.options.keyAttack, event.getButton())) return;
        if (busy(player) || rolling(player)) event.setCanceled(true);
    }

    private static boolean bound(KeyMapping key, int button) {
        InputConstants.Key mapped = key.getKey();
        return mapped.getType() == InputConstants.Type.MOUSE && mapped.getValue() == button;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderHand(RenderHandEvent event) {
        if (busy(Minecraft.getInstance().player)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        if (!ParkourState.doingAny(player, BUSY_HANDS)) {
            held = 0;
            return;
        }

        held++;
        if (held < MAX_HOLD_TICKS) holster(player);
    }

    private static void holster(LocalPlayer player) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.keyAttack.setDown(false);
        minecraft.options.keyUse.setDown(false);
        TaczCombat.cancelAim(player);
        SuperbCombat.cancelAim();
    }
}
