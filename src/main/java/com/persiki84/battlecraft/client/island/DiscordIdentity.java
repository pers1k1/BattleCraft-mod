package com.persiki84.battlecraft.client.island;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.persiki84.battlecraft.BattleCraftMod;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class DiscordIdentity {
    private static final String PIPE = "\\\\.\\pipe\\discord-ipc-";
    private static final int PIPES = 10;
    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    private static final int OP_CLOSE = 2;
    private static final int HEADER = 8;
    private static final int PAYLOAD_LIMIT = 1 << 16;

    private static boolean asked;

    private DiscordIdentity() {}

    public static void ask(String clientId) {
        if (asked || !windows()) return;

        asked = true;
        Thread worker = new Thread(() -> probe(clientId), "battlecraft-discord");
        worker.setDaemon(true);
        worker.start();
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static void probe(String clientId) {
        for (int index = 0; index < PIPES; index++) {
            JsonObject user = greet(PIPE + index, clientId);
            if (user == null) continue;

            String id = string(user, "id");
            String avatar = string(user, "avatar");
            BattleCraftMod.LOGGER.info("[battlecraft] discord identity: id={} avatar={}", id, avatar);
            DiscordAvatar.remember(id, avatar);
            return;
        }
        BattleCraftMod.LOGGER.info("[battlecraft] discord ipc did not answer on any pipe");
    }

    private static JsonObject greet(String pipe, String clientId) {
        try (RandomAccessFile channel = new RandomAccessFile(pipe, "rw")) {
            write(channel, OP_HANDSHAKE, "{\"v\":1,\"client_id\":\"" + clientId + "\"}");
            JsonObject frame = read(channel);
            write(channel, OP_CLOSE, "{}");
            return frame == null ? null : userOf(frame);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static JsonObject userOf(JsonObject frame) {
        if (!frame.has("data") || !frame.get("data").isJsonObject()) return null;

        JsonObject data = frame.getAsJsonObject("data");
        if (!data.has("user") || !data.get("user").isJsonObject()) return null;
        return data.getAsJsonObject("user");
    }

    private static void write(RandomAccessFile channel, int opcode, String payload) throws IOException {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        ByteBuffer frame = ByteBuffer.allocate(HEADER + body.length).order(ByteOrder.LITTLE_ENDIAN);
        frame.putInt(opcode);
        frame.putInt(body.length);
        frame.put(body);
        channel.write(frame.array());
    }

    private static JsonObject read(RandomAccessFile channel) throws IOException {
        byte[] header = new byte[HEADER];
        channel.readFully(header);

        ByteBuffer view = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int opcode = view.getInt();
        int length = view.getInt();
        if (opcode != OP_FRAME || length <= 0 || length > PAYLOAD_LIMIT) return null;

        byte[] body = new byte[length];
        channel.readFully(body);
        return JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static String string(JsonObject holder, String key) {
        return holder.has(key) && !holder.get(key).isJsonNull() ? holder.get(key).getAsString() : "";
    }
}
