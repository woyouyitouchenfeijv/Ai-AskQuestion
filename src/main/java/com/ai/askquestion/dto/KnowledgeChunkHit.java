package com.ai.askquestion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunkHit {
    private Long chunkId;
    private Long documentId;
    private String content;
    private Double score;
}
