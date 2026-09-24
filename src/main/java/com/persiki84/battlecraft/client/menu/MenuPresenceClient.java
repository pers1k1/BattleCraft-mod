package com.persiki84.battlecraft.client.menu;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.network.C2SMenuPresencePacket;
import com.persiki84.battlecraft.network.PacketHandler;
import com.persiki84.shared.client.menu.GlassScreen;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.menu.MenuFace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class MenuPresenceClient {
    private static final float SHOW_SPEED = 8.0f;
    private static final float GONE = 0.01f;

    private static final Map<UUID, Presence> index = new HashMap<>();
    private static final List<Presence> live = new ArrayList<>();

    private static MenuFace reported;
    private static long stamp = -1L;

    private MenuPresenceClient() {}

    public static void accept(UUID player, MenuFace face) {
        if (face == null) settleOwn(player);

        Presence presence = index.get(player);
        if (face == null) {
            if (presence != null) presence.closing = true;
            return;
        }
        if (presence == null) {
            presence = new Presence(player);
            index.put(player, presence);
            live.add(presence);
        }
        if (!MenuFace.sameScreen(presence.face, face)) MenuFrameClient.drop(player);
        presence.face = face;
        presence.closing = false;
    }

    // WHY: сервер снимает окно сам при смерти и смене мира, а экран у игрока может остаться открытым:
    // WHY: без сброса заявка не ушла бы повторно, и соседи не видели бы окно до следующего экрана
    private static void settleOwn(UUID player) {
        LocalPlayer own = Minecraft.getInstance().player;
        if (own != null && own.getUUID().equals(player)) reported = null;
    }

    public static List<Presence> live() {
        advance();
        return live;
    }

    public static float hold(UUID player) {
        advance();
        Presence presence = index.get(player);
        return presence == null ? 0.0f : presence.shown();
    }

    public static void forget() {
        MenuFrameClient.forget();
        index.clear();
        live.clear();
        reported = null;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        report(current());
    }

    private static MenuFace current() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.getConnection() == null) return null;

        Screen screen = client.screen;
        return screen instanceof GlassScreen glass ? glass.face() : null;
    }

    private static void report(MenuFace face) {
        if (Objects.equals(face, reported)) return;

        reported = face;
        PacketHandler.INSTANCE.sendToServer(new C2SMenuPresencePacket(face));
    }

    // WHY: продвигается один раз за кадр и зовётся откуда угодно: позу рисует модель игрока
    // WHY: раньше мировой стадии, и без общей отметки кадра ход шёл бы дважды за кадр
    private static void advance() {
        if (stamp == UiFrame.frame()) return;
        stamp = UiFrame.frame();

        float delta = UiFrame.delta();
        ClientLevel level = Minecraft.getInstance().level;
        for (int slot = live.size() - 1; slot >= 0; slot--) {
            Presence presence = live.get(slot);
            if (level != null && level.getPlayerByUUID(presence.player) == null) presence.closing = true;
            presence.show.to(presence.closing ? 0.0f : 1.0f, SHOW_SPEED, delta);
            presence.age += delta;
            if (!presence.closing || presence.show.get() > GONE) continue;

            live.remove(slot);
            index.remove(presence.player);
            MenuFrameClient.drop(presence.player);
        }
    }

    public static final class Presence {
        private final UUID player;
        private final Smooth show = new Smooth(0.0f, SHOW_SPEED);
        private MenuFace face;
        private boolean closing;
        private float age;

        private Presence(UUID player) {
            this.player = player;
        }

        public UUID player() {
            return player;
        }

        public MenuFace face() {
            return face;
        }

        public float age() {
            return age;
        }

        public float shown() {
            return UiAnim.easeOut(show.get());
        }
    }
}
