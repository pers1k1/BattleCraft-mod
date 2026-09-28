package com.persiki84.battlecraft.client.menu.desktop;

import com.mojang.logging.LogUtils;
import com.persiki84.battlecraft.client.menu.browse.WorldActions;
import com.persiki84.battlecraft.client.menu.custom.CustomizeScreen;
import com.persiki84.shared.client.menu.ManagerScreen;
import com.persiki84.shared.client.ui.UiIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

final class SpotlightSource {
    record Result(Component title, Component kind, UiIcon.Kind glyph, Consumer<Screen> run, String key) {
        Result(Component title, Component kind, UiIcon.Kind glyph, Consumer<Screen> run) {
            this(title, kind, glyph, run, SpotlightRank.keyOf(title));
        }
    }

    private record Entry(Result result, String local, String tail) {
        static Entry of(Result result) {
            return new Entry(result, SpotlightRank.haystack(result.title().getString()),
                    SpotlightRank.keyTail(result.key()));
        }
    }

    private record Hit(Result result, int tier) {
    }

    private static final int LIMIT = 8;
    private static final int SUGGESTED_WORLDS = 2;
    private static final Component KIND_CUSTOMIZE = Component.translatable("battlecraft.desktop.search.customize");

    private static final SpotlightEnglish ENGLISH = new SpotlightEnglish();
    private static List<Entry> places = List.of();
    private static String placesLanguage;
    private static int placesKeys = -1;

    private final List<Result> actions;
    private final AtomicInteger worldsIssue = new AtomicInteger();
    private volatile List<Entry> worlds = List.of();
    private CompletableFuture<Void> worldsLoading = CompletableFuture.completedFuture(null);
    private List<Entry> front = List.of();
    private List<Entry> servers = List.of();
    private String frontLanguage;

    SpotlightSource(List<Result> actions) {
        this.actions = actions;
    }

    // WHY: стол возвращается из настроек, списка миров и серверов тем же экземпляром. Серверы и
    // WHY: миры перечитываются на каждом открытии поиска, иначе удалённый мир оставался в выдаче
    void load() {
        prepare();
        servers = entries(serversOf());
        loadWorlds();
    }

    // WHY: смена языка сперва ставит код языка, а переводы приходят только после перезагрузки
    // WHY: ресурсов под заставкой: собранный под ней индекс остался бы со старыми названиями
    void prepareAhead() {
        if (Minecraft.getInstance().getOverlay() == null) prepare();
    }

    private void prepare() {
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        if (!language.equals(frontLanguage)) {
            frontLanguage = language;
            front = entries(actions);
        }
        if (!language.equals(placesLanguage) || keyCount() != placesKeys) index(language, front);
    }

    // WHY: индекс кастомизации, настроек и клавиш дорогой и общий для всех экземпляров стола: стол
    // WHY: пересоздаётся после выхода из мира и из кастомизации, и сборка на каждой первой букве
    // WHY: давала заминку. Названия в нём уже переведены, поэтому он пересобирается после смены
    // WHY: языка, а клавиши сверяются по числу: моды добавляют их только при загрузке
    private static void index(String language, List<Entry> front) {
        placesLanguage = language;
        placesKeys = keyCount();
        List<Result> reachable = new ArrayList<>(customizeResults());
        reachable.addAll(SpotlightSettings.all());
        places = entries(reachable);
        ENGLISH.load(keysOf(front, places));
    }

    private static int keyCount() {
        return Minecraft.getInstance().options.keyMappings.length;
    }

    // WHY: миры читаются с диска в фоне, как в ванильном списке: поиск открывается сразу,
    // WHY: а строки миров дописываются, когда загрузчик их вернёт. Пока идёт чтение, новое не
    // WHY: заводится: открытия подряд плодили бы чтения всех level.dat, и старое могло прийти последним
    private void loadWorlds() {
        if (!worldsLoading.isDone()) return;
        try {
            LevelStorageSource storage = Minecraft.getInstance().getLevelSource();
            worldsLoading = storage.loadLevelSummaries(storage.findLevelCandidates())
                    .thenAccept(found -> publishWorlds(entries(worldsOf(found))));
        } catch (Exception error) {
            LogUtils.getLogger().warn("[battlecraft] spotlight worlds off: {}", String.valueOf(error));
        }
    }

