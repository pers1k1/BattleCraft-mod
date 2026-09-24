package com.persiki84.battlecraft;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.Event;

// WHY: модулям нужен старт и конец матча, а ядро до сих пор звало их поимённо: событие на общей
// WHY: шине даёт ту же точку без того, чтобы ядро знало о каждом подписчике
public abstract class MatchEvent extends Event {
    private final MinecraftServer server;

    protected MatchEvent(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer server() {
        return server;
    }

    public static final class Started extends MatchEvent {
        public Started(MinecraftServer server) {
            super(server);
        }
    }

    public static final class Ended extends MatchEvent {
        public Ended(MinecraftServer server) {
            super(server);
        }
    }
}
