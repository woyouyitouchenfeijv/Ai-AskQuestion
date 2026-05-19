package com.ai.askquestion.mapper;

import com.ai.askquestion.domain.QuestionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface QuestionRecordMapper {
    int insert(QuestionRecord record);
    int updateStatus(@Param("id") Long id, @Param("answerStatus") String answerStatus);
    int updateAnswer(@Param("id") Long id, @Param("answer") String answer, @Param("answerStatus") String answerStatus);
    QuestionRecord findById(@Param("id") Long id);
    QuestionRecord findVerifiedByQuestionHash(@Param("knowledgeBaseId") Long knowledgeBaseId,
                                              @Param("questionHash") String questionHash);
    QuestionRecord findLatestVerifiedByQuestionHash(@Param("knowledgeBaseId") Long knowledgeBaseId,
                                                    @Param("questionHash") String questionHash);
    List<QuestionRecord> findLatestVerifiedCandidates(@Param("knowledgeBaseId") Long knowledgeBaseId,
                                                      @Param("limit") int limit);
    List<QuestionRecord> findByIds(@Param("ids") List<Long> ids);
    int increaseHitCount(@Param("id") Long id);
    int verify(@Param("id") Long id);
    int disable(@Param("id") Long id);
    int markStaleByDocumentId(@Param("documentId") Long documentId);
    int updateEsQuestionId(@Param("id") Long id, @Param("esQuestionId") String esQuestionId);
}
