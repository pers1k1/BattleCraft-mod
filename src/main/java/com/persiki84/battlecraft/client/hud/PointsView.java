package com.persiki84.battlecraft.client.hud;

import com.mojang.blaze3d.platform.InputConstants;
import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.client.KeyInputHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class PointsView {
    public static final KeyMapping SHOW_POINTS = new KeyMapping(
            "key." + BattleCraftMod.MOD_ID + ".points_hud",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, KeyInputHandler.CATEGORY);

    private static final long HOLD_FROM_MS = 200L;

    private static boolean pressed;
    private static boolean holding;
    private static long pressedAt;

    private PointsView() {}

    public static boolean shown() {
        return holding != HudConfig.pointsInHud();
    }

    public static Component keyName(Component unbound) {
        return SHOW_POINTS.isUnbound() ? unbound : SHOW_POINTS.getTranslatedKeyMessage();
    }

    public static void key(InputConstants.Key key) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.setKey(SHOW_POINTS, key);
        KeyMapping.resetMapping();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null) {
            forget();
            return;
        }
        follow(SHOW_POINTS.isDown(), System.currentTimeMillis());
    }

    // WHY: короткое нажатие переключает видимость насовсем, а удержание лишь выворачивает её на
    // WHY: время удержания, поэтому отпускание после удержания не считается нажатием
    private static void follow(boolean down, long now) {
        if (down && !pressed) {
            pressed = true;
            holding = false;
            pressedAt = now;
            return;
        }
        if (down) {
            holding = now - pressedAt >= HOLD_FROM_MS;
            return;
        }
        if (!pressed) return;

        if (!holding) HudConfig.pointsInHud(!HudConfig.pointsInHud());
        forget();
    }

    // WHY: экран открывается поверх зажатой клавиши, и отпускания игра уже не покажет: без сброса
    // WHY: точки остались бы вывернутыми до следующего нажатия
    private static void forget() {
        pressed = false;
        holding = false;
    }
}
