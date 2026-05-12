package com.ai.askquestion.service;

import com.ai.askquestion.dto.IngestResponse;

public interface KnowledgeIngestService {
    IngestResponse ingest(Long knowledgeBaseId);
}
