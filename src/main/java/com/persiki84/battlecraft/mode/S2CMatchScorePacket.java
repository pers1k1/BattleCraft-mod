package com.persiki84.battlecraft.mode;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class S2CMatchScorePacket {
    public static final int TEAMS_MAX = 16;
    public static final int TEAM_NAME_MAX = 64;

    public record Team(String name, int score) {}

    public final boolean running;
    public final int mode;
    public final int scoreTarget;
    public final int capMinutes;
    public final long suddenEndMillis;
    public final List<Team> teams;

    public S2CMatchScorePacket(boolean running, int mode, int scoreTarget, int capMinutes, long suddenEndMillis,
                               List<Team> teams) {
        this.running = running;
        this.mode = mode;
        this.scoreTarget = scoreTarget;
        this.capMinutes = capMinutes;
        this.suddenEndMillis = suddenEndMillis;
        this.teams = teams;
    }

    public static S2CMatchScorePacket idle() {
        return new S2CMatchScorePacket(false, 0, 0, 0, MatchScore.NO_SUDDEN, List.of());
    }

    public static void encode(S2CMatchScorePacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.running);
        if (!packet.running) return;
        buffer.writeVarInt(packet.mode);
        buffer.writeVarInt(packet.scoreTarget);
        buffer.writeVarInt(packet.capMinutes);
        buffer.writeLong(packet.suddenEndMillis);
        int count = Math.min(TEAMS_MAX, packet.teams.size());
        buffer.writeVarInt(count);
        for (int index = 0; index < count; index++) {
            Team team = packet.teams.get(index);
            buffer.writeUtf(team.name(), TEAM_NAME_MAX);
            buffer.writeVarInt(team.score());
        }
    }

    public static S2CMatchScorePacket decode(FriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) return idle();
        int mode = buffer.readVarInt();
        int target = buffer.readVarInt();
        int cap = buffer.readVarInt();
        long sudden = buffer.readLong();
        int count = Math.max(0, Math.min(TEAMS_MAX, buffer.readVarInt()));
        List<Team> teams = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            teams.add(new Team(buffer.readUtf(TEAM_NAME_MAX), Math.max(0, buffer.readVarInt())));
        }
        return new S2CMatchScorePacket(true, mode, target, cap, sudden, teams);
    }

    public static void handle(S2CMatchScorePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.persiki84.battlecraft.client.ClientMatchMode.acceptScore(packet)));
        ctx.get().setPacketHandled(true);
    }
}
