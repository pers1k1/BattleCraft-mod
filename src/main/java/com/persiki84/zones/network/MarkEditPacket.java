package com.persiki84.zones.network;

import com.persiki84.shared.ActionGate;
import com.persiki84.zones.ZonesMod;
import com.persiki84.zones.mark.MapMark;
import com.persiki84.zones.mark.MarkKind;
import com.persiki84.zones.mark.MarkRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// WHY: правка надписи идёт с экрана карты, а не строкой меню: перетаскивание и набор текста
// WHY: живут прямо на карте, и командой такое не выразить - но заявка всё равно проверяется здесь
public class MarkEditPacket {
    public enum Action {
        CREATE,
        MOVE,
        SET_LINE,
        ADD_LINE,
        REMOVE_LINE,
        COLOR,
        EVERYONE,
        TEAM,
        SCALE,
        DELETE
    }

    private static final int ID_LIMIT = 64;
    private static final int GATE_TICKS = 5;
    private static final String GATE_KEY = "markEditTick";

    private final Action action;
    private final String id;
    private final int x;
    private final int z;
    private final int line;
    private final int color;
    private final String text;

    public MarkEditPacket(Action action, String id, int x, int z, int line, int color, String text) {
        this.action = action;
        this.id = id;
        this.x = x;
        this.z = z;
        this.line = line;
        this.color = color;
        this.text = text;
    }

    public static void encode(MarkEditPacket packet, FriendlyByteBuf buf) {
        buf.writeEnum(packet.action);
        buf.writeUtf(packet.id, ID_LIMIT);
        buf.writeInt(packet.x);
        buf.writeInt(packet.z);
        buf.writeVarInt(packet.line);
        buf.writeInt(packet.color);
        buf.writeUtf(packet.text == null ? "" : packet.text, MapMark.LINE_LIMIT);
    }

    public static MarkEditPacket decode(FriendlyByteBuf buf) {
        return new MarkEditPacket(buf.readEnum(Action.class), buf.readUtf(ID_LIMIT), buf.readInt(),
                buf.readInt(), buf.readVarInt(), buf.readInt(), buf.readUtf(MapMark.LINE_LIMIT));
    }

    public static void handle(MarkEditPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;
            if (!ActionGate.allow(player, GATE_KEY, GATE_TICKS)) return;

            if (packet.action == Action.CREATE) {
                create(packet, player);
                return;
            }
            apply(packet, player);
        });
        ctx.get().setPacketHandled(true);
    }

    // WHY: высота берётся от самого оператора, а не от местности: getHeight за границей прогрузки
    // WHY: генерирует чанк в главном потоке, а надпись на карте по высоте ничего не значит
    private static void create(MarkEditPacket packet, ServerPlayer player) {
        String id = freeId(player);
        MapMark mark = new MapMark(id, new BlockPos(packet.x, player.getBlockY(), packet.z),
                player.serverLevel().dimension().location(), packet.text, packet.color);
        mark.setKind(MarkKind.TEXT);
        mark.setInWorld(false);

        MarkRegistry.upsert(mark);
        ZonesMod.syncMarksToEveryone(player.getServer());
    }

    // WHY: идентификатор надписи не набирают: он берётся первым свободным, иначе игрок на карте
    // WHY: обязан был бы помнить, какие имена уже заняты
    private static String freeId(ServerPlayer player) {
        for (int index = 1; index < 10000; index++) {
            String candidate = "text" + index;
            if (!MarkRegistry.exists(candidate)) return candidate;
        }
        return "text" + player.getUUID();
    }

    // WHY: имя команды приходит от клиента, поэтому существование сверяется здесь: иначе в набор
    // WHY: ляжет мусор, и метка окажется скрытой от всех навсегда
    private static boolean allowTeam(MarkEditPacket packet, ServerPlayer player, MapMark mark) {
        if (player.getServer() == null) return false;
        if (player.getServer().getScoreboard().getPlayerTeam(packet.text) == null) return false;

        if (packet.line != 0) {
            mark.allow(packet.text);
        } else {
            mark.forbid(packet.text);
        }
        return true;
    }

    private static void apply(MarkEditPacket packet, ServerPlayer player) {
        MapMark mark = MarkRegistry.byId(packet.id);
        if (mark == null) return;

        switch (packet.action) {
            case MOVE -> mark.setPosition(new BlockPos(packet.x, mark.position().getY(), packet.z));
            case SET_LINE -> {
                if (!mark.setLine(packet.line, packet.text)) return;
            }
            case ADD_LINE -> {
                if (!mark.addLine(packet.text)) return;
            }
            case REMOVE_LINE -> {
                if (!mark.removeLine(packet.line)) return;
            }
            case COLOR -> mark.setColor(packet.color);
            case SCALE -> mark.setScalePercent(packet.line);
            case EVERYONE -> mark.showEveryone();
            case TEAM -> {
                if (!allowTeam(packet, player, mark)) return;
            }
            case DELETE -> {
                MarkRegistry.remove(packet.id);
                ZonesMod.syncMarksToEveryone(player.getServer());
                return;
            }
            default -> {
                return;
            }
        }

        MarkRegistry.persist();
        ZonesMod.syncMarksToEveryone(player.getServer());
    }
}
