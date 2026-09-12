package com.persiki84.shared;

import net.minecraft.network.chat.Component;

public final class ArgNumbers {

    private ArgNumbers() {}

    public static int asInt(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();

        String text = plain(value);
        if (text == null) return fallback;

        StringBuilder digits = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char symbol = text.charAt(index);
            if (symbol == '-' && digits.length() == 0) {
                digits.append(symbol);
            } else if (symbol >= '0' && symbol <= '9') {
                digits.append(symbol);
            } else if (digits.length() > 0) {
                break;
            }
        }

        try {
            return digits.length() == 0 || digits.toString().equals("-") ? fallback
                    : Integer.parseInt(digits.toString());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String plain(Object value) {
        if (value instanceof Component component) return component.getString();
        if (value instanceof CharSequence text) return text.toString();
        return null;
    }
}
