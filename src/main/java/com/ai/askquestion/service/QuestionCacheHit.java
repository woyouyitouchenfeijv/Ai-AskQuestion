package com.ai.askquestion.service;

import com.ai.askquestion.domain.QuestionRecord;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionCacheHit {
    private QuestionRecord record;
    private String hitType;
    private Double similarityScore;
}
