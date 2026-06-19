package com.persiki84.battlecraft.client;

import com.persiki84.battlecraft.BattleCraftManager;
import dev.firstdark.rpc.enums.ActivityType;
import dev.firstdark.rpc.enums.ErrorCode;
import dev.firstdark.rpc.DiscordRpc;
import dev.firstdark.rpc.handlers.DiscordEventHandler;
import dev.firstdark.rpc.models.DiscordJoinRequest;
import dev.firstdark.rpc.models.DiscordRichPresence;
import dev.firstdark.rpc.models.User;
import net.minecraft.client.Minecraft;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DiscordRpcManager {
    private static final String OWNER_ID = "650390226643976213";
    private static final String CLIENT_ID = "1510061496590401688";

    private static DiscordRpcManager instance;
    private final DiscordRpc rpc = new DiscordRpc();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean initialized = false;
    private volatile boolean isOwner = false;

    private DiscordRpcManager() {}

    public static DiscordRpcManager getInstance() {
        if (instance == null) {
            instance = new DiscordRpcManager();
        }
        return instance;
    }

    public void init() {
        if (initialized) return;
        executor.submit(() -> {
            try {
                rpc.init(CLIENT_ID, new OwnerHandler(), false);
                initialized = true;

                scheduler.scheduleAtFixedRate(() -> {
                    try { rpc.runCallbacks(); } catch (Exception ignored) {}
                }, 1, 2, TimeUnit.SECONDS);

                scheduler.scheduleAtFixedRate(this::updateStatus, 15, 30, TimeUnit.SECONDS);

                updateStatus();
            } catch (Exception ignored) {}
        });
    }

    public void updateStatus() {
        if (!initialized) return;

        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) {
            mc.execute(this::updateStatus);
            return;
        }

        String details = "В лобби";
        String stateText = "Подготовка к матчу";

        if (mc.player != null) {
            net.minecraft.world.scores.Team team = mc.player.getTeam();
            if (ClientGameData.getCurrentPhase() == BattleCraftManager.GamePhase.ACTIVE) {
                if (team != null) {
                    details = "Играет за команду " + team.getName();
                    stateText = ClientGameData.hasActiveVote() ? "Идет голосование (F7/F8)" : "В бою";
                } else {
                    details = "Выбор команды";
                    stateText = "Наблюдение";
                }
            } else if (ClientGameData.getCurrentPhase() == BattleCraftManager.GamePhase.ENDED) {
                details = "Матч завершен";
                stateText = "В главном меню";
            }
        }

        final String fDetails = details;
        final String fState = (isOwner ? "Owner" : "User") + " | " + stateText;

        executor.submit(() -> {
            try {
                DiscordRichPresence presence = DiscordRichPresence.builder()
                        .details(fDetails)
                        .state(fState)
                        .largeImageKey("rpc_icon")
                        .largeImageText("BattleCraft Remake")
                        .activityType(ActivityType.PLAYING)
                        .button(DiscordRichPresence.RPCButton.of("GitHub", "https://github.com/pers1k1/BattleCraft-Remake"))
                        .build();

                rpc.updatePresence(presence);
            } catch (Exception ignored) {}
        });
    }

    public void shutdown() {
        scheduler.shutdownNow();
        executor.submit(() -> {
            try {
                rpc.shutdown();
                initialized = false;
            } catch (Exception ignored) {}
        });
        executor.shutdown();
    }

    private final class OwnerHandler implements DiscordEventHandler {
        @Override
        public void ready(User user) {
            isOwner = user != null && OWNER_ID.equals(user.getUserId());
            updateStatus();
        }

        @Override public void disconnected(ErrorCode errorCode, String message) {}
        @Override public void errored(ErrorCode errorCode, String message) {}
        @Override public void joinGame(String joinSecret) {}
        @Override public void spectateGame(String spectateSecret) {}
        @Override public void joinRequest(DiscordJoinRequest request) {}
    }
}
