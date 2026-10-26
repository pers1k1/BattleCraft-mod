package com.persiki84.battlecraft.bases;

import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.battlecraft.modules.ModuleSwitches;
import com.persiki84.capturepoints.capture.CapturePoint;
import com.persiki84.capturepoints.capture.CapturePointManager;
import com.persiki84.zones.Zone;
import com.persiki84.zones.ZoneLookup;
import com.persiki84.zones.ZoneRegistry;
import com.persiki84.zones.ZoneType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

// WHY: сеть баз команды: её домашние зоны и захваченные ею точки-базы. Модуль, выключенный
// WHY: оператором, свои базы из сети убирает: без зон нет домашней базы, без точек нет точек-баз
public final class BaseNetwork {
    public static final String HOME = "home:";
    public static final String POINT = "point:";

    private BaseNetwork() {}

    public static List<BaseSpot> of(@Nullable String team) {
        List<BaseSpot> spots = new ArrayList<>();
        if (team == null) return spots;
        if (ModuleSwitches.allows(ModuleId.ZONES)) {
            for (Zone zone : ZoneRegistry.all()) {
                if (zone.type() == ZoneType.BASE && zone.belongsTo(team)) spots.add(home(zone));
            }
        }
        if (ModuleSwitches.allows(ModuleId.CAPTURE_POINTS)) {
            for (CapturePoint point : CapturePointManager.getAllPoints()) {
                if (point.isBase() && team.equals(point.getOwnerTeam())) spots.add(point(point));
            }
        }
        return spots;
    }

    private static BaseSpot home(Zone zone) {
        return new BaseSpot(HOME + zone.id(), Component.translatable("battlecraft.bases.home"), true, zone.dimension(),
                zone.area());
    }

    private static BaseSpot point(CapturePoint point) {
        return new BaseSpot(POINT + point.getName(), Component.literal(point.getName()), false,
                point.getDimension().location(), point.getArea());
    }

    @Nullable
    public static BaseSpot find(@Nullable String team, String id) {
        for (BaseSpot spot : of(team)) {
            if (spot.id().equals(id)) return spot;
        }
        return null;
    }

    @Nullable
    public static Zone homeZone(BaseSpot spot) {
        return spot.home() ? ZoneRegistry.byId(spot.id().substring(HOME.length())) : null;
    }

    public static boolean onPointBase(ServerPlayer player) {
        BaseSpot spot = at(player);
        return spot != null && !spot.home();
    }

    // WHY: точка-база внутри домашней зоны считается точкой, как и на клиенте: с неё летят и домой, и
    // WHY: магазин на ней не зависит от того, какая из двух баз нашлась первой
    @Nullable
    public static BaseSpot at(ServerPlayer player) {
        BaseSpot home = null;
        for (BaseSpot spot : of(ZoneLookup.teamNameOf(player))) {
            if (!spot.holds(player.level().dimension().location(), player.getX(), player.getY(), player.getZ())) continue;
            if (!spot.home()) return spot;
            if (home == null) home = spot;
        }
        return home;
    }
}
