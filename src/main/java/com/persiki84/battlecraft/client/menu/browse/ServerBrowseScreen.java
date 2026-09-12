package com.persiki84.battlecraft.client.menu.browse;

import com.mojang.logging.LogUtils;
import com.persiki84.shared.client.ui.UiButton;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.EditServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.server.LanServer;
import net.minecraft.client.server.LanServerDetection;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ServerBrowseScreen extends BrowseScreen {
    private static final int FOOTER_WIDTH = 84;
    private static final int FOOTER_GAP = 6;

    private final List<LanServer> lan = new ArrayList<>();
    private final ServerStatusPinger pinger = new ServerStatusPinger();
    private ServerList servers;
    private LanServerDetection.LanServerList beacon;
    private LanServerDetection.LanServerDetector listener;
    private ServerData editing;

    public ServerBrowseScreen(Screen parent) {
        super(Component.translatable("battlecraft.servers.title"), parent);
    }

    @Override
    protected void init() {
        if (servers == null) {
            servers = new ServerList(Minecraft.getInstance());
            servers.load();
        }
        if (listener == null) openBeacon();
        super.init();
    }

    private void openBeacon() {
        beacon = new LanServerDetection.LanServerList();
        try {
            listener = new LanServerDetection.LanServerDetector(beacon);
            listener.start();
        } catch (Exception error) {
            LogUtils.getLogger().warn("[battlecraft] поиск серверов в сети не запущен: {}",
                    String.valueOf(error.getMessage()));
        }
    }

    @Override
    public void tick() {
        pinger.tick();
        if (beacon == null) return;

        List<LanServer> found = beacon.takeDirtyServers();
        if (found == null) return;

        lan.clear();
        lan.addAll(found);
        refill();
    }

    @Override
    public void removed() {
        if (listener != null) {
            listener.interrupt();
            listener = null;
        }
        pinger.removeAll();
        super.removed();
    }

    public void ping(ServerData data) {
        data.pinged = true;
        data.ping = -2L;
        data.motd = CommonComponents.EMPTY;
        data.status = CommonComponents.EMPTY;
        Util.ioPool().execute(() -> reach(data));
    }

    private void reach(ServerData data) {
        try {
            pinger.pingServer(data, () -> Minecraft.getInstance().execute(this::store));
        } catch (UnknownHostException error) {
            data.ping = -1L;
            data.motd = Component.translatable("multiplayer.status.cannot_resolve");
        } catch (Exception error) {
            data.ping = -1L;
            data.motd = Component.translatable("multiplayer.status.cannot_connect");
        }
    }

    private void store() {
        if (servers != null) servers.save();
    }

    @Override
    protected void fill(String filter) {
        String needle = filter.toLowerCase(Locale.ROOT);
        for (int index = 0; index < servers.size(); index++) {
            ServerData data = servers.get(index);
            if (matches(data.name, data.ip, needle)) cards.add(new ServerCard(this, data, false));
        }
        for (LanServer found : lan) {
            ServerData data = new ServerData(found.getMotd(), found.getAddress(), true);
            if (matches(data.name, data.ip, needle)) cards.add(new ServerCard(this, data, true));
        }
    }

    private static boolean matches(String name, String address, String needle) {
        return name.toLowerCase(Locale.ROOT).contains(needle)
                || address.toLowerCase(Locale.ROOT).contains(needle);
    }

    private ServerCard chosen() {
        return picked() instanceof ServerCard card ? card : null;
    }

    @Override
    public void enter() {
        ServerCard card = chosen();
        if (card == null) return;

        ServerData data = card.data();
        ConnectScreen.startConnecting(this, Minecraft.getInstance(),
                ServerAddress.parseString(data.ip), data, false);
    }

    @Override
    protected void buildActions(int left, int top, int width) {
        ServerCard card = chosen();
        UiButton play = action(left, top, width,
                Component.translatable("battlecraft.servers.play"), this::enter);
        play.active = card != null;
        addRenderableWidget(play);

        boolean saved = card != null && !card.lan();
        int small = (width - ACTION_GAP * 2) / 3;
        int y = top + ACTION_HEIGHT + ACTION_GAP;
        addSmall(left, y, small, "battlecraft.servers.edit", saved, this::editChosen);
        addSmall(left + small + ACTION_GAP, y, small, "battlecraft.servers.delete", saved,
                this::deleteChosen);
        addSmall(left + (small + ACTION_GAP) * 2, y, small, "battlecraft.servers.refresh", true,
                this::rescan);
    }

    private void addSmall(int x, int y, int width, String label, boolean ready, Runnable run) {
        UiButton button = action(x, y, width, Component.translatable(label), run);
        button.active = ready;
        addRenderableWidget(button);
    }

    private void rescan() {
        for (int index = 0; index < servers.size(); index++) {
            servers.get(index).pinged = false;
        }
        pinger.removeAll();
        refill();
    }

    private void editChosen() {
        ServerCard card = chosen();
        if (card == null) return;

        ServerData source = card.data();
        editing = new ServerData(source.name, source.ip, false);
        editing.copyFrom(source);
        Minecraft.getInstance().setScreen(new EditServerScreen(this, saved -> applyEdit(source, saved), editing));
    }

    private void applyEdit(ServerData target, boolean saved) {
        if (saved) {
            target.name = editing.name;
            target.ip = editing.ip;
            target.copyFrom(editing);
            servers.save();
        }
        Minecraft.getInstance().setScreen(this);
        if (saved) refill();
    }

    private void deleteChosen() {
        ServerCard card = chosen();
        if (card == null) return;

        ServerData data = card.data();
        Minecraft.getInstance().setScreen(new ConfirmScreen(agreed -> erase(data, agreed),
                Component.translatable("selectServer.deleteQuestion"),
                Component.translatable("selectServer.deleteWarning", data.name),
                Component.translatable("selectServer.deleteButton"), CommonComponents.GUI_CANCEL));
    }

    private void erase(ServerData data, boolean agreed) {
        if (agreed) {
            servers.remove(data);
            servers.save();
            forget();
        }
        Minecraft.getInstance().setScreen(this);
    }

    @Override
    protected void buildFooter(int centerX, int y) {
        int span = FOOTER_WIDTH * 3 + FOOTER_GAP * 2;
        int left = centerX - span / 2;
        addRenderableWidget(action(left, y, FOOTER_WIDTH,
                Component.translatable("battlecraft.servers.add"), this::add));
        addRenderableWidget(action(left + FOOTER_WIDTH + FOOTER_GAP, y, FOOTER_WIDTH,
                Component.translatable("battlecraft.servers.direct"), this::direct));
        addRenderableWidget(action(left + (FOOTER_WIDTH + FOOTER_GAP) * 2, y, FOOTER_WIDTH,
                Component.translatable("battlecraft.menu.back"), this::leave));
    }

    private void add() {
        editing = new ServerData(I18n.get("selectServer.defaultName"), "", false);
        Minecraft.getInstance().setScreen(new EditServerScreen(this, this::applyAdd, editing));
    }

    private void applyAdd(boolean saved) {
        if (saved) {
            ServerData known = servers.unhide(editing.ip);
            if (known != null) {
                known.copyNameIconFrom(editing);
            } else {
                servers.add(editing, false);
            }
            servers.save();
            forget();
        }
        Minecraft.getInstance().setScreen(this);
    }

    private void direct() {
        editing = new ServerData(I18n.get("selectServer.defaultName"), "", false);
        Minecraft.getInstance().setScreen(new DirectJoinServerScreen(this, this::applyDirect, editing));
    }

    private void applyDirect(boolean agreed) {
        if (!agreed) {
            Minecraft.getInstance().setScreen(this);
            return;
        }
        ServerData known = servers.get(editing.ip);
        ServerData target = known != null ? known : editing;
        if (known == null) {
            servers.add(editing, true);
            servers.save();
        }
        ConnectScreen.startConnecting(this, Minecraft.getInstance(),
                ServerAddress.parseString(target.ip), target, false);
    }

    @Override
    protected Component emptyHint() {
        return Component.translatable("battlecraft.servers.empty");
    }

    @Override
    protected Component searchHint() {
        return Component.translatable("battlecraft.servers.search");
    }

    @Override
    protected void paintPreview(GuiGraphics graphics, int left, int top, int width, int height) {
        ServerCard card = chosen();
        if (card == null) {
            PreviewColumn.nothing(graphics, this.font, left, top, width, height);
            return;
        }
        ServerData data = card.data();
        PreviewColumn column = new PreviewColumn(graphics, this.font, left, top, width, height);
        column.head(card, Component.literal(data.name));
        column.fact("battlecraft.servers.address", Component.literal(data.ip));
        column.fact("battlecraft.servers.players", counts(card));
        column.fact("battlecraft.servers.ping", delay(card));
        column.fact("battlecraft.servers.version", data.version);
    }

    private static Component counts(ServerCard card) {
        ServerData data = card.data();
        if (!card.answered() || data.players == null) {
            return Component.translatable("battlecraft.servers.pinging");
        }
        return Component.literal(data.players.online() + "/" + data.players.max());
    }

    private static Component delay(ServerCard card) {
        if (!card.answered()) return Component.translatable("battlecraft.servers.pinging");
        if (card.data().ping < 0L) return Component.translatable("battlecraft.servers.offline");
        return Component.translatable("battlecraft.servers.ms", card.data().ping);
    }
}
