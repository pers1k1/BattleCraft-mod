package com.persiki84.capturepoints.network;

import com.persiki84.capturepoints.capture.CapturePoint;
import com.persiki84.capturepoints.capture.CapturePointManager;
import com.persiki84.capturepoints.capture.FinalCapturePoint;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class CaptureStartPacket {

    public CaptureStartPacket() {
    }

    public static void encode(CaptureStartPacket packet, FriendlyByteBuf buf) {
    }

    public static CaptureStartPacket decode(FriendlyByteBuf buf) {
        return new CaptureStartPacket();
    }

    public static void handle(CaptureStartPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            CapturePoint nearest = nearestPointAt(player);
            if (nearest != null) {
                CapturePointManager.startCapture(player, nearest);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static CapturePoint nearestPointAt(ServerPlayer player) {
        CapturePoint nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (CapturePoint point : CapturePointManager.getAllPoints()) {
            double distance = distanceInside(point, player);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = point;
            }
        }

        if (CapturePointManager.isFinalPointAvailable()) {
            for (FinalCapturePoint point : CapturePointManager.getAllFinalPoints()) {
                double distance = distanceInside(point, player);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = point;
                }
            }
        }

        return nearest;
    }

    // WHY: сравнивались одни координаты, поэтому игрок в Нижнем мире с теми же X/Y/Z открывал
    // WHY: сессию на точку Верхнего и запирал её для тех, кто реально на ней стоит
    private static double distanceInside(CapturePoint point, ServerPlayer player) {
        if (player.serverLevel() != CapturePointManager.levelOf(point)) return Double.MAX_VALUE;
        if (!point.getArea().contains(player.getX(), player.getY(), player.getZ())) return Double.MAX_VALUE;

        return point.getArea().horizontalDistanceTo(player.getX(), player.getZ());
    }
}
