package com.persiki84.battlecraft.client.lyrics;

import com.persiki84.battlecraft.BattleCraftMod;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.regex.Pattern;

// WHY: порт открыт только на 127.0.0.1, но запрос туда может отправить любая страница, открытая в
// WHY: браузере. Посылка принимается лишь с origin Spotify: заголовок Origin страница подделать не
// WHY: может. Chromium с проверкой доступа к локальной сети ждёт на preflight Allow-Private-Network
final class BridgeExchange implements HttpHandler {
    private static final Pattern SPOTIFY_ORIGIN = Pattern.compile("^https://([a-z0-9-]+\\.)*spotify\\.com$");
    private static final int BODY_LIMIT = 600 * 1024;
    private static final int NO_BODY = -1;

    private final Consumer<BridgeDrop> sink;
    private String lastRefused = "";

    BridgeExchange(Consumer<BridgeDrop> sink) {
        this.sink = sink;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            if (origin == null || !SPOTIFY_ORIGIN.matcher(origin).matches()) {
                refuse(exchange, String.valueOf(origin));
                return;
            }
            allow(exchange.getResponseHeaders(), origin);
            switch (exchange.getRequestMethod()) {
                case "OPTIONS" -> exchange.sendResponseHeaders(204, NO_BODY);
                case "POST" -> accept(exchange);
                default -> exchange.sendResponseHeaders(405, NO_BODY);
            }
        } finally {
            exchange.close();
        }
    }

    private void refuse(HttpExchange exchange, String origin) throws IOException {
        if (!origin.equals(lastRefused)) BattleCraftMod.LOGGER.info("spotify bridge refused origin {}", origin);
        lastRefused = origin;
        exchange.sendResponseHeaders(403, NO_BODY);
    }

    private static void allow(Headers headers, String origin) {
        headers.set("Access-Control-Allow-Origin", origin);
        headers.set("Access-Control-Allow-Methods", "POST, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
        headers.set("Access-Control-Allow-Private-Network", "true");
        headers.set("Access-Control-Max-Age", "600");
        headers.set("Vary", "Origin");
    }

    private void accept(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(BODY_LIMIT + 1);
        if (body.length > BODY_LIMIT) {
            exchange.sendResponseHeaders(413, NO_BODY);
            return;
        }
        BridgeDrop drop = parse(body);
        if (drop != null) sink.accept(drop);
        exchange.sendResponseHeaders(drop != null ? 204 : 422, NO_BODY);
    }

    private static BridgeDrop parse(byte[] body) {
        try {
            return BridgeParcel.read(new String(body, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException error) {
            BattleCraftMod.LOGGER.debug("spotify bridge parcel dropped: {}", error.toString());
            return null;
        }
    }
}
