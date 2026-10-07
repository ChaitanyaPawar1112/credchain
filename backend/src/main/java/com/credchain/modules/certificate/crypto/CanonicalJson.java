package com.credchain.modules.certificate.crypto;

import java.util.Map;
import java.util.TreeMap;

/**
 * Deterministic JSON for hashing: one flat object of string values,
 * keys sorted, no whitespace, null values omitted, JSON.stringify-compatible escaping.
 * The same input always produces exactly the same bytes.
 */
public final class CanonicalJson {

    private CanonicalJson() {
    }

    public static String write(Map<String, String> fields) {
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : new TreeMap<>(fields).entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if (!first) {
                out.append(',');
            }
            first = false;
            appendString(out, entry.getKey());
            out.append(':');
            appendString(out, entry.getValue());
        }
        return out.append('}').toString();
    }


    private static void appendString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);   // everything else as-is (UTF-8 when encoded)
                    }
                }
            }
        }
        out.append('"');
    }
}