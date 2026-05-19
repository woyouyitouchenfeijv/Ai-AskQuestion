package com.ai.askquestion.service;

import com.ai.askquestion.domain.QuestionRecord;
import com.ai.askquestion.dto.NormalizedQuestion;

import java.util.Optional;

public interface QuestionCacheService {
    Optional<QuestionCacheHit> findUsableAnswer(Long knowledgeBaseId, String question, NormalizedQuestion normalizedQuestion);
    void indexQuestion(QuestionRecord record);
}
