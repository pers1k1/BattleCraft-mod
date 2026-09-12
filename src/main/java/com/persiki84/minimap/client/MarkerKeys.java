package com.persiki84.minimap.client;

import com.persiki84.minimap.network.MapMarkerSyncPacket;
import com.persiki84.zones.mark.MapMark;

// WHY: ключ вспышки один на обе карты: миникарта и полный экран рисуют ту же метку, и разъезд
// WHY: ключей означал бы две разные волны на одном событии
public final class MarkerKeys {
    private static final String MARK = "mark:";
    private static final String TEAM = ":team";
    private static final String PRIVATE = ":own";

    private MarkerKeys() {}

    public static String of(MapMark mark) {
        return MARK + mark.id();
    }

    public static String of(MapMarkerSyncPacket.MarkerData marker) {
        return marker.playerId + (marker.isTeam ? TEAM : PRIVATE);
    }
}
