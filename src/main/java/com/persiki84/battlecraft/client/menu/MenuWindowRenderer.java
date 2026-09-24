package com.persiki84.battlecraft.client.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.client.menu.MenuPresenceClient.Presence;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class MenuWindowRenderer {
    private static final float RANGE = 15.0f;
    private static final float FADE = 3.0f;
    private static final int MOST = 6;
    private static final float WIDE = 1.5f;
    private static final float LIVE_FLATTEST = 0.4f;
    private static final float LIVE_TALLEST = 1.0f;
    private static final float LIVE_OPACITY = 0.94f;
    private static final float REACH = 0.62f;
    private static final float LIFT = 0.66f;
    private static final float TILT = 8.0f;
    private static final float HALF_TURN = 180.0f;
    private static final float OPEN_WIDTH = 0.9f;
    private static final float MIN_VISIBLE = 0.02f;
    private static final float TOP_TINT = 0.34f;
    private static final float BOTTOM_TINT = 0.44f;
    private static final float POINTER_ALPHA = 0.9f;
    private static final float POINTER_SPAN = 0.28f;
    private static final float POINTER_RISE = 0.22f;
    private static final float POINTER_MIDDLE = 0.62f;

    private static final Matrix4f view = new Matrix4f();
    private static final Matrix4f projection = new Matrix4f();
    private static final Matrix4f saved = new Matrix4f();
    private static final List<Placed> placed = new ArrayList<>();
    private static final int[] cards = new int[MOST];
    private static final Comparator<Placed> FAR_FIRST = Comparator.comparingDouble(Placed::away).reversed();
    private static long viewed = -1L;

    private MenuWindowRenderer() {}

    // WHY: вид снимается на стадии погоды, а рисуется окно после всего мира: у стадии AFTER_LEVEL в
    // WHY: стеке лежит проекция, а не вид, зато кадр уже собран целиком и руки игрока в нём ещё нет
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            view.set(event.getPoseStack().last().pose());
            projection.set(event.getProjectionMatrix());
            viewed = UiFrame.frame();
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || viewed != UiFrame.frame()) return;

        collect(event.getCamera().getPosition(), event.getPartialTick());
        if (placed.isEmpty()) return;

        try {
            draw();
        } finally {
            placed.clear();
        }
    }

    private static void collect(Vec3 camera, float partial) {
        placed.clear();
        List<Presence> live = MenuPresenceClient.live();
        if (live.isEmpty()) return;

        for (Presence presence : live) {
            Placed spot = place(presence, camera, partial);
            if (spot != null) placed.add(spot);
        }
        placed.sort(FAR_FIRST);
        while (placed.size() > MOST) placed.remove(0);
    }

    private static Placed place(Presence presence, Vec3 camera, float partial) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || presence.face() == null) return null;

        Player owner = client.level.getPlayerByUUID(presence.player());
        if (owner == null || owner.isSpectator() || owner.isInvisibleTo(client.player)) return null;
        if (owner == client.player && client.options.getCameraType().isFirstPerson()) return null;

        double x = Mth.lerp(partial, owner.xOld, owner.getX()) - camera.x;
        double y = Mth.lerp(partial, owner.yOld, owner.getY()) + owner.getBbHeight() * LIFT - camera.y;
        double z = Mth.lerp(partial, owner.zOld, owner.getZ()) - camera.z;
        double away = Math.sqrt(x * x + y * y + z * z);
        float fade = away >= RANGE ? 0.0f : (float) Math.min(1.0, (RANGE - away) / FADE);
        float alpha = fade * presence.shown();
        if (alpha <= MIN_VISIBLE) return null;

        float yaw = Mth.rotLerp(partial, owner.yBodyRotO, owner.yBodyRot);
        return new Placed(presence, live(owner, client.player), x, y, z, yaw, away, alpha);
    }

    // WHY: чужой живой кадр показывается, только пока хозяин окна в одной команде со зрителем: сервер
    // WHY: шлёт кадры своим, но смена команды не отзывает уже присланный
    private static MenuFrameClient.Live live(Player owner, Player viewer) {
        if (owner != viewer && !owner.isAlliedTo(viewer)) return null;
        return MenuFrameClient.frame(owner.getUUID());
    }

    // WHY: карточки пекутся до настройки прохода: запекание ставит свой шейдер, отсечение и текстуры,
    // WHY: и посреди прохода окна после него рисовались бы чужим состоянием
    private static void draw() {
        ShaderInstance shader = MenuWindowShader.shader();
        if (shader == null) return;

        for (int slot = 0; slot < placed.size(); slot++) {
            Placed spot = placed.get(slot);
            cards[slot] = spot.live != null ? spot.live.texture().getId() : MenuWindowCard.texture(spot.presence.face());
        }
        int scene = MenuWindowScene.capture();
        if (scene == 0) return;

        saved.set(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        begin(shader, scene);
        try {
            for (int slot = 0; slot < placed.size(); slot++) window(shader, placed.get(slot), cards[slot]);
        } finally {
            end(sorting);
        }
    }

    private static void begin(ShaderInstance shader, int scene) {
        RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
        PoseStack stack = RenderSystem.getModelViewStack();
        stack.pushPose();
        stack.setIdentity();
        RenderSystem.applyModelViewMatrix();

        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(1, scene);
        tint(shader);
    }

    private static void end(VertexSorting sorting) {
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        UiRender.standardBlend();
        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(saved, sorting);
    }

    private static void tint(ShaderInstance shader) {
        int accent = UiAccent.color();
        shader.safeGetUniform("Accent").set(red(accent), green(accent), blue(accent), 1.0f);
        shader.safeGetUniform("BodyTop").set(red(UiTheme.GLASS_TOP), green(UiTheme.GLASS_TOP),
                blue(UiTheme.GLASS_TOP), TOP_TINT);
        shader.safeGetUniform("BodyBottom").set(red(UiTheme.GLASS_BOTTOM), green(UiTheme.GLASS_BOTTOM),
                blue(UiTheme.GLASS_BOTTOM), BOTTOM_TINT);
    }

    private static void window(ShaderInstance shader, Placed spot, int card) {
        if (card == 0) return;

        Presence presence = spot.presence;
        float tall = tallness(spot);
        source(shader, spot, tall);
        RenderSystem.setShaderTexture(0, card);

        Matrix4f matrix = pose(spot, presence.shown());
        float half = WIDE * tall / 2.0f;
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(matrix, -WIDE / 2.0f, -half, 0.0f).uv(0.0f, 1.0f).endVertex();
        builder.vertex(matrix, WIDE / 2.0f, -half, 0.0f).uv(1.0f, 1.0f).endVertex();
        builder.vertex(matrix, WIDE / 2.0f, half, 0.0f).uv(1.0f, 0.0f).endVertex();
        builder.vertex(matrix, -WIDE / 2.0f, half, 0.0f).uv(0.0f, 0.0f).endVertex();
        Tesselator.getInstance().end();
    }

    private static float tallness(Placed spot) {
        if (spot.live == null) return MenuWindowLayout.HEIGHT / MenuWindowLayout.WIDTH;
        return Mth.clamp((float) spot.live.height() / spot.live.width(), LIVE_FLATTEST, LIVE_TALLEST);
    }

    // WHY: живой кадр это снимок экрана: указатель в нём уже есть, и рисовать поверх свой нельзя;
    // WHY: текстура кадра лежит сверху вниз, а запечённая карточка снизу вверх, отсюда Source.x
    private static void source(ShaderInstance shader, Placed spot, float tall) {
        float width = MenuWindowLayout.WIDTH;
        shader.safeGetUniform("Shape").set(width, width * tall, MenuWindowLayout.RADIUS, MenuWindowLayout.BAND);
        if (spot.live != null) {
            shader.safeGetUniform("Frame").set(spot.alpha, 0.0f, 0.0f, 0.0f);
            shader.safeGetUniform("Source").set(0.0f, LIVE_OPACITY, 0.0f, 0.0f);
            return;
        }
        float age = spot.presence.age();
        float pointerX = width * (0.5f + Mth.cos(age * 1.3f) * POINTER_SPAN);
        float pointerY = MenuWindowLayout.HEIGHT * (POINTER_MIDDLE + Mth.sin(age * 0.9f) * POINTER_RISE);
        shader.safeGetUniform("Frame").set(spot.alpha, pointerX, pointerY, POINTER_ALPHA);
        shader.safeGetUniform("Source").set(1.0f, 1.0f, 0.0f, 0.0f);
    }

    // WHY: окно принадлежит игроку, а не камере: оно стоит перед ним и поворачивается с корпусом;
    // WHY: лицевая сторона выбирается по камере, иначе смотрящий из-за спины читал бы зеркальный текст
    private static Matrix4f pose(Placed spot, float shown) {
        PoseStack pose = new PoseStack();
        pose.last().pose().set(view);
        pose.translate(spot.x, spot.y, spot.z);
        pose.mulPose(Axis.YP.rotationDegrees(-spot.yaw));
        pose.translate(0.0f, 0.0f, REACH);
        pose.mulPose(Axis.XP.rotationDegrees(TILT));
        if (!facing(spot)) pose.mulPose(Axis.YP.rotationDegrees(HALF_TURN));
        pose.scale(OPEN_WIDTH + (1.0f - OPEN_WIDTH) * shown, shown, 1.0f);
        return pose.last().pose();
    }

    private static boolean facing(Placed spot) {
        float radians = spot.yaw * Mth.DEG_TO_RAD;
        double aheadX = -Mth.sin(radians);
        double aheadZ = Mth.cos(radians);
        return (spot.x + aheadX * REACH) * aheadX + (spot.z + aheadZ * REACH) * aheadZ < 0.0;
    }

    private static float red(int color) {
        return ((color >> 16) & 0xFF) / 255.0f;
    }

    private static float green(int color) {
        return ((color >> 8) & 0xFF) / 255.0f;
    }

    private static float blue(int color) {
        return (color & 0xFF) / 255.0f;
    }

    private record Placed(Presence presence, MenuFrameClient.Live live, double x, double y, double z,
                          float yaw, double away, float alpha) {}
}
