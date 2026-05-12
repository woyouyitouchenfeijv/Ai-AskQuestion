package com.ai.askquestion.mapper;

import com.ai.askquestion.domain.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KnowledgeDocumentMapper {
    int insert(KnowledgeDocument document);
    int updateParseSuccess(KnowledgeDocument document);
    int updateParseFailed(@Param("id") Long id, @Param("errorMessage") String errorMessage);
    KnowledgeDocument findByKnowledgeBaseIdAndFilePath(@Param("knowledgeBaseId") Long knowledgeBaseId,
                                                       @Param("filePath") String filePath);
    KnowledgeDocument findById(@Param("id") Long id);
}
