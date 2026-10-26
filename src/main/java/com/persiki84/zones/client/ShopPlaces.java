package com.persiki84.zones.client;

import com.persiki84.battlecraft.client.bases.ClientBases;
import com.persiki84.zones.Zone;
import com.persiki84.zones.ZoneType;

import javax.annotation.Nullable;

// WHY: магазин открывается в зоне магазина и на своей точке-базе: клавиша, подсказка и автозакрытие
// WHY: спрашивают одно место, иначе на точке-базе магазин открывался бы и тут же захлопывался. Когда
// WHY: магазин на точках-базах выключен, а перелёт нет, та же клавиша на точке-базе открывает перелёт
public final class ShopPlaces {
    private ShopPlaces() {}

    public static boolean here() {
        return isShop(ZoneOccupancy.current());
    }

    public static boolean isShop(@Nullable Zone zone) {
        if (zone == null) return false;
        if (zone.type() == ZoneType.SHOP) return true;
        return zone.type() == ZoneType.CAPTURE_POINT && ClientBases.shopOnPoint(zone.id());
    }

    public static boolean travelOnly(@Nullable Zone zone) {
        if (zone == null || zone.type() != ZoneType.CAPTURE_POINT || isShop(zone)) return false;
        return ClientBases.travel() && ClientBases.ownPointBase(zone.id());
    }
}
