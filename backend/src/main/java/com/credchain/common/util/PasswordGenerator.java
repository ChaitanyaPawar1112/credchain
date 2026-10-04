package com.credchain.common.util;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generates temporary passwords that always satisfy the @StrongPassword policy.
 * Ambiguous characters (0/O, 1/l/I) are excluded so passwords are easy to type.
 */
public final class PasswordGenerator {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "@#$%&*!?";
    private static final String ALL = UPPER + LOWER + DIGITS + SYMBOLS;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordGenerator() {
    }

    public static String generate(int length) {
        if (length < 12) {
            throw new IllegalArgumentException("Temporary passwords must be at least 12 characters");
        }
        List<Character> chars = new ArrayList<>(length);
        chars.add(pick(UPPER));      // guarantee one of each category
        chars.add(pick(LOWER));
        chars.add(pick(DIGITS));
        chars.add(pick(SYMBOLS));

        while (chars.size() < length) {
            chars.add(pick(ALL));
        }
        Collections.shuffle(chars, RANDOM);

        StringBuilder sb = new StringBuilder(length);
        chars.forEach(sb::append);
        return sb.toString();
    }

    private static char pick(String source) {
        return source.charAt(RANDOM.nextInt(source.length()));
    }
}