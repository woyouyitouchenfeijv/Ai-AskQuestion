package com.ai.askquestion.service.impl;

import com.ai.askquestion.domain.QuestionRecord;
import com.ai.askquestion.dto.AskQuestionRequest;
import com.ai.askquestion.dto.AskQuestionResponse;
import com.ai.askquestion.mapper.QuestionRecordMapper;
import com.ai.askquestion.service.AiQuestionService;
import com.ai.askquestion.service.Assistant;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author dx
 * @date 2026/2/26 13:53
 */
@Slf4j
@Service
public class AiQuestionServiceImpl implements AiQuestionService {

    @Autowired
    private ChatLanguageModel chatLanguageModel;

    @Autowired
    private Assistant assistant;

    @Autowired
    private QuestionRecordMapper questionRecordMapper;

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
        saveRecord(questionRequest.getQuestion(), chat, "RAG");
        long endTime = System.currentTimeMillis();
        log.info("RAG request cost: {} ms", (endTime - startTime));
        return AskQuestionResponse.of(questionRequest.getQuestion(), chat);
    }

    private void saveRecord(String question, String answer, String sourceType) {
        try {
            QuestionRecord record = new QuestionRecord();
            record.setQuestion(question);
            record.setAnswer(answer);
            record.setSourceType(sourceType);
            questionRecordMapper.insert(record);
        } catch (Exception e) {
            log.error("Save question record failed, sourceType={}", sourceType, e);
        }
    }
}
