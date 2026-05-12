package com.ai.askquestion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AskQuestionResponse {
    private Long recordId;
    private String question;
    private String answer;
    private String sourceType;
    private String hitType;
    private BigDecimal similarityScore;
    private List<Long> sourceChunkIds;
    private List<Long> sourceDocumentIds;
    private Long costTimeMs;
    private String answerStatus;
    private boolean cacheHit;

    public static AskQuestionResponse of(String question, String answer) {
        return new AskQuestionResponse(null, question, answer, null, null, null, List.of(), List.of(), null, null, false);
    }

    public static AskQuestionResponse cacheHit(String question, String answer, Long recordId) {
        return new AskQuestionResponse(recordId, question, answer, "CACHE", "EXACT", null, List.of(), List.of(), 0L, "VERIFIED", true);
    }

    public static AskQuestionResponse generated(String question, String answer, Long recordId, String hitType) {
        return new AskQuestionResponse(recordId, question, answer, "RAG", hitType, null, List.of(), List.of(), null, "DRAFT", false);
    }
}
