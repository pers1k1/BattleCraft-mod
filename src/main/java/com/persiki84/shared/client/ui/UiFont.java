package com.persiki84.shared.client.ui;

import com.persiki84.shared.client.font.MsdfFontSets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.util.function.Function;

public final class UiFont {
    private static final String LOOKUP = "f_92713_";
    private static final String NAMESPACE = UiRender.UI_FONT.getNamespace();

    private static boolean probed;
    private static int depth;
    private static Field lookup;
    private static Function<ResourceLocation, FontSet> vanilla;
    private static Function<ResourceLocation, FontSet> replacement;
    private static ResourceLocation face = UiRender.UI_FONT;
    private static ResourceLocation outer = UiRender.UI_FONT;

    private UiFont() {}

    public static boolean plain() {
        return MsdfFontSets.ready() || depth > 0;
    }

    public static void push() {
        push(UiRender.screenFace());
    }

    public static void push(ResourceLocation replace) {
        if (depth > 0) {
            depth++;
            outer = face;
            face = replace;
            return;
        }
        if (!bind()) return;

        Font font = Minecraft.getInstance().font;
        if (font == null) return;

        try {
            Function<ResourceLocation, FontSet> source = read(font);
            if (source == null) return;
            if (replacement == null) {
                replacement = id -> resolve(source, id);
            }
            face = replace;
            lookup.set(font, replacement);
            vanilla = source;
            depth = 1;
        } catch (Throwable error) {
            lookup = null;
            vanilla = null;
        }
    }

    public static void pop() {
        if (depth == 0) return;
        if (--depth > 0) {
            face = outer;
            return;
        }
        try {
            lookup.set(Minecraft.getInstance().font, vanilla);
        } catch (Throwable error) {
            lookup = null;
        }
        vanilla = null;
    }

    private static FontSet resolve(Function<ResourceLocation, FontSet> source, ResourceLocation id) {
        boolean own = NAMESPACE.equals(id.getNamespace());
        ResourceLocation wanted = own ? id : face;

        FontSet sharp = sharpFace(wanted);
        if (sharp != null) return sharp;
        return source.apply(own ? id : face);
    }

    private static FontSet sharpFace(ResourceLocation wanted) {
        if (!MsdfFontSets.ready()) return null;
        String path = wanted.getPath();
        if (path.startsWith("hero")) return MsdfFontSets.hero();
        if (path.startsWith("title")) return MsdfFontSets.title();
        if (path.contains("semibold")) return MsdfFontSets.semibold();
        return path.contains("bold") ? MsdfFontSets.bold() : MsdfFontSets.regular();
    }

    @SuppressWarnings("unchecked")
    private static Function<ResourceLocation, FontSet> read(Font font) throws IllegalAccessException {
        Object value = lookup.get(font);
        return value instanceof Function ? (Function<ResourceLocation, FontSet>) value : null;
    }

    private static boolean bind() {
        if (!probed) {
            probed = true;
            try {
                lookup = ObfuscationReflectionHelper.findField(Font.class, LOOKUP);
            } catch (Throwable error) {
                lookup = null;
            }
        }
        return lookup != null;
    }
}
