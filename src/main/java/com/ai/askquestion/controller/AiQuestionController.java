package com.ai.askquestion.controller;

import com.ai.askquestion.dto.AskQuestionRequest;
import com.ai.askquestion.dto.AskQuestionResponse;
import com.ai.askquestion.dto.CorrectAnswerRequest;
import com.ai.askquestion.service.AiQuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai")
public class AiQuestionController {
    private final AiQuestionService aiQuestionService;

    @PostMapping("/ask")
    public ResponseEntity<AskQuestionResponse> askQuestion(@RequestBody AskQuestionRequest request) {
        log.info("API request received: {}", request.getQuestion());
        return ResponseEntity.ok(aiQuestionService.askQuestion(request));
    }

    /** MVP 主链路：精确缓存 -> ES 知识库检索 -> 大模型生成 -> DRAFT 入库。 */
    @PostMapping("/rag/ask")
    public ResponseEntity<AskQuestionResponse> ask(@RequestBody AskQuestionRequest questionRequest) {
        log.info("RAG_API request received: {}", questionRequest.getQuestion());
        return ResponseEntity.ok(aiQuestionService.askQuestionRag(questionRequest));
    }

    @PostMapping("/question/{recordId}/verify")
    public ResponseEntity<Void> verify(@PathVariable Long recordId) {
        return aiQuestionService.verifyAnswer(recordId) ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @PostMapping("/question/{recordId}/disable")
    public ResponseEntity<Void> disable(@PathVariable Long recordId) {
        return aiQuestionService.disableAnswer(recordId) ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @PostMapping("/question/{recordId}/correct")
    public ResponseEntity<Void> correct(@PathVariable Long recordId, @RequestBody CorrectAnswerRequest request) {
        return aiQuestionService.correctAnswer(recordId, request.getCorrectedAnswer())
                ? ResponseEntity.ok().build()
                : ResponseEntity.notFound().build();
    }

    /** 兼容旧接口路径。 */
    @PostMapping("/question-records/{id}/verify")
    public ResponseEntity<Void> verifyOld(@PathVariable("id") Long id) {
        return verify(id);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("AI Service is running!");
    }
}
