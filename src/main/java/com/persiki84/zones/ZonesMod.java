package com.persiki84.zones;

import com.persiki84.zones.client.ClientMarkData;
import com.persiki84.zones.client.ClientModifierData;
import com.persiki84.zones.client.ClientShopData;
import com.persiki84.zones.client.ClientZoneData;
import com.persiki84.zones.client.menu.VehiclePreview;
import com.persiki84.zones.network.PacketHandler;
import com.persiki84.zones.network.ZoneRemovePacket;
import com.persiki84.itemmodifiers.ModifierConfig;
import com.persiki84.zones.network.ModifierSyncPacket;
import com.persiki84.zones.network.ShopSyncPacket;
import com.persiki84.zones.mark.MapMark;
import com.persiki84.zones.mark.MarkMenuState;
import com.persiki84.zones.mark.MarkRegistry;
import com.persiki84.zones.mark.MarkTeamWatch;
import com.persiki84.zones.network.MarkSyncAllPacket;
import com.persiki84.zones.network.ZoneSyncAllPacket;
import com.persiki84.zones.network.ZoneUpsertPacket;
import com.persiki84.shared.client.menu.MenuScreens;
import com.persiki84.shared.menu.MenuStates;
import com.persiki84.zones.client.menu.MarkManagerScreen;
import com.persiki84.zones.client.menu.ShopAdminScreen;
import com.persiki84.zones.client.menu.ZoneManagerScreen;
import com.persiki84.zones.shop.ShopCatalog;
import com.persiki84.zones.shop.ShopViewer;
import com.persiki84.zones.shop.StockScope;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Team;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.PacketDistributor;

@Mod(ZonesMod.MOD_ID)
public class ZonesMod {
    public static final String MOD_ID = "zones";
    public static final String ZONES_MENU_ID = "zones";

    private static final int RESTOCK_INTERVAL_TICKS = 20;

    private int restockTicks;

    public ZonesMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        PacketHandler.register();
        MenuStates.register(ZONES_MENU_ID, 2, player -> new CompoundTag());
        MarkMenuState.register();
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        MenuScreens.register(ZONES_MENU_ID, ZoneManagerScreen::new);
        MenuScreens.register(ShopAdminScreen.MENU_ID, ShopAdminScreen::new);
        MenuScreens.register(MarkMenuState.MENU_ID, MarkManagerScreen::new);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ZoneRegistry.bind(event.getServer().overworld());
        MarkRegistry.bind(event.getServer().overworld());
        ShopCatalog.bind(event.getServer().overworld());
        syncEveryone(event.getServer());
        syncShopToEveryone(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        ZoneRegistry.persist();
        ZoneRegistry.unbind();
        MarkRegistry.persist();
        MarkRegistry.unbind();
        MarkTeamWatch.reset();
        ShopCatalog.persist();
        ShopCatalog.unbind();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        restockTicks++;
        if (restockTicks < RESTOCK_INTERVAL_TICKS) return;
        restockTicks = 0;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) MarkTeamWatch.resyncChanged(server, ZonesMod::syncTeamScoped);

        if (!ShopCatalog.restockDue(System.currentTimeMillis())) {
            ShopCatalog.flushDue();
            return;
        }
        ShopCatalog.persist();
        syncShopToEveryone(server);
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MarkTeamWatch.forget(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncTo(player);
        }
    }

    @SubscribeEvent
    public void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncTo(player);
        }
    }

    public static void syncTo(ServerPlayer player) {
        PacketHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new ZoneSyncAllPacket(ZoneRegistry.all()));
        syncShopTo(player);
        PacketHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                modifiers());
        syncMarksTo(player);
    }

    // WHY: снимок модификаторов уходил только на входе и на смене измерения, и после правки
    // WHY: командой карточка товара и подсказка предмета врали до перезахода игрока
    public static void syncModifiersToAll() {
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), modifiers());
    }

    private static ModifierSyncPacket modifiers() {
        return new ModifierSyncPacket(ModifierConfig.getPotionEffects(), ModifierConfig.getAttributes());
    }

    // WHY: оператору уходят все метки, включая скрытые от его команды: иначе спрятанную надпись
    // WHY: он не увидит на карте и не сможет ни подвинуть, ни вернуть - как полный каталог магазина
    public static void syncMarksTo(ServerPlayer player) {
        MarkTeamWatch.remember(player);
        boolean full = player.hasPermissions(2);
        List<MapMark> visible = new ArrayList<>();
        for (MapMark mark : MarkRegistry.all()) {
            if (full || mark.visibleTo(player.getTeam())) visible.add(mark);
        }
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new MarkSyncAllPacket(visible));
    }

    private static void syncTeamScoped(ServerPlayer player) {
        syncMarksTo(player);
        syncShopTo(player);
    }

    public static void syncMarksToEveryone(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncMarksTo(player);
        }
    }

    // WHY: каталог у каждого свой, потому что отделы и товары ограничиваются командами;
    // WHY: оператору уходит полный, иначе ему нечего было бы править в админском экране
    public static void syncShopTo(ServerPlayer player) {
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                new ShopSyncPacket(ShopCatalog.sections(), ShopViewer.of(player), player.hasPermissions(2)));
    }

    // WHY: личный и командный склад после покупки меняются только у покупателя и его команды,
    // WHY: поэтому весь каталог всему серверу рассылается лишь на общем запасе
    public static void syncShopAfterPurchase(ServerPlayer buyer, StockScope scope) {
        MinecraftServer server = buyer.getServer();
        if (server == null) return;

        if (scope == StockScope.PLAYER) {
            syncShopTo(buyer);
            return;
        }
        if (scope == StockScope.SHARED) {
            syncShopToEveryone(server);
            return;
        }

        Team team = buyer.getTeam();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (team == null ? player == buyer : team == player.getTeam()) syncShopTo(player);
        }
    }

    public static void syncShopToEveryone(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncShopTo(player);
        }
    }

    public static void syncEveryone(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncTo(player);
        }
    }

    public static void broadcastUpsert(Zone zone) {
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new ZoneUpsertPacket(zone));
    }

    public static void broadcastRemoval(String zoneId) {
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new ZoneRemovePacket(zoneId));
    }

    public static void clearClientState() {
        ClientZoneData.clear();
        ClientMarkData.clear();
        ClientShopData.clear();
        ClientModifierData.clear();
        VehiclePreview.forget();
    }

}
