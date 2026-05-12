package com.ai.askquestion.service.impl;

import com.ai.askquestion.dto.KnowledgeChunkHit;
import com.ai.askquestion.service.KnowledgeChunkSearchResult;
import com.ai.askquestion.service.KnowledgeChunkSearchService;
import com.ai.askquestion.service.KnowledgeSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeSearchServiceImpl implements KnowledgeSearchService {
    private final KnowledgeChunkSearchService knowledgeChunkSearchService;

    @Override
    public List<KnowledgeChunkHit> search(Long knowledgeBaseId, String question, int limit) {
        return knowledgeChunkSearchService.search(knowledgeBaseId, question).stream()
                .limit(limit)
                .map(this::toHit)
                .toList();
    }

    private KnowledgeChunkHit toHit(KnowledgeChunkSearchResult result) {
        return new KnowledgeChunkHit(result.getChunkId(), result.getDocumentId(), result.getContent(), result.getScore());
    }
}
