package com.ai.askquestion.service.impl;

import com.ai.askquestion.dto.NormalizedQuestion;
import com.ai.askquestion.service.QuestionNormalizeService;
import com.ai.askquestion.service.QuestionNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QuestionNormalizeServiceImpl implements QuestionNormalizeService {
    private final QuestionNormalizer questionNormalizer;

    @Override
    public NormalizedQuestion normalize(String question) {
        String normalized = questionNormalizer.normalize(question);
        return new NormalizedQuestion(question, normalized, questionNormalizer.hash(question));
    }
}
