package com.ai.askquestion.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * 问题标准化 + 稳定 Hash 生成。
 */
@Component
public class QuestionNormalizer {

    public String normalize(String question) {
        if (question == null) {
            throw new IllegalArgumentException("问题不能为空");
        }

        StringBuilder builder = new StringBuilder(question.length());
        for (char c : question.trim().toCharArray()) {
            if (Character.isWhitespace(c)) {
                continue;
            }
            builder.append(toHalfWidth(c));
        }

        String normalized = builder.toString().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        return normalized;
    }

    public String hash(String question) {
        return sha256Hex(normalize(question));
    }

    private char toHalfWidth(char c) {
        if (c == 12288) {
            return ' ';
        }
        if (c >= 65281 && c <= 65374) {
            return (char) (c - 65248);
        }
        return c;
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", e);
        }
    }
}
