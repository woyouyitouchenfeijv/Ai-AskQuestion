package com.ai.askquestion.mapper;

import com.ai.askquestion.domain.KnowledgeChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KnowledgeChunkMapper {
    int insert(KnowledgeChunk chunk);
    int deleteByDocumentId(@Param("documentId") Long documentId);
    List<KnowledgeChunk> findByDocumentId(@Param("documentId") Long documentId);
    List<KnowledgeChunk> findByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);
    List<KnowledgeChunk> selectForEsInit(@Param("knowledgeBaseId") Long knowledgeBaseId,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);
    int updateEsDocId(@Param("id") Long id, @Param("esDocId") String esDocId);
}
