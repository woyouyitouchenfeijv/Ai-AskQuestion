package com.ai.askquestion.service;

import com.ai.askquestion.dto.NormalizedQuestion;

public interface QuestionNormalizeService {
    NormalizedQuestion normalize(String question);
}
