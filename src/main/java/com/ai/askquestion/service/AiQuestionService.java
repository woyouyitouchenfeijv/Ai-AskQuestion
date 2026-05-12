package com.ai.askquestion.service;

import com.ai.askquestion.dto.AskQuestionRequest;
import com.ai.askquestion.dto.AskQuestionResponse;

public interface AiQuestionService {
    AskQuestionResponse askQuestion(AskQuestionRequest request);
    AskQuestionResponse askQuestionRag(AskQuestionRequest request);
    boolean verifyAnswer(Long questionRecordId);
    boolean disableAnswer(Long questionRecordId);
    boolean correctAnswer(Long questionRecordId, String correctedAnswer);
}
