package com.persiki84.battlecraft.client.island;

import com.mojang.blaze3d.platform.NativeImage;
import com.persiki84.battlecraft.BattleCraftMod;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.function.Consumer;

// WHY: своя картинка игрока копируется в папку игры квадратом 256 px и дальше читается только оттуда:
// WHY: исходник могут удалить или подменить, а огромный снимок не должен декодироваться каждый запуск.
// WHY: Картинка видна только самому игроку и на сервер не уходит, поэтому модерировать нечего
public final class AvatarPicture {
    private static final ResourceLocation TARGET = new ResourceLocation("battlecraft", "island/picture");
    private static final String FOLDER = "battlecraft";
    private static final String FILE = "avatar.png";
    private static final long SOURCE_LIMIT = 16L << 20;
    private static final int SIDE_LIMIT = 16384;
    private static final String[] PATTERNS = {"*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"};
    private static final String TOO_BIG = "battlecraft.custom.island.avatar_picture.too_big";
    private static final String UNREADABLE = "battlecraft.custom.island.avatar_picture.unreadable";

    private static volatile boolean ready;
    private static volatile boolean loading;
    private static volatile boolean asking;
    private static volatile boolean broken;
    private static volatile Boolean present;

    private AvatarPicture() {}

    private static final class Refused extends IOException {
        private final String key;

        private Refused(String key) {
            super(key);
            this.key = key;
        }
    }

    public static boolean ready() {
        return ready;
    }

    public static ResourceLocation texture() {
        return TARGET;
    }

    public static boolean present() {
        Boolean known = present;
        if (known == null) {
            known = Files.isRegularFile(file());
            present = known;
        }
        return known;
    }

    // WHY: остров зовёт это каждый кадр, поэтому битый файл не перечитывается по кругу: после
    // WHY: неудачи загрузка ждёт нового выбора картинки
    public static void ensure() {
        if (ready || loading || broken || !present()) return;
        loading = true;
        Util.backgroundExecutor().execute(AvatarPicture::load);
    }

    private static void load() {
        NativeImage[] levels = null;
        try {
            byte[] encoded = Files.readAllBytes(file());
            try (NativeImage decoded = NativeImage.read(new ByteArrayInputStream(encoded));
                 NativeImage squared = IslandImage.squared(decoded)) {
                levels = IslandScale.chain(squared, IslandImage.CORNER_SHARE);
            }
            NativeImage[] carried = levels;
            Minecraft.getInstance().execute(() -> upload(carried));
        } catch (Exception error) {
            IslandPicture.discard(levels);
            broken = true;
            loading = false;
            BattleCraftMod.LOGGER.warn("[battlecraft] own avatar unavailable: {}", error.toString());
        }
    }

    private static void upload(NativeImage[] levels) {
        try {
            IslandPicture picture = IslandPicture.upload(levels);
            Minecraft.getInstance().getTextureManager().register(TARGET, picture);
            ready = true;
        } catch (Exception error) {
            IslandPicture.discard(levels);
            broken = true;
            BattleCraftMod.LOGGER.warn("[battlecraft] own avatar rejected: {}", error.toString());
        } finally {
            loading = false;
        }
    }

    // WHY: диалог выбора модальный и ждёт игрока, поэтому живёт в своём потоке, как выбор обоев
    public static void pick(Runnable done, Consumer<Component> failed) {
        if (asking) return;
        asking = true;
        String title = Component.translatable("battlecraft.custom.island.avatar_picture.pick").getString();
        String kinds = Component.translatable("battlecraft.custom.island.avatar_picture.kinds").getString();
        Thread thread = new Thread(() -> ask(title, kinds, done, failed), "battlecraft-avatar-picker");
        thread.setDaemon(true);
        thread.start();
    }

    private static void ask(String title, String kinds, Runnable done, Consumer<Component> failed) {
        try {
            Path chosen = open(title, kinds);
            if (chosen == null) return;
            importFrom(chosen);
            Minecraft.getInstance().execute(() -> {
                ready = false;
                broken = false;
                present = true;
                ensure();
                done.run();
            });
        } catch (Refused refused) {
            Minecraft.getInstance().execute(() -> failed.accept(Component.translatable(refused.key)));
        } catch (Throwable error) {
            BattleCraftMod.LOGGER.warn("[battlecraft] own avatar import failed: {}", String.valueOf(error));
            Minecraft.getInstance().execute(() -> failed.accept(Component.translatable(UNREADABLE)));
        } finally {
            asking = false;
        }
    }

    private static Path open(String title, String kinds) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(PATTERNS.length);
            for (String pattern : PATTERNS) filters.put(stack.UTF8(pattern));
            filters.flip();
            String answer = TinyFileDialogs.tinyfd_openFileDialog(title, startFolder(), filters, kinds, false);
            return answer == null || answer.isBlank() ? null : Path.of(answer);
        }
    }

    private static String startFolder() {
        Path pictures = Path.of(System.getProperty("user.home"), "Pictures");
        Path folder = Files.isDirectory(pictures) ? pictures : Path.of(System.getProperty("user.home"));
        return folder + java.io.File.separator;
    }

    private static void importFrom(Path source) throws IOException {
        if (!Files.isRegularFile(source) || Files.size(source) > SOURCE_LIMIT) throw new Refused(TOO_BIG);

        BufferedImage fitted = shrunk(cropped(decode(source)), IslandScale.EDGE_LIMIT);
        Path target = file();
        Files.createDirectories(target.getParent());
        Path staging = target.resolveSibling(FILE + ".part");
        if (!ImageIO.write(fitted, "png", staging.toFile())) throw new Refused(UNREADABLE);
        Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
    }

    // WHY: размер берётся из заголовка до декодирования, и большой снимок читается прореженным:
    // WHY: в память ложится кадр чуть больше аватара, а не полный растр на сотни мегабайт
    private static BufferedImage decode(Path source) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(source.toFile())) {
            Iterator<ImageReader> readers = input == null ? null : ImageIO.getImageReaders(input);
            if (readers == null || !readers.hasNext()) throw new Refused(UNREADABLE);

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > SIDE_LIMIT || height > SIDE_LIMIT) throw new Refused(TOO_BIG);
                ImageReadParam param = reader.getDefaultReadParam();
                int step = Math.max(1, Math.min(width, height) / (IslandScale.EDGE_LIMIT * 2));
                param.setSourceSubsampling(step, step, 0, 0);
                BufferedImage image = reader.read(0, param);
                if (image == null) throw new Refused(UNREADABLE);
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage cropped(BufferedImage image) {
        int side = Math.min(image.getWidth(), image.getHeight());
        return image.getSubimage((image.getWidth() - side) / 2, (image.getHeight() - side) / 2, side, side);
    }

    // WHY: уменьшение идёт половинами и только последний шаг бикубикой: одним прыжком в разы
    // WHY: билинейка выбрасывает пиксели, и мелкий узор рябит
    private static BufferedImage shrunk(BufferedImage square, int edge) {
        BufferedImage current = redrawn(square, square.getWidth());
        while (current.getWidth() / 2 >= edge) {
            current = redrawn(current, current.getWidth() / 2);
        }
        return current.getWidth() == edge ? current : redrawn(current, edge);
    }

    private static BufferedImage redrawn(BufferedImage source, int side) {
        BufferedImage target = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D canvas = target.createGraphics();
        try {
            canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            canvas.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            canvas.drawImage(source, 0, 0, side, side, null);
        } finally {
            canvas.dispose();
        }
        return target;
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(FOLDER).resolve(FILE);
    }
}
