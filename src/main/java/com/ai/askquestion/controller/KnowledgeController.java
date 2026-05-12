package com.ai.askquestion.controller;

import com.ai.askquestion.dto.IngestResponse;
import com.ai.askquestion.service.KnowledgeIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/knowledge")
public class KnowledgeController {
    private final KnowledgeIngestService knowledgeIngestService;

    @PostMapping("/{knowledgeBaseId}/ingest")
    public ResponseEntity<IngestResponse> ingest(@PathVariable Long knowledgeBaseId) {
        log.info("Knowledge ingest request, knowledgeBaseId={}", knowledgeBaseId);
        return ResponseEntity.ok(knowledgeIngestService.ingest(knowledgeBaseId));
    }
}
