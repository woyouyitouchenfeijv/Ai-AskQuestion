package com.ai.askquestion.mapper;

import com.ai.askquestion.domain.QuestionFeedback;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionFeedbackMapper {
    int insert(QuestionFeedback feedback);
}
