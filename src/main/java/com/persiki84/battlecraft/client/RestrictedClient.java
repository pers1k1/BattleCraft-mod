package com.persiki84.battlecraft.client;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.rules.GameRule;
import com.persiki84.battlecraft.mixin.DebugRendererAccessor;
import net.minecraft.client.CameraType;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.ModListScreen;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class RestrictedClient {
    private static final String PACKS_KEY = "options.resourcepack";

    private static final Set<String> FORBIDDEN_SCREENS = Set.of(
            "de.maxhenkel.voicechat.gui.group.GroupScreen",
            "de.maxhenkel.voicechat.gui.group.JoinGroupScreen",
            "de.maxhenkel.voicechat.gui.CreateGroupScreen"
    );

    // WHY: настройки чужих модов правятся файлом, а не в игре: пак раздаёт свои значения, и
    // WHY: игрок, поменявший их на ходу, играет по другим правилам. Экраны режутся по классу,
    // WHY: а не по клавише: к ним ведёт ещё и список модов, а клавишу можно назначить заново.
    // WHY: сверено по jar-ам сборки: TACZ и SuperbWarfare рисуют настройки чужим Cloth Config
    // WHY: (отсюда начало имени, а не точное: экран строится подклассами), ParCool своим экраном
    private static final Set<String> CONFIG_SCREENS = Set.of(
            "me.shedaniel.clothconfig2",
            "com.alrex.parcool.client.gui.ParCoolSettingScreen",
            "com.alrex.parcool.client.gui.Setting"
    );

    private RestrictedClient() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        ServerLocks.refresh();
        ClientGuard.watch();
        ConfigGuard.tick();
        if (!ServerRules.restricted()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (ClientGameRules.allows(GameRule.BLOCK_DEBUG_KEYS)) silenceDebugRenders(minecraft);
        if (ClientGameRules.allows(GameRule.FIRST_PERSON_ONLY)) keepFirstPerson(minecraft);
        if (ClientGameRules.allows(GameRule.BLOCK_SUBTITLES)) silenceSubtitles(minecraft);
    }

    private static void silenceSubtitles(Minecraft minecraft) {
        if (!minecraft.options.showSubtitles().get()) return;

        minecraft.options.showSubtitles().set(false);
        minecraft.options.save();
    }

    private static void keepFirstPerson(Minecraft minecraft) {
        while (minecraft.options.keyTogglePerspective.consumeClick()) {
            continue;
        }
        if (minecraft.options.getCameraType() == CameraType.FIRST_PERSON) return;

        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        minecraft.levelRenderer.needsUpdate();
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        Options options = Minecraft.getInstance().options;
        if (screen instanceof OptionsScreen) {
            for (GuiEventListener child : screen.children()) {
                if (child instanceof AbstractWidget widget && named(widget, PACKS_KEY)) widget.active = false;
            }
        }
        if (ServerRules.restricted() && ClientGameRules.allows(GameRule.BLOCK_SUBTITLES)) {
            lock(screen, options.showSubtitles());
        }
        if (ServerLocks.glintLocked()) {
            lock(screen, options.glintSpeed(), options.glintStrength());
        }
    }

    // WHY: строки настроек живут не в children экрана, а внутри OptionsList, и он же умеет найти
    // WHY: виджет по самой опции: поиск по ключу перевода мимо списка не находил вообще ничего
    private static void lock(Screen screen, OptionInstance<?>... locked) {
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof OptionsList list)) continue;

            for (OptionInstance<?> option : locked) {
                AbstractWidget widget = list.findOption(option);
                if (widget == null) continue;

                widget.active = false;
                widget.setTooltip(Tooltip.create(ServerLocks.reason()));
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen opened = event.getNewScreen();
        if (opened instanceof PackSelectionScreen && ClientGameRules.allows(GameRule.BLOCK_RESOURCE_PACKS)) {
            event.setCanceled(true);
            return;
        }
        if (!ServerRules.restricted() || !forbidden(opened)) return;
        event.setCanceled(true);
    }

    private static void silenceDebugRenders(Minecraft mc) {
        if (mc.getEntityRenderDispatcher().shouldRenderHitBoxes()) {
            mc.getEntityRenderDispatcher().setRenderHitBoxes(false);
        }
        if (mc.debugRenderer instanceof DebugRendererAccessor debug) {
            debug.battlecraft$setRenderChunkborder(false);
        }
    }

    private static boolean named(AbstractWidget widget, String key) {
        return widget.getMessage().getContents() instanceof TranslatableContents contents
                && key.equals(contents.getKey());
    }

    private static boolean forbidden(Screen screen) {
        if (screen == null) return false;
        if (screen instanceof AdvancementsScreen) return ClientGameRules.allows(GameRule.BLOCK_ADVANCEMENTS);
        if (screen instanceof ModListScreen) return ClientGameRules.allows(GameRule.BLOCK_MOD_LIST);
        String name = screen.getClass().getName();
        if (configScreen(name)) return ClientGameRules.allows(GameRule.BLOCK_MOD_CONFIGS);
        return FORBIDDEN_SCREENS.contains(name) && ClientGameRules.allows(GameRule.BLOCK_VOICE_GROUPS);
    }

    private static boolean configScreen(String name) {
        for (String prefix : CONFIG_SCREENS) {
            if (name.startsWith(prefix)) return true;
        }
        return false;
    }
}
