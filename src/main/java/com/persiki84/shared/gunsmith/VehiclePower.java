package com.persiki84.shared.gunsmith;

import java.util.List;

public record VehiclePower(int energy, List<Cell> cells) {

    public record Cell(String name, int count) {}
}
