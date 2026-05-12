package com.ai.askquestion.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunkSearchResult {

    private Long chunkId;

    private Long documentId;

    private String content;

    private Double score;
}
