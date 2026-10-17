package com.persiki84.sellmod.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import com.persiki84.shared.client.ui.UiRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// WHY: игрок в окнах денег узнаётся по лицу: в окне перевода лицо берётся прямо со скина из списка
// WHY: игроков, а плитке студии нужен предмет, и голова получает профиль с текстурами, если игрок
// WHY: в сети, иначе одно имя, и скин подтянет сама ваниль
public final class PlayerHeads {
    private static final String OWNER = "SkullOwner";
    private static final float GONE = 0.01f;

    private record Head(ItemStack stack, boolean textured) {}

    private static final Map<UUID, Head> heads = new HashMap<>();

    private PlayerHeads() {}

    public static ItemStack stack(UUID id, String name) {
        Head known = heads.get(id);
        PlayerInfo info = info(id);
        boolean textured = info != null && !info.getProfile().getProperties().isEmpty();
        if (known != null && (known.textured() || !textured)) return known.stack();

        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        if (textured) {
            head.getOrCreateTag().put(OWNER, NbtUtils.writeGameProfile(new CompoundTag(), info.getProfile()));
        } else if (name != null && !name.isEmpty()) {
            head.getOrCreateTag().putString(OWNER, name);
        }
        heads.put(id, new Head(head, textured));
        return head;
    }

    public static void forget() {
        heads.clear();
    }

    public static ResourceLocation skin(UUID id) {
        PlayerInfo info = info(id);
        return info == null ? null : info.getSkinLocation();
    }

    public static GameProfile profile(UUID id) {
        PlayerInfo info = info(id);
        return info == null ? null : info.getProfile();
    }

    // WHY: лицо едет вместе с плиткой дробными координатами, а PlayerFaceRenderer берёт только целые:
    // WHY: место и размер ставит матрица, лицо рисуется в ноль размером восемь и растягивается ею
    public static void face(GuiGraphics graphics, UUID id, float x, float y, float size, float alpha) {
        ResourceLocation skin = skin(id);
        if (skin == null || alpha <= GONE || size <= 0.0f) return;

        graphics.flush();
        float[] tone = RenderSystem.getShaderColor();
        float red = tone[0];
        float green = tone[1];
        float blue = tone[2];
        float base = tone[3];
        RenderSystem.setShaderColor(red, green, blue, base * alpha);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x, y, 0.0f);
            graphics.pose().scale(size / 8.0f, size / 8.0f, 1.0f);
            UiRender.standardBlend();
            PlayerFaceRenderer.draw(graphics, skin, 0, 0, 8);
            graphics.flush();
        } finally {
            graphics.pose().popPose();
            RenderSystem.setShaderColor(red, green, blue, base);
            UiRender.standardBlend();
        }
    }

    private static PlayerInfo info(UUID id) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection == null || id == null ? null : connection.getPlayerInfo(id);
    }
}
