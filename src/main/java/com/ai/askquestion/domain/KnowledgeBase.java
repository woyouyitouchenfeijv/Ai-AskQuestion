package com.ai.askquestion.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeBase {
    private Long id;
    private String name;
    private String description;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
