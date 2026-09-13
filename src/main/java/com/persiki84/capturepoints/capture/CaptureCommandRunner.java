package com.persiki84.capturepoints.capture;

import com.persiki84.capturepoints.CapturePointsMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class CaptureCommandRunner {
    public static final int MAX_LENGTH = 160;

    private static final String TEAM_MARK = "%team%";
    private static final String POINT_MARK = "%point%";
    private static final String X_MARK = "%x%";
    private static final String Y_MARK = "%y%";
    private static final String Z_MARK = "%z%";
    private static final int OPERATOR_LEVEL = 4;

    private CaptureCommandRunner() {}

    public static void onCaptured(MinecraftServer server, CapturePoint point, String team) {
        String command = point.getCaptureCommand();
        if (server == null || command == null) return;

        try {
            server.getCommands().performPrefixedCommand(sourceAt(server, point), filled(command, point, team));
        } catch (RuntimeException failure) {
            CapturePointsMod.LOGGER.error("Capture command of point {} failed: {}", point.getName(), command, failure);
        }
    }

    private static CommandSourceStack sourceAt(MinecraftServer server, CapturePoint point) {
        CommandSourceStack source = server.createCommandSourceStack()
                .withPermission(OPERATOR_LEVEL)
                .withSuppressedOutput();

        ServerLevel level = server.getLevel(point.getDimension());
        if (level == null) return source;

        return source.withLevel(level).withPosition(Vec3.atCenterOf(point.getPosition()));
    }

    private static String filled(String command, CapturePoint point, String team) {
        BlockPos position = point.getPosition();
        return command.replace(TEAM_MARK, team == null ? "" : team)
                .replace(POINT_MARK, point.getName())
                .replace(X_MARK, String.valueOf(position.getX()))
                .replace(Y_MARK, String.valueOf(position.getY()))
                .replace(Z_MARK, String.valueOf(position.getZ()));
    }
}
