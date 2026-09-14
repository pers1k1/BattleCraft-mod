package com.persiki84.battlecraft.client.hud;

import com.persiki84.battlecraft.client.custom.Customization;
import com.persiki84.shared.client.ui.UiGlassStyle;
import com.persiki84.battlecraft.client.custom.HudLayout;
import com.persiki84.battlecraft.client.island.IslandModel;
import com.persiki84.battlecraft.client.media.MediaWatch;
import com.persiki84.battlecraft.client.voice.RadioChirp;
import com.persiki84.battlecraft.client.voice.RadioTalk;
import com.persiki84.shared.client.menu.ScreenVeil;
import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.client.ClientGameRules;
import com.persiki84.battlecraft.client.menu.ScreenSkin;
import com.persiki84.battlecraft.combat.Adrenaline;
import com.persiki84.battlecraft.rules.GameRule;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiBackdrop;
import com.persiki84.shared.client.ui.UiCamera;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiLens;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiQuality;
import com.persiki84.shared.client.ui.UiVital;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class HudEvents {
    private static final int HUD_STACK_HEIGHT = 49;
    private static final int STATS_INTERVAL = 20;

    private static int statsTicks = STATS_INTERVAL;

    private static final Set<ResourceLocation> STATUS_OVERLAYS = Set.of(
            VanillaGuiOverlay.PLAYER_HEALTH.id(),
            VanillaGuiOverlay.ARMOR_LEVEL.id(),
            VanillaGuiOverlay.FOOD_LEVEL.id(),
            VanillaGuiOverlay.AIR_LEVEL.id(),
            VanillaGuiOverlay.MOUNT_HEALTH.id(),
            VanillaGuiOverlay.JUMP_BAR.id(),
            VanillaGuiOverlay.EXPERIENCE_BAR.id()
    );

    private static final Set<ResourceLocation> HOTBAR_OVERLAYS = Set.of(
            VanillaGuiOverlay.HOTBAR.id(),
            VanillaGuiOverlay.ITEM_NAME.id()
    );

    private static final Set<ResourceLocation> FOREIGN_OVERLAYS = Set.of(
            new ResourceLocation("tacz", "tac_kill_amount_overlay"),
            new ResourceLocation("superbwarfare", "kill_message")
    );

    private static final Set<String> FOREIGN_NAMESPACES = Set.of("parcool");

    private static final Set<ResourceLocation> MEASURED_OVERLAYS = Set.of(
            new ResourceLocation("superbwarfare", "vehicle_hud")
    );

    private static final Set<ResourceLocation> MESSAGE_OVERLAYS = Set.of(
            VanillaGuiOverlay.TITLE_TEXT.id(),
            VanillaGuiOverlay.RECORD_OVERLAY.id()
    );

    private HudEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Customization.flush();
        HudState.tick(Minecraft.getInstance());
        islandTick();
        if (Minecraft.getInstance().level == null) {
            KillFeedBridge.forget();
            return;
        }
        ScanBridge.tick();
        KillFeedBridge.tick();
        pollHeld();
    }

    private static void islandTick() {
        MediaWatch.want(HudConfig.island() && HudConfig.islandMedia());
        MediaWatch.tick();
        if (++statsTicks < STATS_INTERVAL) return;

        statsTicks = 0;
        IslandModel.pollStats();
    }

    private static void pollHeld() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            StaminaBridge.clear();
            AmmoHud.forget();
            VoiceBridge.clear();
            RadioChirp.forget();
            RadioTalk.forget();
            return;
        }
        StaminaBridge.poll(mc.player);
        AmmoHud.poll(mc.player);
        VoiceBridge.poll();
        RadioChirp.tick(mc.player);
        RadioTalk.tick(mc.player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onVisualsTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !HudConfig.heartbeatSync()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            VisualsBridge.tick(mc.player);
        }
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        UiFrame.advance();
        UiAccent.advance(UiFrame.delta());
        UiPalette.advance(UiFrame.delta());
        HudInk.advance(UiFrame.delta());
        HudLayout.washTints(UiFrame.delta());
        ScreenVeil.advance(UiFrame.delta());
        ScreenSkin.advance(UiFrame.delta());
        ChatSkin.advance(UiFrame.delta());
        com.persiki84.battlecraft.client.hudedit.HudEditSession.advance(UiFrame.delta());
        UiBackdrop.setEnabled(HudConfig.glassRefraction() && UiQuality.blurring());
        com.persiki84.shared.client.ui.UiReveal.allow(HudConfig.menuIntro());
        com.persiki84.shared.client.ui.UiPlane.allow(HudConfig.worldPanel());
        com.persiki84.shared.client.ui.UiReveal.probe(HudConfig.revealProbe());
        UiLens.useDispersion(UiGlassStyle.dispersion());
        UiLens.useBrightness(UiGlassStyle.brightness());
        traceVitals();
    }

    private static void traceVitals() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            StaminaBridge.clear();
            UiVital.rest(UiFrame.delta());
            return;
        }
        UiVital.advance(mc.player.getHealth(), mc.player.getMaxHealth(),
                StaminaBridge.exertion(), Adrenaline.rushing(mc.player) ? 1.0f : 0.0f, UiFrame.delta());
    }

    @SubscribeEvent
    public static void onLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        UiCamera.observe(event.getProjectionMatrix(), event.getCamera());
    }

    @SubscribeEvent
    public static void onAboveHud(RenderGuiEvent.Post event) {
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        VeilOverlay.render(event.getGuiGraphics(), width, height);
        com.persiki84.shared.client.ui.UiFarewell.render(event.getGuiGraphics(), width, height,
                event.getPartialTick());
        com.persiki84.battlecraft.client.menu.WidgetRestyle.settle();
        if (HudConfig.screenProbe()) probeScreens(event.getGuiGraphics());
        if (com.persiki84.battlecraft.client.hudedit.HudEditSession.fading()) {
            com.persiki84.battlecraft.client.hudedit.HudEditLayer.render(event.getGuiGraphics());
        }
    }

    private static void probeScreens(net.minecraft.client.gui.GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        boolean burning = com.persiki84.shared.client.ui.UiFarewell.burning();
        com.persiki84.shared.client.ui.UiRender.textScaled(graphics, mc.font,
                net.minecraft.network.chat.Component.literal("burn=" + (burning ? 1 : 0)
                        + " screen=" + (mc.screen == null ? "null" : mc.screen.getClass().getSimpleName())),
                4.0f, 4.0f, 1.0f, 0xFFFFE066, false);
        com.persiki84.shared.client.ui.UiRender.textScaled(graphics, mc.font,
                net.minecraft.network.chat.Component.literal(
                        com.persiki84.battlecraft.client.menu.WidgetRestyle.report()),
                4.0f, 16.0f, 1.0f, 0xFFFFE066, false);
        com.persiki84.shared.client.ui.UiRender.textScaled(graphics, mc.font,
                net.minecraft.network.chat.Component.literal(
                        com.persiki84.battlecraft.client.menu.WidgetRestyle.strayReport()),
                4.0f, 28.0f, 1.0f, 0xFFFF7A66, false);
        com.persiki84.battlecraft.client.menu.WidgetRestyle.markStray(graphics);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        ForeignHud.beginFrame();
        float ui = com.persiki84.shared.client.ui.UiScale.factor();
        HudLayout.beginFrame(HudClearance.lift(),
                event.getWindow().getGuiScaledWidth() / ui, event.getWindow().getGuiScaledHeight() / ui);
        if (ScreenSkin.seeThrough(Minecraft.getInstance().screen)) {
            UiBackdrop.suspend();
        } else {
            UiBackdrop.capture();
        }
        BossBarHud.beginFrame(UiFrame.delta());

        Minecraft mc = Minecraft.getInstance();
        if (HudConfig.statusBars() && mc.gui instanceof ForgeGui forgeGui) {
            forgeGui.leftHeight = HUD_STACK_HEIGHT;
            forgeGui.rightHeight = HUD_STACK_HEIGHT;
        }
    }

    @SubscribeEvent
    public static void onOverlayPre(RenderGuiOverlayEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ResourceLocation id = event.getOverlay().id();
        if (replaced(id, mc)) {
            event.setCanceled(true);
            return;
        }
        ForeignHud.watch(MEASURED_OVERLAYS.contains(id));
    }

    @SubscribeEvent
    public static void onOverlayPost(RenderGuiOverlayEvent.Post event) {
        ForeignHud.watch(false);
    }

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        ForeignHud.commit();
    }

    private static boolean replaced(ResourceLocation id, Minecraft mc) {
        if (VanillaGuiOverlay.VIGNETTE.id().equals(id)) return blindingVignette(mc);
        if (FOREIGN_OVERLAYS.contains(id)) return true;
        if (FOREIGN_NAMESPACES.contains(id.getNamespace())) return true;
        if (MESSAGE_OVERLAYS.contains(id)) return GuiBridge.available();
        if (HOTBAR_OVERLAYS.contains(id)) return !mc.player.isSpectator();
        if (STATUS_OVERLAYS.contains(id)) return HudConfig.statusBars();
        return HudConfig.effectChips() && VanillaGuiOverlay.POTION_ICONS.id().equals(id);
    }

    private static boolean blindingVignette(Minecraft mc) {
        return mc.player.isSpectator() && ClientGameRules.allows(GameRule.SPECTATOR_LIGHT);
    }
}
