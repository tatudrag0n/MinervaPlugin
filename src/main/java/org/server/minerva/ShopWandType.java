package org.server.minerva;

import java.util.Arrays;
import java.util.Locale;

enum ShopWandType {
    SHELF("shelf"),
    BARREL("barrel"),
    FRAME("frame");

    private final String key;

    ShopWandType(String key) {
        this.key = key;
    }

    String key() {
        return key;
    }

    static ShopWandType fromKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(type -> type.key.equals(normalized))
                .findFirst()
                .orElse(null);
    }
}
