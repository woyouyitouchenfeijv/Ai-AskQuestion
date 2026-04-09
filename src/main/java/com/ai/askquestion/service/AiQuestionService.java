package com.ai.askquestion.service;

import com.ai.askquestion.dto.AskQuestionRequest;
import com.ai.askquestion.dto.AskQuestionResponse;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI Question Service
 */

public interface AiQuestionService {

    AskQuestionResponse askQuestion(AskQuestionRequest request);
    AskQuestionResponse askQuestionRag(AskQuestionRequest request);
}
