package com.persiki84.battlecraft.client.menu.browse;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.SymlinkWarningScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.SharedConstants;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;

import java.io.IOException;
import java.nio.file.Path;

// WHY: порядок вопросов повторяет ванильный: резервная копия, предупреждение о чужой версии,
// WHY: ссылка вне каталога миров. Пропустить хоть один значит дать игроку сломать свой мир
public final class WorldActions {
    private WorldActions() {}

    public static void join(Screen owner, LevelSummary summary) {
        if (summary.isDisabled()) return;
        if (blocked(owner, summary)) return;

        LevelSummary.BackupStatus status = summary.backupStatus();
        if (status.shouldBackup()) {
            askBackup(owner, summary, status);
            return;
        }
        if (summary.askToOpenWorld()) {
            askVersion(owner, summary);
            return;
        }
        load(owner, summary);
    }

    private static boolean blocked(Screen owner, LevelSummary summary) {
        if (!(summary instanceof LevelSummary.SymlinkLevelSummary)) return false;

        Minecraft.getInstance().setScreen(new SymlinkWarningScreen(owner));
        return true;
    }

    private static void askBackup(Screen owner, LevelSummary summary, LevelSummary.BackupStatus status) {
        MutableComponent question =
                Component.translatable("selectWorld.backupQuestion." + status.getTranslationKey());
        if (status.isSevere()) question.withStyle(ChatFormatting.BOLD, ChatFormatting.RED);

        Component warning = Component.translatable("selectWorld.backupWarning." + status.getTranslationKey(),
                summary.getWorldVersionName(), SharedConstants.getCurrentVersion().getName());
        Minecraft.getInstance().setScreen(new BackupConfirmScreen(owner, (backup, erase) -> {
            if (backup) backup(summary);
            load(owner, summary);
        }, question, warning, false));
    }

    private static void backup(LevelSummary summary) {
        String id = summary.getLevelId();
        try (LevelStorageSource.LevelStorageAccess access =
                     Minecraft.getInstance().getLevelSource().validateAndCreateAccess(id)) {
            EditWorldScreen.makeBackupAndShowToast(access);
        } catch (Exception error) {
            SystemToast.onWorldAccessFailure(Minecraft.getInstance(), id);
            LogUtils.getLogger().error("[battlecraft] копия мира {} не сделана", id, error);
        }
    }

    private static void askVersion(Screen owner, LevelSummary summary) {
        Minecraft.getInstance().setScreen(new ConfirmScreen(agreed -> {
            if (agreed) {
                load(owner, summary);
                return;
            }
            Minecraft.getInstance().setScreen(owner);
        }, Component.translatable("selectWorld.versionQuestion"),
                Component.translatable("selectWorld.versionWarning", summary.getWorldVersionName()),
                Component.translatable("selectWorld.versionJoinButton"), CommonComponents.GUI_CANCEL));
    }

    private static void load(Screen owner, LevelSummary summary) {
        Minecraft client = Minecraft.getInstance();
        if (!client.getLevelSource().levelExists(summary.getLevelId())) return;

        client.forceSetScreen(new GenericDirtMessageScreen(
                Component.translatable("selectWorld.data_read")));
        client.createWorldOpenFlows().loadLevel(owner, summary.getLevelId());
    }

    public static void edit(Screen owner, LevelSummary summary, Runnable reload) {
        if (blocked(owner, summary)) return;

        Minecraft client = Minecraft.getInstance();
        String id = summary.getLevelId();
        try {
            LevelStorageSource.LevelStorageAccess access = client.getLevelSource().validateAndCreateAccess(id);
            client.setScreen(new EditWorldScreen(saved -> release(access, id, saved, reload, owner), access));
        } catch (Exception error) {
            SystemToast.onWorldAccessFailure(client, id);
            LogUtils.getLogger().error("[battlecraft] мир {} не открыт для правки", id, error);
            reload.run();
        }
    }

    private static void release(LevelStorageSource.LevelStorageAccess access, String id,
                                boolean saved, Runnable reload, Screen owner) {
        try {
            access.close();
        } catch (IOException error) {
            LogUtils.getLogger().error("[battlecraft] мир {} не отпущен", id, error);
        }
        if (saved) reload.run();
        Minecraft.getInstance().setScreen(owner);
    }

    public static void recreate(Screen owner, LevelSummary summary) {
        if (blocked(owner, summary)) return;

        Minecraft client = Minecraft.getInstance();
        client.forceSetScreen(new GenericDirtMessageScreen(
                Component.translatable("selectWorld.data_read")));
        try (LevelStorageSource.LevelStorageAccess access =
                     client.getLevelSource().validateAndCreateAccess(summary.getLevelId())) {
            Pair<LevelSettings, WorldCreationContext> made =
                    client.createWorldOpenFlows().recreateWorldData(access);
            Path packs = CreateWorldScreen.createTempDataPackDirFromExistingWorld(
                    access.getLevelPath(LevelResource.DATAPACK_DIR), client);
            client.setScreen(CreateWorldScreen.createFromExisting(client, owner, made.getFirst(),
                    made.getSecond(), packs));
        } catch (Exception error) {
            LogUtils.getLogger().error("[battlecraft] копия мира {} не собрана",
                    summary.getLevelId(), error);
            client.setScreen(owner);
        }
    }

    public static void delete(Screen owner, LevelSummary summary, Runnable reload) {
        Minecraft.getInstance().setScreen(new ConfirmScreen(agreed -> {
            if (agreed) {
                Minecraft.getInstance().setScreen(new ProgressScreen(true));
                erase(summary, reload);
            }
            Minecraft.getInstance().setScreen(owner);
        }, Component.translatable("selectWorld.deleteQuestion"),
                Component.translatable("selectWorld.deleteWarning", summary.getLevelName()),
                Component.translatable("selectWorld.deleteButton"), CommonComponents.GUI_CANCEL));
    }

    private static void erase(LevelSummary summary, Runnable reload) {
        String id = summary.getLevelId();
        try (LevelStorageSource.LevelStorageAccess access =
                     Minecraft.getInstance().getLevelSource().createAccess(id)) {
            access.deleteLevel();
        } catch (IOException error) {
            SystemToast.onWorldDeleteFailure(Minecraft.getInstance(), id);
            LogUtils.getLogger().error("[battlecraft] мир {} не удалён", id, error);
        }
        reload.run();
    }
}
