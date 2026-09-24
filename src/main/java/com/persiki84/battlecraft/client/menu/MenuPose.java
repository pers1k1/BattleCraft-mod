package com.persiki84.battlecraft.client.menu;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

public final class MenuPose {
    private static final float REACH = -1.04f;
    private static final float TURN = 0.28f;
    private static final float TUCK = 0.10f;
    private static final float TAP = 0.05f;
    private static final float TAP_SPEED = 0.26f;
    private static final float HELD = 0.01f;

    private MenuPose() {}

    public static boolean posed(LivingEntity entity) {
        return !entity.isSwimming() && !entity.isFallFlying() && !entity.isSleeping()
                && !entity.isAutoSpinAttack();
    }

    public static boolean holding(float hold) {
        return hold > HELD;
    }

    // WHY: рукава копируются заново: PlayerModel снимает их с рук в начале своей анимации,
    // WHY: и поза, наложенная после неё, оставила бы куртку висеть вдоль тела
    public static void apply(PlayerModel<?> model, float hold, float age) {
        float tap = Mth.sin(age * TAP_SPEED) * TAP * hold;

        arm(model.rightArm, hold, -TURN, -TUCK, tap);
        arm(model.leftArm, hold, TURN, TUCK, -tap);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
    }

    private static void arm(ModelPart arm, float hold, float turn, float tuck, float tap) {
        arm.xRot = Mth.lerp(hold, arm.xRot, REACH + tap);
        arm.yRot = Mth.lerp(hold, arm.yRot, turn);
        arm.zRot = Mth.lerp(hold, arm.zRot, tuck);
    }
}
