package com.persiki84.battlecraft.client;

import com.persiki84.battlecraft.network.C2SClientReportPacket;
import com.persiki84.battlecraft.network.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;

import java.util.ArrayList;
import java.util.List;

public final class ClientGuard {
    private static final int SWEEP_TICKS = 20;
    private static final String FOLDER_PREFIX = "file/";

    private static final List<String> foreign = new ArrayList<>();
    private static final List<String> left = new ArrayList<>();
    private static final List<String> attempted = new ArrayList<>();

    private static int ticks;
    private static boolean reported;
    private static boolean glintLoose;

    private ClientGuard() {}

    public static void forget() {
        reported = false;
        ticks = SWEEP_TICKS;
        attempted.clear();
    }

    public static void watch() {
        if (!ServerLocks.packsLocked() && !ServerLocks.glintLocked()) {
            forget();
            return;
        }
        if (++ticks < SWEEP_TICKS) return;

        ticks = 0;
        sweep();
    }

    private static void sweep() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean loose = ServerLocks.glintLoose();
        if (!ServerLocks.packsLocked()) {
            settle(loose);
            return;
        }

        PackRepository repository = minecraft.getResourcePackRepository();
        collect(repository, foreign);
        if (foreign.isEmpty()) {
            attempted.clear();
            settle(loose);
            return;
        }

        stubborn();
        drop(minecraft, repository);
        attempted.clear();
        attempted.addAll(foreign);
        report(loose);
    }

    // WHY: пак считается оставленным, только если он пережил уже сделанную попытку снять его:
    // WHY: перезагрузка ресурсов идёт своим чередом, и проверка сразу после removePack ловила
    // WHY: состояние на середине пути, а сервер кикал игрока за пак, который снимался нормально
    private static void stubborn() {
        left.clear();
        for (String id : foreign) {
            if (attempted.contains(id)) left.add(id);
        }
    }

    // WHY: отчёт без новостей шлётся один раз: сервер ждёт его один раз на рассылку правил, и
    // WHY: секундный пакет от каждого игрока всё оставшееся время матча ему ни к чему
    private static void settle(boolean loose) {
        if (reported && loose == glintLoose) return;

        report(loose);
    }

    // WHY: снимается только то, что игрок сам положил в resourcepacks - у таких паков id идёт с
    // WHY: file/ (FolderRepositorySource). Отбор по источнику ловил вместе с ними mod_resources,
    // WHY: то есть ресурсы всех модов, а он ещё и required: rebuildSelected возвращал его назад,
    // WHY: сторож видел пак снова и сервер кикал игрока за «отказ снять» текстуры сборки
    private static void collect(PackRepository repository, List<String> target) {
        target.clear();
        for (Pack pack : repository.getSelectedPacks()) {
            if (!pack.getId().startsWith(FOLDER_PREFIX)) continue;
            if (pack.getPackSource() != PackSource.DEFAULT || pack.isFixedPosition() || pack.isRequired()) continue;

            target.add(pack.getId());
        }
    }

    private static void drop(Minecraft minecraft, PackRepository repository) {
        boolean changed = false;
        for (String id : foreign) {
            if (repository.removePack(id)) changed = true;
        }
        if (changed) minecraft.options.updateResourcePacks(repository);
    }

    private static void report(boolean loose) {
        reported = true;
        glintLoose = loose;
        PacketHandler.INSTANCE.sendToServer(
                new C2SClientReportPacket(List.copyOf(foreign), List.copyOf(left), loose));
        foreign.clear();
        left.clear();
    }

}
