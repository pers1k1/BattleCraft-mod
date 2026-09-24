package com.persiki84.airdrop.config;

// WHY: пределы одни на конфиг, команды и меню: команда принимала interval 5 и despawn 0, а спек
// WHY: конфига начинался с 10 и 1, и при следующем чтении файла Forge молча возвращал такое
// WHY: значение к умолчанию - настройка «выбивалась» сама через минуту после правки
public final class AirDropLimits {
    public static final int TICKS_PER_SECOND = 20;
    public static final int COORDINATE = 30000000;
    public static final int RADIUS_MIN = 10;
    public static final int RADIUS_MAX = 30000;
    public static final int INTERVAL_MIN = 10;
    public static final int INTERVAL_MAX = 86400;
    public static final int PERCENT = 100;
    public static final int FLIGHT_MIN = 1;
    public static final int FLIGHT_MAX = 300;
    public static final int OPEN_DELAY_MAX = 300;
    public static final int DESPAWN_MIN = 1;
    public static final int DESPAWN_MAX = 86400;
    public static final int WARN_MAX = 3600;
    public static final int HEIGHT_MIN = -64;
    public static final int HEIGHT_MAX = 2000;

    private AirDropLimits() {}
}
