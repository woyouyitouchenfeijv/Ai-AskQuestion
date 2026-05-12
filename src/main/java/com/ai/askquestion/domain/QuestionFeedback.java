package com.ai.askquestion.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QuestionFeedback {
    private Long id;
    private Long questionRecordId;
    private String feedbackType;
    private String correctedAnswer;
    private LocalDateTime createdAt;
}
