package com.persiki84.shared.client.ui;

// WHY: смена палитры это скачок всего экрана разом, и резкая подмена читается как мигание, а не
// WHY: как выбор. Каждый цвет переезжает своим UiWash: мастер первого запуска и кастомизация
// WHY: назначают цвета по нескольку раз за вызов, а переезд меряется от показанного, поэтому
// WHY: промежуточные значения не видны вовсе. Первое применение конфига встаёт без переезда
public final class UiPalette {
    private static final float WASH_SECONDS = 0.44f;

    private static final UiWash backdrop = new UiWash(WASH_SECONDS, UiTheme.BACKDROP);
    private static final UiWash panel = new UiWash(WASH_SECONDS, UiTheme.PANEL);
    private static final UiWash panelRaised = new UiWash(WASH_SECONDS, UiTheme.PANEL_RAISED);
    private static final UiWash panelDeep = new UiWash(WASH_SECONDS, UiTheme.PANEL_DEEP);
    private static final UiWash glassTop = new UiWash(WASH_SECONDS, UiTheme.GLASS_TOP);
    private static final UiWash glassBottom = new UiWash(WASH_SECONDS, UiTheme.GLASS_BOTTOM);
    private static final UiWash glassTopLit = new UiWash(WASH_SECONDS, UiTheme.GLASS_TOP_LIT);
    private static final UiWash glassBottomLit = new UiWash(WASH_SECONDS, UiTheme.GLASS_BOTTOM_LIT);
    private static final UiWash wellTop = new UiWash(WASH_SECONDS, UiTheme.GLASS_WELL_TOP);
    private static final UiWash wellBottom = new UiWash(WASH_SECONDS, UiTheme.GLASS_WELL_BOTTOM);
    private static final UiWash alert = new UiWash(WASH_SECONDS, UiTheme.ALERT);
    private static final UiWash alertDim = new UiWash(WASH_SECONDS, UiTheme.ALERT_DIM);
    private static final UiWash stroke = new UiWash(WASH_SECONDS, UiTheme.STROKE);

    private static final UiWash[] ALL = {
            backdrop, panel, panelRaised, panelDeep, glassTop, glassBottom, glassTopLit,
            glassBottomLit, wellTop, wellBottom, alert, alertDim, stroke
    };

    private UiPalette() {}

    public static void advance(float delta) {
        for (UiWash wash : ALL) {
            wash.advance(delta);
        }
    }

    public static void reset() {
        backdrop.aim(UiTheme.BACKDROP);
        panel.aim(UiTheme.PANEL);
        panelRaised.aim(UiTheme.PANEL_RAISED);
        panelDeep.aim(UiTheme.PANEL_DEEP);
        glassTop.aim(UiTheme.GLASS_TOP);
        glassBottom.aim(UiTheme.GLASS_BOTTOM);
        glassTopLit.aim(UiTheme.GLASS_TOP_LIT);
        glassBottomLit.aim(UiTheme.GLASS_BOTTOM_LIT);
        wellTop.aim(UiTheme.GLASS_WELL_TOP);
        wellBottom.aim(UiTheme.GLASS_WELL_BOTTOM);
        alert.aim(UiTheme.ALERT);
        alertDim.aim(UiTheme.ALERT_DIM);
        stroke.aim(UiTheme.STROKE);
    }

    public static void backdrop(int argb) {
        backdrop.aim(argb);
    }

    public static void panel(int argb) {
        float share = share(UiTheme.PANEL, argb);
        panel.aim(scaledAlpha(UiTheme.PANEL, argb, share));
        panelRaised.aim(scaledAlpha(UiTheme.PANEL_RAISED, UiTheme.lighten(argb, 0.10f), share));
        panelDeep.aim(scaledAlpha(UiTheme.PANEL_DEEP, UiTheme.mix(argb, UiTheme.BLACK, 0.35f), share));
    }

    // WHY: выбранная альфа обязана доходить до цвета, но заводская разница плотности между слоями сохраняется
    public static void glass(int top, int bottom) {
        float share = share(UiTheme.GLASS_TOP, top);
        glassTop.aim(scaledAlpha(UiTheme.GLASS_TOP, top, share));
        glassBottom.aim(scaledAlpha(UiTheme.GLASS_BOTTOM, bottom, share));
        glassTopLit.aim(scaledAlpha(UiTheme.GLASS_TOP_LIT, UiTheme.lighten(top, 0.22f), share));
        glassBottomLit.aim(scaledAlpha(UiTheme.GLASS_BOTTOM_LIT, UiTheme.lighten(bottom, 0.18f), share));
    }

    public static void well(int top, int bottom) {
        float share = share(UiTheme.GLASS_WELL_TOP, top);
        wellTop.aim(scaledAlpha(UiTheme.GLASS_WELL_TOP, top, share));
        wellBottom.aim(scaledAlpha(UiTheme.GLASS_WELL_BOTTOM, bottom, share));
    }

    public static void alert(int argb) {
        float share = share(UiTheme.ALERT, argb);
        alert.aim(scaledAlpha(UiTheme.ALERT, argb, share));
        alertDim.aim(scaledAlpha(UiTheme.ALERT_DIM, UiTheme.mix(argb, UiTheme.BLACK, 0.42f), share));
    }

    public static void stroke(int argb) {
        stroke.aim(argb);
    }

    public static int backdrop() {
        return backdrop.get();
    }

    public static int panel() {
        return panel.get();
    }

    public static int panelRaised() {
        return panelRaised.get();
    }

    public static int panelDeep() {
        return panelDeep.get();
    }

    public static int glassTop() {
        return glassTop.get();
    }

    public static int glassBottom() {
        return glassBottom.get();
    }

    public static int glassTopLit() {
        return glassTopLit.get();
    }

    public static int glassBottomLit() {
        return glassBottomLit.get();
    }

    public static int wellTop() {
        return wellTop.get();
    }

    public static int wellBottom() {
        return wellBottom.get();
    }

    public static int alert() {
        return alert.get();
    }

    public static int alertDim() {
        return alertDim.get();
    }

    public static int stroke() {
        return stroke.get();
    }

    private static float share(int reference, int argb) {
        return ((argb >>> 24) & 0xFF) / (float) ((reference >>> 24) & 0xFF);
    }

    private static int scaledAlpha(int reference, int argb, float share) {
        int opacity = Math.max(0, Math.min(255, Math.round(((reference >>> 24) & 0xFF) * share)));
        return (opacity << 24) | (argb & 0x00FFFFFF);
    }
}
