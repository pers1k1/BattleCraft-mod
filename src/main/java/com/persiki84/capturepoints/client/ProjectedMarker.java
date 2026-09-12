package com.persiki84.capturepoints.client;

public final class ProjectedMarker {
    private String name;
    private String owner;
    private boolean isFinal;
    private double distance;
    private float screenX;
    private float screenY;
    private Integer explicitColor;

    public ProjectedMarker set(String name, String owner, boolean isFinal, double distance,
                               float screenX, float screenY, Integer explicitColor) {
        this.name = name;
        this.owner = owner;
        this.isFinal = isFinal;
        this.distance = distance;
        this.screenX = screenX;
        this.screenY = screenY;
        this.explicitColor = explicitColor;
        return this;
    }

    public String name() { return name; }
    public String owner() { return owner; }
    public boolean isFinal() { return isFinal; }
    public double distance() { return distance; }
    public float screenX() { return screenX; }
    public float screenY() { return screenY; }
    public Integer explicitColor() { return explicitColor; }
}
