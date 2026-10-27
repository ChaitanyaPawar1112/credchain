package com.credchain.modules.verification.application;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides whether a value from the official record appears in text read by OCR from a photo or scan.
 * OCR makes small mistakes (a "1" read as "l", a dropped letter), so one wrong character is forgiven in longer words.
 */
final class PrintedTextMatcher {

    /** Words this long or longer may differ by one character. */
    private static final int FORGIVING_LENGTH = 5;

    private final Set<String> words;
    private final String compact;

    PrintedTextMatcher(String ocrText) {
        String normalized = normalize(ocrText);
        this.words = Arrays.stream(normalized.split(" ")).filter(w -> !w.isEmpty()).collect(Collectors.toSet());
        this.compact = normalized.replace(" ", "");
    }

    /** Names, programme titles: every word of the official value must be on the page. */
    boolean containsWords(String officialValue) {
        List<String> wanted = Arrays.stream(normalize(officialValue).split(" "))
                .filter(w -> w.length() >= 2)
                .toList();
        return !wanted.isEmpty() && wanted.stream().allMatch(this::hasWord);
    }

    /** Numbers and codes (PIT-PUNE-2026-000004, PIT2022CS002, 8.50): compared without spaces or punctuation. */
    boolean containsCode(String officialValue) {
        String code = normalize(officialValue).replace(" ", "");
        if (code.isEmpty()) {
            return false;
        }
        if (compact.contains(code)) {
            return true;
        }
        return code.length() >= FORGIVING_LENGTH && windowWithinOneEdit(code);
    }

    private boolean hasWord(String word) {
        if (words.contains(word)) {
            return true;
        }
        if (word.length() < FORGIVING_LENGTH) {
            return false;
        }
        return words.stream().anyMatch(w -> Math.abs(w.length() - word.length()) <= 1 && withinOneEdit(w, word));
    }

    /** Is there a stretch of the page text, about as long as the code, that differs from it by one character? */
    private boolean windowWithinOneEdit(String code) {
        for (int length = code.length() - 1; length <= code.length() + 1; length++) {
            for (int start = 0; start + length <= compact.length(); start++) {
                if (withinOneEdit(compact.substring(start, start + length), code)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** One replaced, added or missing character at most. */
    static boolean withinOneEdit(String a, String b) {
        if (Math.abs(a.length() - b.length()) > 1) {
            return false;
        }
        int i = 0;
        int j = 0;
        int edits = 0;
        while (i < a.length() && j < b.length()) {
            if (a.charAt(i) == b.charAt(j)) {
                i++;
                j++;
                continue;
            }
            if (++edits > 1) {
                return false;
            }
            if (a.length() > b.length()) {
                i++;
            } else if (a.length() < b.length()) {
                j++;
            } else {
                i++;
                j++;
            }
        }
        return edits + (a.length() - i) + (b.length() - j) <= 1;
    }

    /** Upper case, letters and digits only, single spaces. */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim();
    }
}
