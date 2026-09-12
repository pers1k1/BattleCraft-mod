package com.persiki84.zones.client;

import com.persiki84.shared.client.ui.Smooth;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public final class ZonePrompts {
    private static final int MAX_VISIBLE = 3;
    private static final float FADE_SPEED = 8.0f;
    private static final float SLIDE_SPEED = 12.0f;
    private static final float SPAWN_SLOT_OFFSET = 0.65f;

    private static final List<Prompt> prompts = new ArrayList<>();

    private ZonePrompts() {}

    public static void announce(Component text, long holdMillis) {
        for (Prompt prompt : prompts) {
            if (Objects.equals(prompt.text.getString(), text.getString())) {
                prompt.expiresAt = System.currentTimeMillis() + holdMillis;
                return;
            }
        }

        prompts.add(new Prompt(text, System.currentTimeMillis() + holdMillis,
                prompts.size() + SPAWN_SLOT_OFFSET));
        expireOverflow();
    }

    private static void expireOverflow() {
        long now = System.currentTimeMillis();
        int kept = 0;
        for (int index = prompts.size() - 1; index >= 0; index--) {
            Prompt prompt = prompts.get(index);
            if (prompt.expiresAt <= now) continue;

            kept++;
            if (kept > MAX_VISIBLE) prompt.expiresAt = 0L;
        }
    }

    public static void hold(Component text) {
        announce(text, Long.MAX_VALUE - System.currentTimeMillis());
    }

    public static void release(Component text) {
        for (Prompt prompt : prompts) {
            if (Objects.equals(prompt.text.getString(), text.getString())) {
                prompt.expiresAt = 0L;
            }
        }
    }

    public static void clear() {
        prompts.clear();
    }

    public static List<Prompt> update(float delta, boolean suppressed) {
        long now = System.currentTimeMillis();
        Iterator<Prompt> iterator = prompts.iterator();
        int slot = 0;

        while (iterator.hasNext()) {
            Prompt prompt = iterator.next();
            float target = !suppressed && now < prompt.expiresAt ? 1.0f : 0.0f;
            prompt.alpha.to(target, delta);
            prompt.slot.to(slot, delta);
            if (target <= 0.0f && prompt.alpha.get() < 0.01f) {
                iterator.remove();
                continue;
            }
            slot++;
        }
        return prompts;
    }

    public static final class Prompt {
        private final Component text;
        private final Smooth alpha = new Smooth(FADE_SPEED);
        private final Smooth slot = new Smooth(SLIDE_SPEED);
        private long expiresAt;

        private Prompt(Component text, long expiresAt, float spawnSlot) {
            this.text = text;
            this.expiresAt = expiresAt;
            this.slot.snap(spawnSlot);
        }

        public Component text() { return text; }
        public float alpha() { return alpha.get(); }
        public float slot() { return slot.get(); }
    }
}
