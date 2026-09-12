package com.persiki84.battlecraft.client.voice;

import com.persiki84.shared.client.menu.KeyRow;
import com.persiki84.shared.client.menu.PickRow;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class VoiceRows {
    public static final int COUNT = 4;

    private static final Component DEFAULT_DEVICE =
            Component.translatable("battlecraft.voice.device.default");
    private static final Component UNBOUND =
            Component.translatable("battlecraft.voice.key.unbound");
    private static final int VOICE_ACTIVATION = 0;
    private static final int PUSH_TO_TALK = 1;

    private VoiceRows() {}

    public static List<AbstractWidget> build(int left, int top, int width, int height, int gap) {
        List<AbstractWidget> built = new ArrayList<>();
        int y = top;

        built.add(deviceRow(left, y, width, height, "battlecraft.voice.speaker",
                VoiceOptions.speakers(), VoiceOptions::speaker, VoiceOptions::speaker));
        y += height + gap;
        built.add(deviceRow(left, y, width, height, "battlecraft.voice.microphone",
                VoiceOptions.microphones(), VoiceOptions::microphone, VoiceOptions::microphone));
        y += height + gap;
        built.add(modeRow(left, y, width, height));
        y += height + gap;
        built.add(keyRow(left, y, width, height));
        return built;
    }

    // WHY: строка клавиши гаснет при голосовой активации, но остаётся на месте: пропадающая
    // WHY: строка утаскивает за собой всю раскладку страницы при каждом переключении способа
    public static void refresh(List<? extends AbstractWidget> rows) {
        for (AbstractWidget row : rows) {
            if (row instanceof KeyRow key) key.active = VoiceOptions.pushToTalk();
        }
    }

    private static PickRow deviceRow(int left, int top, int width, int height, String key,
                                     List<String> devices, Supplier<String> reader,
                                     Consumer<String> writer) {
        List<String> choices = new ArrayList<>();
        choices.add("");
        choices.addAll(devices);

        String stored = reader.get();
        if (!stored.isEmpty() && !choices.contains(stored)) choices.add(stored);

        List<Component> labels = new ArrayList<>();
        for (String device : choices) {
            labels.add(VoiceOptions.deviceName(device, DEFAULT_DEVICE));
        }
        PickRow row = new PickRow(left, top, width, height, Component.translatable(key), labels,
                () -> Math.max(0, choices.indexOf(reader.get())),
                picked -> writer.accept(choices.get(picked)));
        row.hint(key + ".hint");
        return row;
    }

    private static PickRow modeRow(int left, int top, int width, int height) {
        List<Component> labels = List.of(
                Component.translatable("battlecraft.voice.mode.voice"),
                Component.translatable("battlecraft.voice.mode.ptt"));
        PickRow row = new PickRow(left, top, width, height,
                Component.translatable("battlecraft.voice.mode"), labels,
                () -> VoiceOptions.pushToTalk() ? PUSH_TO_TALK : VOICE_ACTIVATION,
                picked -> VoiceOptions.pushToTalk(picked == PUSH_TO_TALK));
        row.hint("battlecraft.voice.mode.hint");
        return row;
    }

    private static KeyRow keyRow(int left, int top, int width, int height) {
        KeyRow row = new KeyRow(left, top, width, height,
                Component.translatable("battlecraft.voice.key"),
                () -> VoiceOptions.talkKeyName(UNBOUND), VoiceOptions::talkKey);
        row.active = VoiceOptions.pushToTalk();
        row.hint("battlecraft.voice.key.hint");
        return row;
    }
}
