package com.credchain.modules.verification.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Comparing OCR text with the official record")
class PrintedTextMatcherTest {

    private static final String PAGE = """
            Pune Institute of Technology
            This is to certify that
            SNEHA J0SHI
            has been awarded the degree of B.Tech Computer Engineering
            Enrollment No. PIT2022CS002   Certificate No: PIT-PUNE-2026-000004
            CGPA 8.50
            """;

    private final PrintedTextMatcher page = new PrintedTextMatcher(PAGE);

    @Test
    @DisplayName("names match in any case, with one OCR mistake forgiven in longer words")
    void names() {
        assertThat(page.containsWords("Sneha Joshi")).isTrue();       // OCR read the O as a zero
        assertThat(page.containsWords("sneha joshi")).isTrue();
        assertThat(page.containsWords("Rahul Sharma")).isFalse();
        assertThat(page.containsWords("Sneha Patil")).isFalse();      // every word must be there
        assertThat(page.containsWords("B.Tech Computer Engineering")).isTrue();
        assertThat(page.containsWords("")).isFalse();
    }

    @Test
    @DisplayName("short words must match exactly: one wrong letter in a 4-letter word is a different word")
    void shortWords() {
        assertThat(new PrintedTextMatcher("Ravi Rao").containsWords("Ravi Rai")).isFalse();
    }

    @Test
    @DisplayName("codes match without spaces or punctuation, with one OCR mistake forgiven")
    void codes() {
        assertThat(page.containsCode("PIT-PUNE-2026-000004")).isTrue();
        assertThat(page.containsCode("PIT2022CS002")).isTrue();
        assertThat(page.containsCode("PIT2022CS0O2")).isTrue();      // one character off
        assertThat(page.containsCode("PIT2022CS099")).isFalse();     // two characters off
        assertThat(page.containsCode("PIT-PUNE-2026-000007")).isTrue();   // off by one digit: forgiven...
        assertThat(page.containsCode("PIT-PUNE-2026-000077")).isFalse();  // ...but not two
        assertThat(page.containsCode("8.50")).isTrue();
        assertThat(page.containsCode("9.10")).isFalse();
    }

    @Test
    @DisplayName("edit distance of at most one")
    void withinOneEdit() {
        assertThat(PrintedTextMatcher.withinOneEdit("JOSHI", "JOSHI")).isTrue();
        assertThat(PrintedTextMatcher.withinOneEdit("JOSHI", "J0SHI")).isTrue();
        assertThat(PrintedTextMatcher.withinOneEdit("JOSHI", "JOSH")).isTrue();
        assertThat(PrintedTextMatcher.withinOneEdit("JOSHI", "JOSHII")).isTrue();
        assertThat(PrintedTextMatcher.withinOneEdit("JOSHI", "JASHU")).isFalse();
        assertThat(PrintedTextMatcher.withinOneEdit("JOSHI", "JO")).isFalse();
    }
}
