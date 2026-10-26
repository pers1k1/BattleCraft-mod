package com.persiki84.battlecraft.client.bases;

import com.persiki84.battlecraft.bases.BaseNetwork;
import com.persiki84.battlecraft.bases.C2SBaseRequestPacket;
import com.persiki84.battlecraft.bases.S2CBasesPacket;
import com.persiki84.battlecraft.network.PacketHandler;
import com.persiki84.zones.Zone;
import com.persiki84.zones.ZoneType;
import com.persiki84.zones.client.render.VisibleZones;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// WHY: клиент держит только последний список баз своей команды от сервера: экран смерти, перелёт и
// WHY: клавиша магазина на точке читают его, а сервер всё равно проверяет каждую заявку. Имена точек-баз
// WHY: лежат набором: подсказка HUD спрашивает их каждый кадр
public final class ClientBases {
    private static List<S2CBasesPacket.Entry> bases = List.of();
    private static final Set<String> ids = new HashSet<>();
    private static final Set<String> pointNames = new HashSet<>();
    private static boolean respawn;
    private static boolean travel;
    private static boolean shop;
    private static int version;
    private static int respawnVersion;

    private ClientBases() {}

    public static void accept(S2CBasesPacket packet) {
        Set<String> previousPoints = Set.copyOf(pointNames);
        boolean previousRespawn = respawn;
        bases = List.copyOf(packet.bases);
        ids.clear();
        pointNames.clear();
        for (S2CBasesPacket.Entry entry : bases) {
            ids.add(entry.id());
            if (!entry.home()) pointNames.add(entry.id().substring(BaseNetwork.POINT.length()));
        }
        respawn = packet.respawn;
        if (respawn != previousRespawn || !pointNames.equals(previousPoints)) respawnVersion++;
        travel = packet.travel;
        shop = packet.shop;
        version++;
    }

    public static void forget() {
        bases = List.of();
        ids.clear();
        pointNames.clear();
        respawn = false;
        travel = false;
        shop = false;
        version++;
        respawnVersion++;
    }

    public static List<S2CBasesPacket.Entry> bases() {
        return bases;
    }

    public static int version() {
        return version;
    }

    // WHY: экран смерти пересобирается только от смены точек-баз или правила возрождения: пересборка
    // WHY: заново заводит ванильную задержку кнопок, а список шлётся и на любые другие изменения
    public static int respawnVersion() {
        return respawnVersion;
    }

    public static boolean respawn() {
        return respawn && !pointNames.isEmpty();
    }

    // WHY: перелёт идёт только с точкой-базой на одном из концов, поэтому без точек-баз его нет вовсе
    public static boolean travel() {
        return travel && !pointNames.isEmpty();
    }

    public static boolean ownPointBase(String pointName) {
        return pointNames.contains(pointName);
    }

    public static boolean shopOnPoint(String pointName) {
        return shop && pointNames.contains(pointName);
    }

    // WHY: точка-база внутри домашней зоны считается точкой: с неё летят и домой
    @Nullable
    public static String currentBaseId() {
        Player player = Minecraft.getInstance().player;
        if (player == null || ids.isEmpty()) return null;
        String home = null;
        for (Zone zone : VisibleZones.current()) {
            if (!zone.area().contains(player.getX(), player.getY(), player.getZ(), 0.0)) continue;
            if (zone.type() == ZoneType.CAPTURE_POINT && pointNames.contains(zone.id())) {
                return BaseNetwork.POINT + zone.id();
            }
            String homeId = BaseNetwork.HOME + zone.id();
            if (zone.type() == ZoneType.BASE && ids.contains(homeId)) home = homeId;
        }
        return home;
    }

    public static void requestSpawn(String id) {
        PacketHandler.INSTANCE.sendToServer(new C2SBaseRequestPacket(C2SBaseRequestPacket.Kind.SPAWN, id));
    }

    public static void requestTravel(String id) {
        PacketHandler.INSTANCE.sendToServer(new C2SBaseRequestPacket(C2SBaseRequestPacket.Kind.TRAVEL, id));
    }
}
