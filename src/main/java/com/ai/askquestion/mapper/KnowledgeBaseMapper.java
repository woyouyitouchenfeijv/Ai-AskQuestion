package com.ai.askquestion.mapper;

import com.ai.askquestion.domain.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KnowledgeBaseMapper {
    int insert(KnowledgeBase knowledgeBase);
    KnowledgeBase findById(@Param("id") Long id);
}
