package com.persiki84.dmgndctr.client;

import com.persiki84.dmgndctr.network.DamagePacket;

public class ClientPacketHandler {
    public static void handle(DamagePacket msg) {
        for (int index = 0; index < msg.count(); index++) {
            DamageNumbers.add(msg.targets[index], msg.xs[index], msg.ys[index], msg.zs[index],
                    msg.amounts[index], msg.crits[index]);
        }
    }
}
