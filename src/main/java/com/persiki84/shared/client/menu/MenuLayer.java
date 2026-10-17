package com.persiki84.shared.client.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.opengl.GL11;

// WHY: ванильный renderItem кладёт значок на высоту 150 и пишет глубину, а стекло, которое ложится
// WHY: поверх него следующим слоем (поднятая плитка, меню, подсказка, окно палитры), стоит ниже.
// WHY: Тест глубины пропускал значки насквозь: они висели поверх чужого стекла и не преломлялись.
// WHY: Новый слой начинается с чистой глубины, тем же приёмом, что ваниль ставит между HUD и экраном.
// WHY: Под ножницами чистится только их прямоугольник
public final class MenuLayer {
    private MenuLayer() {}

    public static void flatten(GuiGraphics graphics) {
        graphics.flush();
        RenderSystem.depthMask(true);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }
}
