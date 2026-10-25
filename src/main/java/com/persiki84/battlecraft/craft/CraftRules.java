package com.persiki84.battlecraft.craft;

import com.persiki84.battlecraft.BattleCraftManager;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

// WHY: в одиночной игре поиск рецептов зовут и клиент, и встроенный сервер в одной JVM, поэтому
// WHY: запреты лежат двумя снимками: серверный ведёт книга запретов, клиентский приходит пакетом.
// WHY: Сторона выбирается по миру вызова, а клиентский фильтр нужен, чтобы камнерез и кузнечный
// WHY: стол не показывали результат, которого сервер всё равно не выдаст
public final class CraftRules {
    private static final String VANILLA = "minecraft";

    private static volatile CraftBanSet server = CraftBanSet.EMPTY;
    private static volatile CraftBanSet client = CraftBanSet.EMPTY;
    private static volatile boolean clientRunning;
    private static volatile RegistryAccess builtIn;
    private static final ThreadLocal<int[]> PAUSED = ThreadLocal.withInitial(() -> new int[1]);

    private CraftRules() {}

    public static void publishServer(Collection<CraftBan> bans) {
        server = CraftBanSet.of(bans);
    }

    public static void acceptClient(Collection<CraftBan> bans, boolean matchRunning) {
        client = CraftBanSet.of(bans);
        clientRunning = matchRunning;
    }

    public static void forgetClient() {
        client = CraftBanSet.EMPTY;
        clientRunning = false;
    }

    public static boolean idle(@Nullable Level level) {
        return level == null || set(level.isClientSide()).isEmpty() || paused();
    }

    // WHY: остаток сетки (getRemainingItemsFor) и книга рецептов (загрузка, выдача, книга знаний)
    // WHY: спрашивают тот же поиск, но запрет там не защищает, а ломает: пустой ответ остатку отдаёт
    // WHY: сами стопки сетки вместо копий, и взятие результата удваивает их, а книга молча стирает
    // WHY: открытый рецепт. На время таких вызовов фильтр снят, счётчик держит вложенные вызовы
    public static void pause() {
        PAUSED.get()[0]++;
    }

    public static void resume() {
        PAUSED.get()[0]--;
    }

    private static boolean paused() {
        return PAUSED.get()[0] > 0;
    }

    public static Optional<? extends Recipe<?>> keyUnfiltered(RecipeManager manager, ResourceLocation key) {
        pause();
        try {
            return manager.byKey(key);
        } finally {
            resume();
        }
    }

    public static <C extends Container> boolean blocks(Recipe<C> recipe, C container, Level level) {
        CraftBanSet bans = set(level.isClientSide());
        if (bans.isEmpty()) return false;

        RegistryAccess access = level.registryAccess();
        ItemStack result = resultOf(recipe, access);
        // WHY: у особых рецептов (стрелы с зельем, фейерверк) образца результата нет, он
        // WHY: собирается из того, что лежит в сетке, поэтому запрет сверяется с собранным.
        // WHY: Сюда приходят только совпавшие с сеткой рецепты, и сборка для них допустима
        boolean assembled = result.isEmpty() || bans.hasVariants(result.getItem());
        if (assembled && container != null) result = assembledOf(recipe, container, access, result);
        return bans.blocks(result, running(level.isClientSide()));
    }

    // WHY: модовые станки (верстак оружия TACZ) крафтят по ключу, поэтому отказ по ключу даётся
    // WHY: серверу и только рецептам не ванильных типов. Книга рецептов спрашивает ключ через
    // WHY: keyUnfiltered (миксины ServerRecipeBook, ServerPlayer, KnowledgeBookItem): отказ там
    // WHY: стирал открытый рецепт из книги игрока навсегда, и у модовых типов книга тоже бывает
    public static boolean blocksKeyed(RecipeManager manager, Recipe<?> recipe) {
        CraftBanSet bans = server;
        if (bans.isEmpty() || paused()) return false;

        MinecraftServer current = ServerLifecycleHooks.getCurrentServer();
        if (current == null || current.getRecipeManager() != manager) return false;
        if (vanilla(recipe)) return false;
        return bans.blocks(resultOf(recipe, current.registryAccess()), running(false));
    }

    // WHY: модовые станки строят свой список рецептов полным перечнем типа (TACZ на клиенте рисует
    // WHY: так витрину верстака оружия), поэтому запрещённое из перечня убирается на обеих сторонах.
    // WHY: Ванильные типы не трогаются: кузнечный стол держит этим перечнем шаблоны для shift-клика
    public static boolean blocksListed(RecipeManager manager, Recipe<?> recipe) {
        MinecraftServer current = ServerLifecycleHooks.getCurrentServer();
        boolean serverSide = current != null && current.getRecipeManager() == manager;
        CraftBanSet bans = set(!serverSide);
        if (bans.isEmpty() || vanilla(recipe)) return false;
        RegistryAccess access = serverSide ? current.registryAccess() : builtIn();
        return bans.blocks(resultOf(recipe, access), running(!serverSide));
    }

    // WHY: варка зелий идёт мимо RecipeManager, через реестр Forge, и спрашивается без мира. Сторона
    // WHY: берётся по потоку: стойка варит в серверном тике, а клиент спрашивает только для витрин
    public static boolean blocksBrewed(ItemStack output) {
        if (output.isEmpty()) return false;
        MinecraftServer current = ServerLifecycleHooks.getCurrentServer();
        boolean serverSide = current != null && current.isSameThread();
        CraftBanSet bans = set(!serverSide);
        return !bans.isEmpty() && bans.blocks(output, running(!serverSide));
    }

    private static boolean vanilla(Recipe<?> recipe) {
        ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        return type == null || VANILLA.equals(type.getNamespace());
    }

    // WHY: образец результата чужого рецепта может требовать данных, которых у этой стороны нет,
    // WHY: и бросить исключение: такой рецепт считается незапрещённым, поиск от него не падает
    private static ItemStack resultOf(Recipe<?> recipe, RegistryAccess access) {
        try {
            return recipe.getResultItem(access);
        } catch (RuntimeException unreadable) {
            return ItemStack.EMPTY;
        }
    }

    // WHY: сборка зовётся и на рецептах, которые ваниль сама не собрала бы (запасной обход после
    // WHY: запрещённого), поэтому сломанная сборка чужого рецепта не имеет права уронить слот крафта
    // WHY: или тик печи: судит тогда образец результата, а без образца рецепт считается незапрещённым
    private static <C extends Container> ItemStack assembledOf(Recipe<C> recipe, C container, RegistryAccess access,
                                                               ItemStack sample) {
        try {
            return recipe.assemble(container, access);
        } catch (RuntimeException unassembled) {
            return sample;
        }
    }

    // WHY: на клиенте мира сервера под рукой нет, а общий код не имеет права звать клиентские классы.
    // WHY: Образцу результата хватает встроенных реестров, динамические (биомы, отделка) ему не нужны
    private static RegistryAccess builtIn() {
        RegistryAccess access = builtIn;
        if (access == null) {
            access = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            builtIn = access;
        }
        return access;
    }

    @Nullable
    public static CraftBan clientBan(ItemStack stack) {
        return client.find(stack, clientRunning);
    }

    public static List<CraftBan> clientBans() {
        return client.all();
    }

    public static boolean clientRunning() {
        return clientRunning;
    }

    private static CraftBanSet set(boolean clientSide) {
        return clientSide ? client : server;
    }

    private static boolean running(boolean clientSide) {
        return clientSide ? clientRunning : BattleCraftManager.getInstance().matchRunning();
    }
}
