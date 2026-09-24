package com.persiki84.capturepoints.client;

public record ProjectedMarker(String key, String name, String owner, boolean isFinal, boolean kept,
                              double distance, float screenX, float screenY, Integer explicitColor) {
}
