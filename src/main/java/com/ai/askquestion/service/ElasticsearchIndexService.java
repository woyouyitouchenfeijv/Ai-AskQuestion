package com.ai.askquestion.service;

import com.ai.askquestion.domain.KnowledgeChunk;
import com.ai.askquestion.domain.QuestionRecord;
import com.ai.askquestion.dto.EsInitRequest;
import com.ai.askquestion.dto.EsInitResponse;

public interface ElasticsearchIndexService {
    EsInitResponse createKnowledgeChunkIndex(boolean recreate);
    EsInitResponse createQaQuestionIndex(boolean recreate);
    EsInitResponse initializeKnowledgeChunkIndex(EsInitRequest request);
    String indexKnowledgeChunk(KnowledgeChunk chunk);
    String indexQuestion(QuestionRecord record);
}
