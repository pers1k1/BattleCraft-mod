package com.persiki84.capturepoints.network;

import com.persiki84.capturepoints.capture.CapturePoint;
import com.persiki84.capturepoints.capture.CapturePointManager;
import com.persiki84.capturepoints.capture.FinalCapturePoint;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class CaptureStartPacket {
    private final BlockPos playerPos;

    public CaptureStartPacket(BlockPos playerPos) {
        this.playerPos = playerPos;
    }

    public static void encode(CaptureStartPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.playerPos);
    }

    public static CaptureStartPacket decode(FriendlyByteBuf buf) {
        return new CaptureStartPacket(buf.readBlockPos());
    }

    public static void handle(CaptureStartPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                CapturePoint nearestPoint = null;
                double minDistance = Double.MAX_VALUE;

                for (CapturePoint point : CapturePointManager.getAllPoints()) {
                    double distance = point.getPosition().distSqr(packet.playerPos);
                    if (distance < minDistance && distance <= point.getRadius() * point.getRadius()) {
                        minDistance = distance;
                        nearestPoint = point;
                    }
                }

                if (CapturePointManager.isFinalPointAvailable()) {
                    for (FinalCapturePoint point : CapturePointManager.getAllFinalPoints()) {
                        double distance = point.getPosition().distSqr(packet.playerPos);
                        if (distance < minDistance && distance <= point.getRadius() * point.getRadius()) {
                            minDistance = distance;
                            nearestPoint = point;
                        }
                    }
                }

                if (nearestPoint != null) {
                    CapturePointManager.startCapture(player, nearestPoint);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
