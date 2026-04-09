package com.ai.askquestion.service.impl;

import com.ai.askquestion.dto.AskQuestionRequest;
import com.ai.askquestion.dto.AskQuestionResponse;
import com.ai.askquestion.service.AiQuestionService;
import com.ai.askquestion.service.Assistant;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author dx
 * @date 2026/2/26 13:53
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiQuestionServiceImpl implements AiQuestionService {

    @Autowired
    ChatLanguageModel chatLanguageModel;
    @Autowired
    Assistant assistant;

    /**
     * Ask a question to AI
     *
     * @param request the question request
     * @return the AI response
     */
    @Override
    public AskQuestionResponse askQuestion(AskQuestionRequest request) {
        log.info("Received question: {}", request.getQuestion());

        String answer = chatLanguageModel.generate(request.getQuestion());

        log.info("Generated answer: {}", answer);

        return AskQuestionResponse.of(request.getQuestion(), answer);
    }

    @Override
    public AskQuestionResponse askQuestionRag(AskQuestionRequest questionRequest) {
        long startTime = System.currentTimeMillis();
        String chat = assistant.chat(questionRequest.getQuestion());
        long endTime = System.currentTimeMillis();
        log.info("耗时：{}",(endTime-startTime));
        AskQuestionResponse response = new AskQuestionResponse();
        response.setQuestion(questionRequest.getQuestion());
        response.setAnswer(chat);
        return response;
    }

}
