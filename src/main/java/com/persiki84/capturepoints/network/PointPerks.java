package com.persiki84.capturepoints.network;

import com.persiki84.battlecraft.mode.MatchModes;
import com.persiki84.capturepoints.capture.CapturePoint;
import com.persiki84.capturepoints.capture.CapturePointManager;
import com.persiki84.capturepoints.capture.FinalCapturePoint;
import com.persiki84.capturepoints.capture.IncomePayout;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

// WHY: что даёт точка, клиенту нужно только для окна точки на карте. Число очков решает сервер:
// WHY: своё у точки или из режима матча, а цифр режима у игрока нет. Пустой предмет дохода - валюта
public record PointPerks(int score, ItemStack income, int incomeAmount, int incomeSeconds, IncomePayout payout,
                         ItemStack reward, int rewardAmount, int captureSeconds, String buff, int buffLevel) {
    private static final int TICKS_PER_SECOND = 20;
    private static final int BUFF_LIMIT = 256;

    public static PointPerks of(CapturePoint point) {
        boolean last = point instanceof FinalCapturePoint;
        int score = last ? 0 : MatchModes.pointWorth(point);
        int income = last ? 0 : point.getPassiveIncomeAmount();
        String buff = point.getBuffEffect() == null ? "" : point.getBuffEffect();
        return new PointPerks(score, point.getIncomeItem().copy(), income, point.getIncomeIntervalSeconds(),
                CapturePointManager.incomePayout(), point.getReward().copy(), point.getRewardAmount(),
                point.getCaptureTime() / TICKS_PER_SECOND, buff, point.getBuffAmplifier());
    }

    // WHY: пакет точек приходит заново на любом событии и несёт новые записи; окно точки на карте
    // WHY: пересобирается только по сменившемуся содержанию, а стаки у записи сравниваются по сути
    public boolean sameAs(PointPerks other) {
        return other != null && score == other.score && incomeAmount == other.incomeAmount
                && incomeSeconds == other.incomeSeconds && payout == other.payout && rewardAmount == other.rewardAmount
                && captureSeconds == other.captureSeconds && buffLevel == other.buffLevel && buff.equals(other.buff)
                && ItemStack.matches(income, other.income) && ItemStack.matches(reward, other.reward);
    }

    public boolean currencyIncome() {
        return income.isEmpty();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(score);
        buf.writeItem(income);
        buf.writeVarInt(incomeAmount);
        buf.writeVarInt(incomeSeconds);
        buf.writeEnum(payout);
        buf.writeItem(reward);
        buf.writeVarInt(rewardAmount);
        buf.writeVarInt(captureSeconds);
        buf.writeUtf(buff, BUFF_LIMIT);
        buf.writeVarInt(buffLevel);
    }

    public static PointPerks read(FriendlyByteBuf buf) {
        return new PointPerks(buf.readVarInt(), buf.readItem(), buf.readVarInt(), buf.readVarInt(),
                buf.readEnum(IncomePayout.class), buf.readItem(), buf.readVarInt(), buf.readVarInt(),
                buf.readUtf(BUFF_LIMIT), buf.readVarInt());
    }
}
