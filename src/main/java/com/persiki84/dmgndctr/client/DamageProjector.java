package com.persiki84.dmgndctr.client;

import com.persiki84.dmgndctr.DmgIndicatorMod;
import com.persiki84.shared.client.ui.UiFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;

@Mod.EventBusSubscriber(modid = DmgIndicatorMod.MODID, value = Dist.CLIENT)
public final class DamageProjector {
    private static final float MARGIN = 32.0f;

    private static final Vector4f scratch = new Vector4f();

    private DamageProjector() {}

    // WHY: цифра живёт в мире, а рисуется интерфейсом: точка удара проецируется здесь теми же
    // WHY: матрицами кадра, что и мировые метки, а оверлей кладёт её нашим пером поверх сцены
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        DamageNumbers.advance(UiFrame.delta());
        Minecraft minecraft = Minecraft.getInstance();
        float width = minecraft.getWindow().getGuiScaledWidth();
        float height = minecraft.getWindow().getGuiScaledHeight();
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f view = event.getPoseStack().last().pose();
        Matrix4f projection = event.getProjectionMatrix();

        for (DamageNumber number : DamageNumbers.all()) {
            if (number.alive) project(number, camera, view, projection, width, height);
        }
    }

    private static void project(DamageNumber number, Vec3 camera, Matrix4f view, Matrix4f projection,
                                float width, float height) {
        double dx = number.x - camera.x;
        double dy = number.y - camera.y;
        double dz = number.z - camera.z;
        scratch.set((float) dx, (float) dy, (float) dz, 1.0f);
        scratch.mul(view);
        scratch.mul(projection);
        if (scratch.w() <= 0.0f) {
            number.onScreen = false;
            return;
        }

        number.screenX = (scratch.x() / scratch.w() + 1.0f) * 0.5f * width;
        number.screenY = (1.0f - scratch.y() / scratch.w()) * 0.5f * height;
        number.distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        number.onScreen = number.screenX > -MARGIN && number.screenY > -MARGIN
                && number.screenX < width + MARGIN && number.screenY < height + MARGIN;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        DamageNumbers.clear();
    }
}
