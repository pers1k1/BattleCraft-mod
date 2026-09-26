package com.persiki84.zones.client.menu;

import com.persiki84.shared.client.menu.MenuCommands;
import com.persiki84.shared.client.menu.gunsmith.GunBench;
import com.persiki84.shared.gunsmith.GunSmith;
import com.persiki84.zones.client.ClientShopData;
import com.persiki84.zones.client.menu.studio.ShopStudioScreen;
import com.persiki84.zones.network.PacketHandler;
import com.persiki84.zones.network.ShopRefreshPacket;
import com.persiki84.zones.shop.ShopEntry;
import com.persiki84.zones.shop.ShopSection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class ShopGunBench implements GunBench {
    private final String sectionId;
    private final List<ItemStack> guns = new ArrayList<>();
    private final List<String> ids = new ArrayList<>();
    private int seenVersion = -1;

    public ShopGunBench(String sectionId) {
        this.sectionId = sectionId;
    }

    @Override
    public List<ItemStack> guns() {
        int version = ClientShopData.version();
        if (version == seenVersion) return guns;

        seenVersion = version;
        guns.clear();
        ids.clear();
        ShopSection section = ClientShopData.section(sectionId);
        if (section == null) return guns;

        collect(section);
        for (ShopSection child : section.children().values()) {
            collect(child);
        }
        return guns;
    }

    private void collect(ShopSection section) {
        for (ShopEntry entry : section.entries().values()) {
            if (!GunSmith.isGun(entry.stack())) continue;
            guns.add(entry.stack());
            ids.add(entry.id());
        }
    }

    public int positionOf(String entryId) {
        guns();
        return Math.max(0, ids.indexOf(entryId));
    }

    @Override
    public Component title() {
        ShopSection section = ClientShopData.section(sectionId);
        return Component.literal(section == null ? sectionId : section.title());
    }

    @Override
    public void attach(int gun, String option) {
        run(gun, "attach", "\"" + option + "\"");
    }

    @Override
    public void detach(int gun, String slot) {
        run(gun, "detach", slot);
    }

    private void run(int gun, String verb, String tail) {
        guns();
        if (gun < 0 || gun >= ids.size()) return;
        MenuCommands.run("battlecraft shop item " + verb + " " + sectionId + " " + ids.get(gun) + " " + tail,
                ShopStudioScreen.MENU_ID);
    }

    @Override
    public void refresh() {
        PacketHandler.INSTANCE.sendToServer(new ShopRefreshPacket());
    }
}
