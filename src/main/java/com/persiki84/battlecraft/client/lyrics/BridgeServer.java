package com.persiki84.battlecraft.client.lyrics;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.function.Consumer;

// WHY: всё, что трогает jdk.httpserver, живёт в этом классе: в урезанной сборке Java модуля может не
// WHY: быть, и тогда падает только загрузка этого класса, которую мост ловит, а не остальная лирика
final class BridgeServer {
    private final HttpServer server;

    private BridgeServer(HttpServer server) {
        this.server = server;
    }

    static BridgeServer open(int port, Consumer<BridgeDrop> sink) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext("/lyrics", new BridgeExchange(sink));
        server.start();
        return new BridgeServer(server);
    }

    void close() {
        server.stop(0);
    }
}
