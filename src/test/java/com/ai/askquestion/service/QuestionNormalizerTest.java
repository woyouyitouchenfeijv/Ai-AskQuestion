package com.ai.askquestion.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuestionNormalizerTest {

    private final QuestionNormalizer normalizer = new QuestionNormalizer();

    @Test
    void normalizeShouldTrimLowerCaseConvertFullWidthAndRemoveBlankCharacters() {
        String normalized = normalizer.normalize("  Ｓｐｒｉｎｇ　Boot 是 什么？  ");

        assertEquals("springboot是什么?", normalized);
    }

    @Test
    void hashShouldBeStableForEquivalentQuestions() {
        String hash1 = normalizer.hash("  Ｓｐｒｉｎｇ　Boot 是 什么？  ");
        String hash2 = normalizer.hash("spring boot是什么?");

        assertEquals(hash1, hash2);
    }

    @Test
    void normalizeShouldRejectBlankQuestion() {
        assertThrows(IllegalArgumentException.class, () -> normalizer.normalize("   "));
    }
}
