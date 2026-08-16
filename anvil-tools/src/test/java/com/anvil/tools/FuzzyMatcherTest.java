package com.anvil.tools;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FuzzyMatcherTest {

    @Test
    void normalizedMatchIgnoresExtraSpaces() {
        String haystack = "void foo() {\n  bar();\n}";
        String needle = "void foo() {\n bar();\n}";
        FuzzyMatcher.Match match = FuzzyMatcher.normalized(haystack, needle);
        assertTrue(match != null);
    }

    @Test
    void nearMatchesReturnsCandidates() {
        List<FuzzyMatcher.Match> matches = FuzzyMatcher.nearMatches("connectToServer()", "connectServer", 3);
        assertFalse(matches.isEmpty());
    }
}
