package com.ai.askquestion.controller;

import com.ai.askquestion.service.Assistant;
import com.ai.askquestion.dto.AskQuestionRequest;
import com.ai.askquestion.dto.AskQuestionResponse;
import com.ai.askquestion.service.AiQuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AI Question Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
public class AiQuestionController {

    @Autowired
    AiQuestionService aiQuestionService;

    /**
     * Ask a question endpoint
     * 普通的，直接调用ai
     */
    @PostMapping("/ask")
    public ResponseEntity<AskQuestionResponse> askQuestion(@RequestBody AskQuestionRequest request) {
        log.info("API request received: {}", request.getQuestion());
        AskQuestionResponse response = aiQuestionService.askQuestion(request);
        return ResponseEntity.ok(response);
    }

    /**
     * rag模式，先本地，拿到后再调用ai
     */
    @PostMapping("/rag/ask")
    public ResponseEntity<AskQuestionResponse> ask(@RequestBody AskQuestionRequest questionRequest) {
        log.info("RAG_API request received: {}", questionRequest.getQuestion());
        AskQuestionResponse response = aiQuestionService.askQuestionRag(questionRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("AI Service is running!");
    }

}