    // WHY: выпуск меняется, только когда другими стали видимые строки: открытая панель по нему
    // WHY: пересобирает выдачу, и перечитанный без перемен список не должен дёргать её каждый раз
    private void publishWorlds(List<Entry> fresh) {
        boolean changed = !sameTitles(worlds, fresh);
        worlds = fresh;
        if (changed) worldsIssue.incrementAndGet();
    }

    private static boolean sameTitles(List<Entry> before, List<Entry> after) {
        if (before.size() != after.size()) return false;

        for (int index = 0; index < before.size(); index++) {
            if (!before.get(index).local().equals(after.get(index).local())) return false;
        }
        return true;
    }

    int issue() {
        return worldsIssue.get() + ENGLISH.issue();
    }

    private static List<Entry> entries(List<Result> results) {
        List<Entry> made = new ArrayList<>(results.size());
        for (Result result : results) {
            made.add(Entry.of(result));
        }
        return List.copyOf(made);
    }

    private static Set<String> keysOf(List<Entry> first, List<Entry> second) {
        Set<String> keys = new HashSet<>();
        for (List<Entry> pool : List.of(first, second)) {
            for (Entry entry : pool) {
                if (entry.result().key() != null) keys.add(entry.result().key());
            }
        }
        return keys;
    }

    private static List<Result> customizeResults() {
        List<Result> made = new ArrayList<>();
        for (ManagerScreen.Landmark mark : CustomizeScreen.searchIndex()) {
            made.add(new Result(mark.title(), KIND_CUSTOMIZE, UiIcon.Kind.SLIDERS,
                    owner -> CustomizeScreen.openAt(mark.key()), mark.key()));
        }
        return made;
    }

    // WHY: мир из поиска входит тем же путём, что из списка миров: вопросы о копии и о чужой
    // WHY: версии, проверка ссылки и наличия папки. Прямой loadLevel открывал мир новее игры молча
    private static List<Result> worldsOf(List<LevelSummary> found) {
        List<Result> made = new ArrayList<>();
        found.stream().filter(summary -> !summary.isDisabled() && !summary.isLocked())
                .sorted(Comparator.comparingLong(LevelSummary::getLastPlayed).reversed())
                .forEach(summary -> made.add(new Result(Component.literal(summary.getLevelName()),
                        Component.translatable("battlecraft.desktop.search.world"), UiIcon.Kind.PERSON,
                        owner -> WorldActions.join(owner, summary), null)));
        return made;
    }

    private static List<Result> serversOf() {
        ServerList list = new ServerList(Minecraft.getInstance());
        list.load();
        List<Result> made = new ArrayList<>();
        for (int index = 0; index < list.size(); index++) {
            ServerData data = list.get(index);
            made.add(new Result(Component.literal(data.name), Component.translatable("battlecraft.desktop.search.server"),
                    UiIcon.Kind.GLOBE, owner -> ConnectScreen.startConnecting(owner, Minecraft.getInstance(),
                    ServerAddress.parseString(data.ip), data, false), null));
        }
        return made;
    }

    List<Result> find(String query) {
        String needle = SpotlightRank.needle(query);
        if (needle.isEmpty()) return suggestions();

        String[] words = SpotlightRank.words(needle);
        List<Hit> hits = new ArrayList<>();
        for (List<Entry> pool : List.of(front, worlds, servers, places)) {
            collect(hits, pool, needle, words);
        }
        hits.sort(Comparator.comparingInt(Hit::tier));
        List<Result> kept = new ArrayList<>(Math.min(LIMIT, hits.size()));
        for (int index = 0; index < hits.size() && kept.size() < LIMIT; index++) {
            kept.add(hits.get(index).result());
        }
        return kept;
    }

    private void collect(List<Hit> hits, List<Entry> pool, String needle, String[] words) {
        for (Entry entry : pool) {
            int tier = SpotlightRank.tier(entry.local(), ENGLISH.of(entry.result().key()), entry.tail(), needle, words);
            if (tier != SpotlightRank.MISS) hits.add(new Hit(entry.result(), tier));
        }
    }

    private List<Result> suggestions() {
        List<Entry> known = worlds;
        List<Result> offered = new ArrayList<>();
        for (int index = 0; index < Math.min(SUGGESTED_WORLDS, known.size()); index++) {
            offered.add(known.get(index).result());
        }
        for (Result action : actions) {
            if (offered.size() >= LIMIT) break;
            offered.add(action);
        }
        return offered;
    }
}
