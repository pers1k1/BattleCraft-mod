package com.persiki84.battlecraft;

import com.persiki84.battlecraft.client.menu.MenuBackground;
import com.persiki84.battlecraft.client.menu.ScreenDress;
import com.persiki84.shared.client.menu.ScreenDim;
import com.mojang.logging.LogUtils;
import com.persiki84.battlecraft.client.ClientEventHandler;
import com.persiki84.battlecraft.client.ClientOverlayRenderer;
import com.persiki84.battlecraft.client.DiscordRpcManager;
import com.persiki84.battlecraft.client.KeyInputHandler;
import com.persiki84.battlecraft.client.goggles.GogglesClient;
import com.persiki84.battlecraft.client.LauncherTheme;
import com.persiki84.battlecraft.client.combat.CrawlPenalty;
import com.persiki84.battlecraft.client.combat.ParkourRules;
import com.persiki84.battlecraft.client.custom.Customization;
import com.persiki84.battlecraft.client.hud.AmmoHud;
import com.persiki84.battlecraft.client.hud.GogglesHud;
import com.persiki84.battlecraft.client.hud.BottomHud;
import com.persiki84.battlecraft.client.hud.EffectsHud;
import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.battlecraft.compat.iff.IffService;
import com.persiki84.battlecraft.client.hud.AnnounceHud;
import com.persiki84.battlecraft.client.hud.MessageHud;
import com.persiki84.battlecraft.client.hud.ScanHud;
import com.persiki84.battlecraft.client.hud.ToastHud;
import com.persiki84.battlecraft.client.hud.VitalVeil;
import com.persiki84.battlecraft.client.hud.PointsView;
import com.persiki84.battlecraft.client.hud.VoiceHud;
import com.persiki84.battlecraft.client.menu.BattleCraftMenuScreen;
import com.persiki84.battlecraft.client.menu.panel.PanelScreens;
import com.persiki84.battlecraft.menu.BattleCraftMenuState;
import com.persiki84.battlecraft.menu.AnnounceMenuState;
import com.persiki84.battlecraft.menu.ConfigMenuState;
import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.battlecraft.network.PacketHandler;
import com.persiki84.battlecraft.projectile.ProjectileChunkLoader;
import com.persiki84.battlecraft.sound.ModSounds;
import com.persiki84.shared.client.font.MsdfFontSets;
import com.persiki84.shared.client.menu.MenuScreens;
import com.persiki84.shared.client.ui.UiDress;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.menu.MenuNetwork;
import net.minecraftforge.api.distmarker.Dist;
import com.persiki84.battlecraft.client.island.IslandHud;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(BattleCraftMod.MOD_ID)
public class BattleCraftMod {
    public static final String MOD_ID = "battlecraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final double CHAT_LINE_SPACING = 0.55;

    public BattleCraftMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        ModSounds.register(modEventBus);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, HudConfig.SPEC, "battlecraft-hud.toml");
        }

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new IffService());
        BattleCraftManager.getInstance();
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        PacketHandler.register();
        ProjectileChunkLoader.register();
        MenuNetwork.register();
        BattleCraftMenuState.register();
        ModuleMenuStates.register();
        AnnounceMenuState.register();
        ConfigMenuState.register();
        LOGGER.info("BattleCraft Core Mod initialized!");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("BattleCraft Client setup complete");
    }


    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BattleCraftCommands.register(event.getDispatcher());
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
            event.registerBelow(VanillaGuiOverlay.HOTBAR.id(), "battlecraft_veil", VitalVeil.OVERLAY);
            event.registerBelow(VanillaGuiOverlay.HOTBAR.id(), "battlecraft_bottom", BottomHud.OVERLAY);
            event.registerAbove(VanillaGuiOverlay.POTION_ICONS.id(), "battlecraft_effects", EffectsHud.OVERLAY);
            event.registerAboveAll("battlecraft_ammo", AmmoHud.OVERLAY);
            event.registerAboveAll("battlecraft_voice", VoiceHud.OVERLAY);
            event.registerAboveAll("battlecraft_goggles", GogglesHud.OVERLAY);
            event.registerAboveAll("battlecraft_toasts", ToastHud.OVERLAY);
            event.registerAboveAll("battlecraft_messages", MessageHud.OVERLAY);
            event.registerAboveAll("battlecraft_island", IslandHud.OVERLAY);
            event.registerAboveAll("battlecraft_scan", ScanHud.OVERLAY);
            event.registerAboveAll("battlecraft_announce", AnnounceHud.OVERLAY);
            event.registerAboveAll("battlecraft_hud", ClientOverlayRenderer.HUD_OVERLAY);
        }

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(KeyInputHandler.VOTE_YES);
            event.register(KeyInputHandler.VOTE_NO);
            event.register(GogglesClient.TOGGLE);
            event.register(GogglesClient.SWITCH_MODE);
            event.register(GogglesClient.ZOOM);
            event.register(PointsView.SHOW_POINTS);
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            MinecraftForge.EVENT_BUS.register(new ClientEventHandler());
            MinecraftForge.EVENT_BUS.register(new KeyInputHandler());
            ParkourRules.listen();
            CrawlPenalty.listen();
            MenuScreens.register(BattleCraftMenuState.MENU_ID, BattleCraftMenuScreen::new);
            PanelScreens.register();
            ScreenDim.painter((graphics, width, height) ->
                    MenuBackground.shared().render(graphics, width, height));
            UiDress.wardrobe(ScreenDress.shared());
            MsdfFontSets.chooser(Customization::font);
            LauncherTheme.apply();
            UiSound.source(HudConfig::soundVolume);
            UiSound.hoverSource(HudConfig::hoverSound);
            UiSound.schemeSource(Customization::sound);
            DiscordRpcManager.getInstance().identify();
            DiscordRpcManager.getInstance().refresh();
            event.enqueueWork(ClientModEvents::widenChatLines);
        }

        @SubscribeEvent
        public static void onConfigLoad(ModConfigEvent.Loading event) {
            applyHudTheme(event.getConfig());
        }

        @SubscribeEvent
        public static void onConfigReload(ModConfigEvent.Reloading event) {
            applyHudTheme(event.getConfig());
        }

        private static void applyHudTheme(ModConfig config) {
            if (config.getSpec() == HudConfig.SPEC) LauncherTheme.apply();
        }

        // WHY: сохранять здесь нельзя - на первом запуске клавиши модов ещё не прочитаны из файла,
        // WHY: и запись уносит раскладку лаунчера на дефолты; значение уйдёт на диск со штатным
        // WHY: сохранением игры, а каждый запуск ставит его заново
        private static void widenChatLines() {
            Options options = Minecraft.getInstance().options;
            if (options == null || options.chatLineSpacing().get() >= CHAT_LINE_SPACING) return;
            options.chatLineSpacing().set(CHAT_LINE_SPACING);
        }
    }
}
