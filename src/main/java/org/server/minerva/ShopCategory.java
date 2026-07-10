package org.server.minerva;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

enum ShopCategory {
    FOOD("food"),
    COMBAT("combat"),
    DECORATION("decoration"),
    MATERIAL("material"),
    DROP("drop"),
    VALUABLES("valuables"),
    OTHERS("others");

    private final String key;

    ShopCategory(String key) {
        this.key = key;
    }

    String key() {
        return key;
    }

    static ShopCategory fromKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(category -> category.key.equals(normalized))
                .findFirst()
                .orElse(null);
    }

    static List<String> keys() {
        return Arrays.stream(values()).map(ShopCategory::key).toList();
    }
}
