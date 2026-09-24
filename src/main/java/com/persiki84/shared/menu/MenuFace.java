package com.persiki84.shared.menu;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

// WHY: окно соседа собирается из ключей перевода, а не из текста: клиент не может разослать
// WHY: через сервер ни произвольную строку, ни чужие данные своего экрана, только имена вкладок
public record MenuFace(MenuKind kind, String title, List<String> tabs, int active) {
    public static final int MAX_TABS = 8;
    public static final int MAX_KEY = 64;
    public static final int NO_TAB = -1;

    private static final Pattern KEY = Pattern.compile("[a-z0-9_]+(\\.[a-z0-9_]+)+");

    public MenuFace {
        tabs = List.copyOf(tabs);
    }

    public static MenuFace of(MenuKind kind, Component title) {
        return new MenuFace(kind, keyOf(title), List.of(), NO_TAB);
    }

    public static MenuFace of(MenuKind kind, Component title, List<Component> tabs, int active) {
        int count = Math.min(MAX_TABS, tabs.size());
        List<String> keys = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            keys.add(keyOf(tabs.get(index)));
        }
        return new MenuFace(kind, keyOf(title), keys, active >= 0 && active < count ? active : NO_TAB);
    }

    // WHY: подпись вне перевода (раздел магазина, имя игрока) это данные экрана, и она не уходит
    // WHY: соседям вовсе: вкладка остаётся пустой плашкой
    private static String keyOf(Component value) {
        return value.getContents() instanceof TranslatableContents translatable
                && sane(translatable.getKey()) ? translatable.getKey() : "";
    }

    // WHY: смена вкладки это тот же экран, и его кадр остаётся до свежего; другой экран свой кадр
    // WHY: снимает сразу, иначе под новым заголовком висела бы картинка прошлого окна
    public static boolean sameScreen(MenuFace first, MenuFace second) {
        return first != null && second != null && first.kind == second.kind && first.title.equals(second.title);
    }

    public boolean valid() {
        if (kind == null || !blankOrSane(title) || tabs.size() > MAX_TABS) return false;
        if (active != NO_TAB && (active < 0 || active >= tabs.size())) return false;
        for (String tab : tabs) {
            if (!blankOrSane(tab)) return false;
        }
        return true;
    }

    private static boolean blankOrSane(String key) {
        return key.isEmpty() || sane(key);
    }

    private static boolean sane(String key) {
        return key.length() <= MAX_KEY && KEY.matcher(key).matches();
    }

    public static void write(MenuFace face, FriendlyByteBuf buffer) {
        buffer.writeByte(MenuKind.index(face == null ? null : face.kind));
        if (face == null) return;

        buffer.writeUtf(face.title, MAX_KEY);
        buffer.writeByte(face.tabs.size());
        for (String tab : face.tabs) {
            buffer.writeUtf(tab, MAX_KEY);
        }
        buffer.writeByte(face.active);
    }

    public static MenuFace read(FriendlyByteBuf buffer) {
        MenuKind kind = MenuKind.byIndex(buffer.readByte());
        if (kind == null) return null;

        String title = buffer.readUtf(MAX_KEY);
        int count = buffer.readByte();
        if (count < 0 || count > MAX_TABS) throw new DecoderException("menu face tabs: " + count);

        List<String> tabs = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            tabs.add(buffer.readUtf(MAX_KEY));
        }
        return new MenuFace(kind, title, tabs, buffer.readByte());
    }
}
