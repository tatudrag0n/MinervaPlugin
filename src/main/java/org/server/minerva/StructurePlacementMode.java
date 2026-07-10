package org.server.minerva;

import java.util.Arrays;
import java.util.Locale;

enum StructurePlacementMode {
    UNDERGROUND("underground"),
    SEMIUNDERGROUND("semiunderground"),
    GROUND("ground"),
    SKY("sky"),
    RANGE("range");

    private final String key;

    StructurePlacementMode(String key) {
        this.key = key;
    }

    String key() {
        return key;
    }

    static StructurePlacementMode fromKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(mode -> mode.key.equals(normalized))
                .findFirst()
                .orElse(null);
    }
}
