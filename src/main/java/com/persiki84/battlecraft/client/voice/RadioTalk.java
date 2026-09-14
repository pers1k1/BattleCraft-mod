package com.persiki84.battlecraft.client.voice;

import com.mojang.blaze3d.platform.InputConstants;
import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.client.KeyInputHandler;
import com.persiki84.battlecraft.client.hud.ToastHud;
import com.persiki84.battlecraft.client.hud.VoiceBridge;
import com.persiki84.battlecraft.compat.walkie.Walkie;
import com.persiki84.battlecraft.network.C2SRadioKeyPacket;
import com.persiki84.battlecraft.network.PacketHandler;
import com.persiki84.shared.client.ui.UiSound;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

// WHY: рация в руке это клавиша передачи у чужого мода, и с ней в руках нет оружия. Своя
// WHY: клавиша выводит в эфир рацию с пояса: она же ведёт голосовой чат в передачу (миксин
// WHY: на PTTKeyHandler) и она же говорит серверу, что эту речь надо пустить по частоте
public final class RadioTalk {
    public static final KeyMapping TALK = new KeyMapping(
            "key." + BattleCraftMod.MOD_ID + ".radio_talk",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_CAPS_LOCK, KeyInputHandler.CATEGORY);

    private static final long REFRESH_MS = 400L;
    private static final long NOTICE_MS = 2500L;
    private static final long NOTICE_LIFE_MS = 600L;

    private static volatile boolean air;

    private static boolean refused;
    private static long refreshedAt;
    private static long noticedAt;

    private RadioTalk() {}

    // WHY: читают из потока микрофона голосового чата, пишут из клиентского тика
    public static boolean onAir() {
        return air;
    }

    // WHY: одно условие «я в эфире» на карточку передачи и на щелчки: рация в руке это путь
    // WHY: чужого мода, клавиша это наш, а выглядеть и звучать они обязаны одинаково
    public static boolean speaking(Player player, long now) {
        if (player == null) return false;
        if (!air && Walkie.transmitting(player).isEmpty()) return false;

        return VoiceTraffic.own().talking(now);
    }

    public static Component keyName(Component unbound) {
        return TALK.isUnbound() ? unbound : TALK.getTranslatedKeyMessage();
    }

    public static void key(InputConstants.Key key) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.setKey(TALK, key);
        KeyMapping.resetMapping();
        minecraft.options.save();
    }

    public static void tick(Player player) {
        if (!pressed()) {
            release();
            return;
        }
        if (air) {
            refresh();
            return;
        }
        if (!refused) open(player);
    }

    // WHY: заявку отсюда не шлют: зовут при выходе из мира, канала уже нет. Сервер снимает
    // WHY: эфир сам - по истечении заявки и по выходу игрока
    public static void forget() {
        air = false;
        refused = false;
        refreshedAt = 0L;
    }

    // WHY: экран и потеря фокуса снимают клавишу сами: иначе Alt+Tab оставляет эфир открытым,
    // WHY: а отпускание клавиши приходит уже в чужое окно и до игры не доезжает вовсе
    private static boolean pressed() {
        Minecraft minecraft = Minecraft.getInstance();
        return TALK.isDown() && minecraft.screen == null && minecraft.isWindowActive()
                && Walkie.available();
    }

    private static void open(Player player) {
        Refusal refusal = check(player);
        if (refusal != null) {
            refused = true;
            notice(refusal);
            return;
        }

        air = true;
        refreshedAt = System.currentTimeMillis();
        PacketHandler.INSTANCE.sendToServer(new C2SRadioKeyPacket(true));
    }

    // WHY: сервер забывает эфир, если заявка не обновилась: отпускание может потеряться вместе
    // WHY: с вылетом клиента, и тогда чужая речь ушла бы в частоту молча
    private static void refresh() {
        long now = System.currentTimeMillis();
        if (now - refreshedAt < REFRESH_MS) return;

        refreshedAt = now;
        PacketHandler.INSTANCE.sendToServer(new C2SRadioKeyPacket(true));
    }

    private static void release() {
        refused = false;
        if (!air) return;

        air = false;
        PacketHandler.INSTANCE.sendToServer(new C2SRadioKeyPacket(false));
    }

    private static Refusal check(Player player) {
        ItemStack carried = Walkie.carried(player);
        if (carried.isEmpty()) return Refusal.NO_RADIO;
        if (!Walkie.active(carried)) return Refusal.RADIO_OFF;
        if (Walkie.muted(carried)) return Refusal.RADIO_MUTED;

        return switch (VoiceBridge.current()) {
            case DISCONNECTED, DISABLED -> Refusal.VOICE_OFF;
            case NO_MICROPHONE -> Refusal.NO_MICROPHONE;
            case MUTED -> Refusal.VOICE_MUTED;
            default -> null;
        };
    }

    // WHY: отказ обязан звучать и читаться: игрок жмёт клавишу в бою и на экран не смотрит,
    // WHY: а молчащая клавиша выглядит как поломка рации, а не как выключенная рация
    private static void notice(Refusal refusal) {
        long now = System.currentTimeMillis();
        if (now - noticedAt < NOTICE_MS) return;

        noticedAt = now;
        ToastHud.pushFor(Component.translatable(refusal.key()), NOTICE_LIFE_MS);
        UiSound.deny();
    }

    private enum Refusal {
        NO_RADIO("no_radio"),
        RADIO_OFF("radio_off"),
        RADIO_MUTED("radio_muted"),
        VOICE_OFF("voice_off"),
        NO_MICROPHONE("no_microphone"),
        VOICE_MUTED("voice_muted");

        private final String id;

        Refusal(String id) {
            this.id = id;
        }

        private String key() {
            return "battlecraft.radio.refuse." + id;
        }
    }
}
