package com.ai.askquestion.controller;

import com.ai.askquestion.dto.EsInitRequest;
import com.ai.askquestion.dto.EsInitResponse;
import com.ai.askquestion.service.ElasticsearchIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/es")
public class ElasticsearchIndexController {
    private final ElasticsearchIndexService elasticsearchIndexService;

    @PostMapping("/knowledge-chunk-index")
    public ResponseEntity<EsInitResponse> createKnowledgeChunkIndex(
            @RequestParam(value = "recreate", defaultValue = "false") boolean recreate) {
        log.info("Create knowledge chunk index request, recreate={}", recreate);
        return ResponseEntity.ok(elasticsearchIndexService.createKnowledgeChunkIndex(recreate));
    }

    @PostMapping("/qa-question-index")
    public ResponseEntity<EsInitResponse> createQaQuestionIndex(
            @RequestParam(value = "recreate", defaultValue = "false") boolean recreate) {
        log.info("Create QA question index request, recreate={}", recreate);
        return ResponseEntity.ok(elasticsearchIndexService.createQaQuestionIndex(recreate));
    }

    @PostMapping("/knowledge-chunk-index/init")
    public ResponseEntity<EsInitResponse> initializeKnowledgeChunkIndex(@RequestBody(required = false) EsInitRequest request) {
        log.info("Initialize knowledge chunk index request: {}", request);
        return ResponseEntity.ok(elasticsearchIndexService.initializeKnowledgeChunkIndex(request));
    }
}
