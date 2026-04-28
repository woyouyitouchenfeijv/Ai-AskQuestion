package com.ai.askquestion.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问答记录
 */
@Data
public class QuestionRecord {

    private Long id;

    private String question;

    private String answer;

    /**
     * 取值示例：NORMAL / RAG
     */
    private String sourceType;

    private LocalDateTime createdAt;
}
