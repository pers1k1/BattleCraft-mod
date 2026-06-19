package com.persiki84.capturepoints.client;

public class ProjectedMarker {
    public final String name;
    public final String owner;
    public final boolean isFinal;
    public final double distance;
    public final float screenX;
    public final float screenY;
    public final Integer explicitColor;

    public ProjectedMarker(String name, String owner, boolean isFinal, double distance, float screenX, float screenY) {
        this.name = name;
        this.owner = owner;
        this.isFinal = isFinal;
        this.distance = distance;
        this.screenX = screenX;
        this.screenY = screenY;
        this.explicitColor = null;
    }

    public ProjectedMarker(String name, String owner, boolean isFinal, double distance, float screenX, float screenY, Integer explicitColor) {
        this.name = name;
        this.owner = owner;
        this.isFinal = isFinal;
        this.distance = distance;
        this.screenX = screenX;
        this.screenY = screenY;
        this.explicitColor = explicitColor;
    }
}
