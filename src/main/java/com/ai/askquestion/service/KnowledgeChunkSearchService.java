package com.ai.askquestion.service;

import java.util.List;

public interface KnowledgeChunkSearchService {

    /**
     * 从 knowledge_chunk_index 检索与问题相关的知识切片。
     */
    List<KnowledgeChunkSearchResult> search(Long knowledgeBaseId, String question);
}
