package com.persiki84.battlecraft.client.menu.custom;

import com.persiki84.battlecraft.client.custom.Customization;
import com.persiki84.battlecraft.client.custom.FontLook;
import com.persiki84.battlecraft.client.island.AvatarPicture;
import com.persiki84.battlecraft.client.island.AvatarSource;
import com.persiki84.shared.client.font.FontShape;
import com.persiki84.shared.client.menu.PickRow;
import com.persiki84.shared.client.ui.UiCue;
import com.persiki84.shared.client.ui.UiMotionSet;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiSoundScheme;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

// WHY: строки выбора из перечислений: подписи вариантов собираются один раз при сборке строки,
// WHY: а применение каждого варианта сразу сохраняет настройки, как и прежде
final class Picks {
    private Picks() {}

    static PickRow font(RowKit kit) {
        List<Component> options = new ArrayList<>();
        for (FontShape shape : FontShape.values()) {
            options.add(Component.translatable(shape.translationKey()));
        }
        return kit.pick("battlecraft.custom.font", options, () -> Customization.font().ordinal(), picked -> {
            FontLook.choose(FontShape.values()[picked]);
            Customization.save();
        });
    }

    static PickRow sound(RowKit kit) {
        List<Component> options = new ArrayList<>();
        for (UiSoundScheme scheme : UiSoundScheme.values()) {
            options.add(Component.translatable(scheme.translationKey()));
        }
        return kit.pick("battlecraft.custom.sound", options, () -> Customization.sound().ordinal(), picked -> {
            Customization.sound(UiSoundScheme.values()[picked]);
            Customization.save();
            UiSound.preview(Customization.sound(), UiCue.PRESS);
        });
    }

    static PickRow motion(RowKit kit) {
        List<Component> options = new ArrayList<>();
        for (UiMotionSet set : UiMotionSet.values()) {
            options.add(Component.translatable(set.translationKey()));
        }
        return kit.pick("battlecraft.custom.motion", options, () -> Customization.motion().ordinal(), picked -> {
            Customization.motion(UiMotionSet.values()[picked]);
            Customization.save();
        });
    }

    // WHY: картинка, которой ещё нет, сначала выбирается диалогом: вариант без файла показал бы
    // WHY: в острове пустое место вместо аватара
    static PickRow avatar(RowKit kit) {
        List<Component> options = new ArrayList<>();
        for (AvatarSource source : AvatarSource.values()) {
            options.add(Component.translatable(source.translationKey()));
        }
        return kit.pick("battlecraft.custom.island.avatar_source", options, () -> Customization.avatar().ordinal(),
                picked -> {
                    AvatarSource chosen = AvatarSource.values()[picked];
                    if (chosen == AvatarSource.PICTURE && !AvatarPicture.present()) {
                        kit.host().pickAvatarPicture();
                        return;
                    }
                    Customization.avatar(chosen);
                    Customization.save();
                });
    }
}
