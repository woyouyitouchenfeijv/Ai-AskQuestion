package com.ai.askquestion.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeDocument {
    private Long id;
    private Long knowledgeBaseId;
    private String fileName;
    private String filePath;
    private String fileType;
    private String fileHash;
    private String parseStatus;
    private String errorMessage;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
