package com.ai.askquestion.service;

import com.ai.askquestion.dto.KnowledgeChunkHit;

import java.util.List;

public interface KnowledgeSearchService {
    List<KnowledgeChunkHit> search(Long knowledgeBaseId, String question, int limit);
}
