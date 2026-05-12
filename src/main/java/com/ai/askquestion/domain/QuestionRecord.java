package com.ai.askquestion.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问答记录 / 问答资产。
 */
@Data
public class QuestionRecord {

    private Long id;

    private Long knowledgeBaseId;

    private String question;

    private String normalizedQuestion;

    private String questionHash;

    private String answer;

    /**
     * DRAFT / VERIFIED / STALE / DISABLED
     */
    private String answerStatus;

    /**
     * CACHE / RAG / LLM / MANUAL
     */
    private String sourceType;

    /**
     * EXACT / SIMILAR / RAG / NONE
     */
    private String hitType;

    private Double similarityScore;

    /**
     * 引用的 chunk id，逗号分隔。
     */
    private String sourceChunkIds;

    /**
     * 引用的 document id，逗号分隔。
     */
    private String sourceDocumentIds;

    private String esQuestionId;

    private String modelName;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private Long costTimeMs;

    private Integer hitCount;

    private Integer usefulCount;

    private Integer uselessCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
