package com.persiki84.shared.gunsmith;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class GunSmith {

    private GunSmith() {}

    public static boolean isGun(ItemStack stack) {
        return TaczSmith.isGun(stack) || SuperbSmith.isGun(stack);
    }

    // WHY: модели SuperbWarfare выбирают кости обвесов по предмету в главной руке игрока, а не по
    // WHY: рисуемому стаку: предпросмотр такого оружия обязан на время отрисовки держать его в руке
    public static boolean readsHeldItem(ItemStack stack) {
        return SuperbSmith.isGun(stack);
    }

    public static List<String> options(ItemStack gun) {
        if (TaczSmith.isGun(gun)) return TaczSmith.options(gun);
        return SuperbSmith.isGun(gun) ? SuperbSmith.options(gun) : List.of();
    }

    public static List<String> installed(ItemStack gun) {
        if (TaczSmith.isGun(gun)) return TaczSmith.installed(gun);
        return SuperbSmith.isGun(gun) ? SuperbSmith.installed(gun) : List.of();
    }

    public static Outcome attach(ItemStack gun, String option) {
        if (TaczSmith.isGun(gun)) return TaczSmith.attach(gun, option);
        return SuperbSmith.isGun(gun) ? SuperbSmith.attach(gun, option) : Outcome.NOT_A_GUN;
    }

    public static Outcome detach(ItemStack gun, String slot) {
        if (TaczSmith.isGun(gun)) return TaczSmith.detach(gun, slot);
        return SuperbSmith.isGun(gun) ? SuperbSmith.detach(gun, slot) : Outcome.NOT_A_GUN;
    }

    public static ItemStack preview(String option) {
        return TaczSmith.preview(option);
    }

    public static Component optionName(String option) {
        ItemStack preview = preview(option);
        return preview.isEmpty() ? Component.literal(option) : preview.getHoverName();
    }

    public static Component slotName(String slot) {
        return Component.translatable("zones.attachment.slot." + slot);
    }

    public static List<String> notes(String option) {
        return TaczSmith.notes(option);
    }

    public static Component notesLabel(String option) {
        List<String> lines = notes(option);
        return lines.isEmpty() ? Component.empty() : Component.literal(String.join("  ", lines));
    }

    public static List<GunStat> stats(ItemStack gun) {
        return TaczSmith.stats(gun);
    }

    public static List<GunAmmo> ammo(ItemStack gun) {
        if (TaczSmith.isGun(gun)) return TaczSmith.ammo(gun);
        return SuperbSmith.isGun(gun) ? SuperbSmith.ammo(gun) : List.of();
    }

    public static List<GunAmmo> vehicleAmmo(EntityType<?> vehicle) {
        return SuperbSmith.vehicleAmmo(vehicle);
    }

    public static VehiclePower vehiclePower(EntityType<?> vehicle) {
        return SuperbSmith.vehiclePower(vehicle);
    }

    public static List<GunSlot> slots(ItemStack gun) {
        if (TaczSmith.isGun(gun)) return TaczSmith.slots(gun);
        return SuperbSmith.isGun(gun) ? SuperbSmith.slots(gun) : List.of();
    }
}
