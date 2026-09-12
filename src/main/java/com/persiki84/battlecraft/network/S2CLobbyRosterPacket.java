package com.persiki84.battlecraft.network;

import com.persiki84.battlecraft.client.ClientLobbyData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class S2CLobbyRosterPacket {
    private final List<String> teams;
    private final List<Member> members;
    private final int slotsPerTeam;

    public S2CLobbyRosterPacket(List<String> teams, List<Member> members, int slotsPerTeam) {
        this.teams = teams;
        this.members = members;
        this.slotsPerTeam = slotsPerTeam;
    }

    public List<String> teams() { return teams; }
    public List<Member> members() { return members; }
    public int slotsPerTeam() { return slotsPerTeam; }

    public static void encode(S2CLobbyRosterPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.slotsPerTeam);

        buf.writeInt(packet.teams.size());
        for (String team : packet.teams) {
            buf.writeUtf(team);
        }

        buf.writeInt(packet.members.size());
        for (Member member : packet.members) {
            buf.writeUUID(member.uuid());
            buf.writeUtf(member.name());
            buf.writeUtf(member.team() == null ? "" : member.team());
            buf.writeBoolean(member.ready());
        }
    }

    public static S2CLobbyRosterPacket decode(FriendlyByteBuf buf) {
        int slots = buf.readInt();

        int teamCount = buf.readInt();
        List<String> teams = new ArrayList<>(teamCount);
        for (int i = 0; i < teamCount; i++) {
            teams.add(buf.readUtf());
        }

        int memberCount = buf.readInt();
        List<Member> members = new ArrayList<>(memberCount);
        for (int i = 0; i < memberCount; i++) {
            UUID uuid = buf.readUUID();
            String name = buf.readUtf();
            String team = buf.readUtf();
            boolean ready = buf.readBoolean();
            members.add(new Member(uuid, name, team.isEmpty() ? null : team, ready));
        }
        return new S2CLobbyRosterPacket(teams, members, slots);
    }

    public static void handle(S2CLobbyRosterPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> ClientLobbyData.accept(packet)));
        ctx.get().setPacketHandled(true);
    }

    public record Member(UUID uuid, String name, String team, boolean ready) {}
}
