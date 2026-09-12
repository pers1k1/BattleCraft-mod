package com.persiki84.battlecraft.client.custom;

import java.nio.file.Path;
import java.util.Set;

public record UserPreset(String name, Path file, Set<ConfigSection> sections, String created, ConfigDoc doc) {
}
