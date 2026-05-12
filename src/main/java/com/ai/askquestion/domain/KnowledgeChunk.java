package com.ai.askquestion.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库切片。
 */
@Data
public class KnowledgeChunk {

    private Long id;

    private Long knowledgeBaseId;

    private Long documentId;

    private Integer chunkIndex;

    private String content;

    private String contentHash;

    private String esDocId;

    private String fileName;

    private String filePath;

    private Integer tokenCount;

    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
