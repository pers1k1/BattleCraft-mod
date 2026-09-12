package com.persiki84.shared.client.ui;

public final class UiPalette {
    private static int backdrop = UiTheme.BACKDROP;
    private static int panel = UiTheme.PANEL;
    private static int panelRaised = UiTheme.PANEL_RAISED;
    private static int panelDeep = UiTheme.PANEL_DEEP;
    private static int glassTop = UiTheme.GLASS_TOP;
    private static int glassBottom = UiTheme.GLASS_BOTTOM;
    private static int glassTopLit = UiTheme.GLASS_TOP_LIT;
    private static int glassBottomLit = UiTheme.GLASS_BOTTOM_LIT;
    private static int wellTop = UiTheme.GLASS_WELL_TOP;
    private static int wellBottom = UiTheme.GLASS_WELL_BOTTOM;
    private static int alert = UiTheme.ALERT;
    private static int alertDim = UiTheme.ALERT_DIM;
    private static int stroke = UiTheme.STROKE;

    private UiPalette() {}

    public static void reset() {
        backdrop = UiTheme.BACKDROP;
        panel = UiTheme.PANEL;
        panelRaised = UiTheme.PANEL_RAISED;
        panelDeep = UiTheme.PANEL_DEEP;
        glassTop = UiTheme.GLASS_TOP;
        glassBottom = UiTheme.GLASS_BOTTOM;
        glassTopLit = UiTheme.GLASS_TOP_LIT;
        glassBottomLit = UiTheme.GLASS_BOTTOM_LIT;
        wellTop = UiTheme.GLASS_WELL_TOP;
        wellBottom = UiTheme.GLASS_WELL_BOTTOM;
        alert = UiTheme.ALERT;
        alertDim = UiTheme.ALERT_DIM;
        stroke = UiTheme.STROKE;
    }

    public static void backdrop(int argb) {
        backdrop = argb;
    }

    public static void panel(int argb) {
        float share = share(UiTheme.PANEL, argb);
        panel = scaledAlpha(UiTheme.PANEL, argb, share);
        panelRaised = scaledAlpha(UiTheme.PANEL_RAISED, UiTheme.lighten(argb, 0.10f), share);
        panelDeep = scaledAlpha(UiTheme.PANEL_DEEP, UiTheme.mix(argb, UiTheme.BLACK, 0.35f), share);
    }

    // WHY: выбранная альфа обязана доходить до цвета, но заводская разница плотности между слоями сохраняется
    public static void glass(int top, int bottom) {
        float share = share(UiTheme.GLASS_TOP, top);
        glassTop = scaledAlpha(UiTheme.GLASS_TOP, top, share);
        glassBottom = scaledAlpha(UiTheme.GLASS_BOTTOM, bottom, share);
        glassTopLit = scaledAlpha(UiTheme.GLASS_TOP_LIT, UiTheme.lighten(top, 0.22f), share);
        glassBottomLit = scaledAlpha(UiTheme.GLASS_BOTTOM_LIT, UiTheme.lighten(bottom, 0.18f), share);
    }

    public static void well(int top, int bottom) {
        float share = share(UiTheme.GLASS_WELL_TOP, top);
        wellTop = scaledAlpha(UiTheme.GLASS_WELL_TOP, top, share);
        wellBottom = scaledAlpha(UiTheme.GLASS_WELL_BOTTOM, bottom, share);
    }

    public static void alert(int argb) {
        float share = share(UiTheme.ALERT, argb);
        alert = scaledAlpha(UiTheme.ALERT, argb, share);
        alertDim = scaledAlpha(UiTheme.ALERT_DIM, UiTheme.mix(argb, UiTheme.BLACK, 0.42f), share);
    }

    public static void stroke(int argb) {
        stroke = argb;
    }

    public static int backdrop() {
        return backdrop;
    }

    public static int panel() {
        return panel;
    }

    public static int panelRaised() {
        return panelRaised;
    }

    public static int panelDeep() {
        return panelDeep;
    }

    public static int glassTop() {
        return glassTop;
    }

    public static int glassBottom() {
        return glassBottom;
    }

    public static int glassTopLit() {
        return glassTopLit;
    }

    public static int glassBottomLit() {
        return glassBottomLit;
    }

    public static int wellTop() {
        return wellTop;
    }

    public static int wellBottom() {
        return wellBottom;
    }

    public static int alert() {
        return alert;
    }

    public static int alertDim() {
        return alertDim;
    }

    public static int stroke() {
        return stroke;
    }

    private static float share(int reference, int argb) {
        return ((argb >>> 24) & 0xFF) / (float) ((reference >>> 24) & 0xFF);
    }

    private static int scaledAlpha(int reference, int argb, float share) {
        int opacity = Math.max(0, Math.min(255, Math.round(((reference >>> 24) & 0xFF) * share)));
        return (opacity << 24) | (argb & 0x00FFFFFF);
    }
}
