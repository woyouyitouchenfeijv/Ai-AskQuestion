package com.ai.askquestion.mapper;

import com.ai.askquestion.domain.QuestionRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionRecordMapper {

    int insert(QuestionRecord record);
}
