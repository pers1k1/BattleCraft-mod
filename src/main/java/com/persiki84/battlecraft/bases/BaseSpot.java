package com.persiki84.battlecraft.bases;

import com.persiki84.shared.zone.ZoneArea;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

// WHY: база команды одним видом: домашняя зона и захваченная точка-база для возрождения и перелёта
// WHY: равны. Мир null у зоны значит «тот, где игрок», как у возрождения на домашней базе
public record BaseSpot(String id, Component name, boolean home, @Nullable ResourceLocation dimension, ZoneArea area) {
    public boolean holds(ResourceLocation world, double x, double y, double z) {
        return (dimension == null || dimension.equals(world)) && area.contains(x, y, z);
    }
}
